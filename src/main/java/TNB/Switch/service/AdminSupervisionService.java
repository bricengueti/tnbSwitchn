package TNB.Switch.service;

import TNB.Switch.DTO.response.PendingCompensationResponse;
import TNB.Switch.DTO.response.PendingReconciliationResponse;
import TNB.Switch.DTO.response.StuckCommandeResponse;
import TNB.Switch.entity.*;
import TNB.Switch.enums.*;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.mapper.PendingCompensationMapper;
import TNB.Switch.mapper.PendingReconciliationMapper;
import TNB.Switch.mapper.StuckCommandeMapper;
import TNB.Switch.messaging.CommandRoutingProducer;
import TNB.Switch.messaging.CompensationProducer;
import TNB.Switch.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static TNB.Switch.specification.CommandeSpecifications.createdBefore;
import static TNB.Switch.specification.CommandeSpecifications.hasNoDevice;
import static TNB.Switch.specification.MessageOperateurBrutSpecifications.hasProcessingStatus;
import static TNB.Switch.specification.TransactionSpecifications.hasStatus;

/**
 * Point d'accès unique aux trois files de reprise manuelle créées au fil
 * du pipeline : messages opérateurs AMBIGUOUS (§9.3bis), transactions en
 * COMPENSATION_MANUAL_REVIEW (§8.4), commandes jamais routées (DLQ
 * routage). Exploite les Specifications déjà posées plutôt que des
 * méthodes dérivées dédiées.
 *
 * =====================================================================
 *                    ADMIN SUPERVISION SERVICE
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  FILE 1 : Messages AMBIGUOUS (Réconciliation)                     │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  findPendingReconciliation()                                      │
 *  │  → resolveManually(messageId, commandeId, success)               │
 *  │  → dismissAsUnrelated(messageId)                                 │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  FILE 2 : Transactions COMPENSATION_MANUAL_REVIEW                 │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  findPendingCompensationReview()                                  │
 *  │  → retryCompensationManually(transactionId)                      │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  FILE 3 : Commandes jamais routées (DLQ)                          │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  findStuckUnroutedCommands(thresholdSeconds)                     │
 *  │  → forceReroute(commandeId)                                      │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 */
@Service
public class AdminSupervisionService {

    private static final Logger log = LoggerFactory.getLogger(AdminSupervisionService.class);

    private final MessageOperateurBrutRepository messageRepository;

    public AdminSupervisionService(MessageOperateurBrutRepository messageRepository, TransactionRepository transactionRepository, CommandeRepository commandeRepository, CompensationAttemptRepository compensationAttemptRepository, TransactionService transactionService, CommandRoutingProducer commandRoutingProducer, CompensationProducer compensationProducer, PendingReconciliationMapper pendingReconciliationMapper, PendingCompensationMapper pendingCompensationMapper, StuckCommandeMapper stuckCommandeMapper) {
        this.messageRepository = messageRepository;
        this.transactionRepository = transactionRepository;
        this.commandeRepository = commandeRepository;
        this.compensationAttemptRepository = compensationAttemptRepository;
        this.transactionService = transactionService;
        this.commandRoutingProducer = commandRoutingProducer;
        this.compensationProducer = compensationProducer;
        this.pendingReconciliationMapper = pendingReconciliationMapper;
        this.pendingCompensationMapper = pendingCompensationMapper;
        this.stuckCommandeMapper = stuckCommandeMapper;
    }

    private final TransactionRepository transactionRepository;
    private final CommandeRepository commandeRepository;
    private final CompensationAttemptRepository compensationAttemptRepository;
    private final TransactionService transactionService;
    private final CommandRoutingProducer commandRoutingProducer;
    private final CompensationProducer compensationProducer;

    // Mappers
    private final PendingReconciliationMapper pendingReconciliationMapper;
    private final PendingCompensationMapper pendingCompensationMapper;
    private final StuckCommandeMapper stuckCommandeMapper;



    // =====================================================================
    //  LECTURE — LES 3 FILES
    // =====================================================================

    /**
     * File 1 : Messages opérateurs en attente de reprise manuelle (AMBIGUOUS).
     */
    public List<PendingReconciliationResponse> findPendingReconciliation() {
        Specification<MessageOperateurBrut> spec =
                hasProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
        return messageRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "receivedAt"))
                .stream()
                .map(pendingReconciliationMapper)
                .toList();
    }

    /**
     * File 2 : Transactions en attente de reprise manuelle (COMPENSATION_MANUAL_REVIEW).
     */
    public List<PendingCompensationResponse> findPendingCompensationReview() {
        Specification<Transaction> spec =
                hasStatus(TransactionStatus.COMPENSATION_MANUAL_REVIEW);
        return transactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(pendingCompensationMapper)
                .toList();
    }

    /**
     * File 3 : Commandes en file depuis plus de thresholdSeconds sans device.
     * Combine hasNoDevice() et createdBefore() via Specifications.
     */
    public List<StuckCommandeResponse> findStuckUnroutedCommands(int thresholdSeconds) {
        Instant threshold = Instant.now().minusSeconds(thresholdSeconds);
        Specification<Commande> spec = Specification
                .where(hasNoDevice())
                .and(createdBefore(threshold));
        return commandeRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .map(stuckCommandeMapper)
                .toList();
    }

    /**
     * Compte le nombre de tentatives de compensation pour une transaction.
     */
    public int countCompensationAttempts(Transaction transaction) {
        return compensationAttemptRepository.countByTransaction(transaction);
    }

    // =====================================================================
    //  ACTIONS — INTERVENTION MANUELLE
    // =====================================================================

    /**
     * Résolution manuelle d'un message AMBIGUOUS/UNMATCHED.
     * L'admin tranche lui-même quelle Commande correspond réellement.
     */
    @Transactional
    public void resolveManually(UUID messageId, UUID commandeId, boolean success) {
        MessageOperateurBrut message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", messageId));

        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", commandeId));

        message.setMatchedCommande(commande);
        message.setMatchingStatus(MatchingStatus.MATCHED);
        message.setProcessingStatus(MessageProcessingStatus.RECONCILED);

        log.warn("Message [{}] résolu MANUELLEMENT par un admin -> commande [{}], succès={}",
                messageId, commandeId, success);

        Transaction transaction = commande.getTransaction();
        if (commande.getPhase() == CommandPhase.WITHDRAWAL) {
            transactionService.handleWithdrawalResult(transaction.getId(), success);
        } else {
            transactionService.handleExecutionResult(transaction.getId(), commande.getId(), success);
        }
    }

    /**
     * Rejette un message comme hors-sujet (UNRELATED).
     */
    @Transactional
    public void dismissAsUnrelated(UUID messageId) {
        MessageOperateurBrut message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", messageId));

        message.setProcessingStatus(MessageProcessingStatus.CLOSED_UNRELATED);
        log.info("Message [{}] rejeté manuellement comme hors-sujet par un admin", messageId);
    }

    /**
     * Relance manuelle d'une compensation bloquée en COMPENSATION_MANUAL_REVIEW.
     * L'admin décide de retenter malgré l'épuisement des 3 tentatives.
     */
    @Transactional
    public void retryCompensationManually(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));

        if (transaction.getStatus() != TransactionStatus.COMPENSATION_MANUAL_REVIEW) {
            throw new IllegalStateException(
                    "Seule une transaction en COMPENSATION_MANUAL_REVIEW peut être relancée manuellement"
            );
        }

        List<Commande> executionCommandes = commandeRepository.findByTransaction(transaction).stream()
                .filter(c -> c.getPhase() == CommandPhase.EXECUTION)
                .toList();

        if (executionCommandes.isEmpty()) {
            throw new IllegalStateException("Aucune commande EXECUTION trouvée pour cette transaction");
        }

        Commande lastFailedCommande = executionCommandes.get(executionCommandes.size() - 1);

        compensationProducer.publishManualRetry(transaction, lastFailedCommande);

        log.warn("Transaction [{}] : relance manuelle de compensation par un admin (tentative {})",
                transactionId, countCompensationAttempts(transaction) + 1);
    }

    /**
     * Force le reroutage d'une commande jamais routée (DLQ).
     */
    @Transactional
    public void forceReroute(UUID commandeId) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", commandeId));

        if (commande.getDevice() != null) {
            throw new IllegalStateException(
                    "La commande [%s] est déjà routée vers le device [%s]"
                            .formatted(commandeId, commande.getDevice().getId())
            );
        }

        commandRoutingProducer.publishForRouting(commande);
        log.info("Commande [{}] republiée manuellement pour routage par un admin", commandeId);
    }
}