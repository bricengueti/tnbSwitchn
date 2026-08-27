package TNB.Switch.service;


import TNB.Switch.entity.*;
import TNB.Switch.enums.*;
import TNB.Switch.exeption.ResourceNotFoundException;
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
 * méthodes dérivées dédiées — cohérent avec le reste du projet et prêt
 * à accueillir d'autres filtres (device, opérateur, période...) sans
 * multiplier les méthodes de repository.
 */
@Service
public class AdminSupervisionService {

    private static final Logger log = LoggerFactory.getLogger(AdminSupervisionService.class);

    private final MessageOperateurBrutRepository messageRepository;
    private final TransactionRepository transactionRepository;
    private final CommandeRepository commandeRepository;
    private final CompensationAttemptRepository compensationAttemptRepository;
    private final TransactionService transactionService;
    private final CommandRoutingProducer commandRoutingProducer;
    private final CompensationProducer compensationProducer;

    public AdminSupervisionService(
            MessageOperateurBrutRepository messageRepository,
            TransactionRepository transactionRepository,
            CommandeRepository commandeRepository,
            CompensationAttemptRepository compensationAttemptRepository,
            TransactionService transactionService,
            CommandRoutingProducer commandRoutingProducer, CompensationProducer compensationProducer) {
        this.messageRepository = messageRepository;
        this.transactionRepository = transactionRepository;
        this.commandeRepository = commandeRepository;
        this.compensationAttemptRepository = compensationAttemptRepository;
        this.transactionService = transactionService;
        this.commandRoutingProducer = commandRoutingProducer;
        this.compensationProducer = compensationProducer;
    }

    // ===== LECTURE — les 3 files, via Specifications =====

    public List<MessageOperateurBrut> findPendingReconciliation() {
        Specification<MessageOperateurBrut> spec =
                hasProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
        return messageRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "receivedAt"));
    }

    public List<Transaction> findPendingCompensationReview() {
        Specification<Transaction> spec =
                hasStatus(TransactionStatus.COMPENSATION_MANUAL_REVIEW);
        return transactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Commandes en file depuis plus de thresholdSeconds sans device —
     * combine hasNoDevice() et createdBefore() : exactement le genre de
     * composition que les Specifications permettent d'éviter de dupliquer
     * en @Query figée dans le repository.
     */
    public List<Commande> findStuckUnroutedCommands(int thresholdSeconds) {
        Instant threshold = Instant.now().minusSeconds(thresholdSeconds);
        Specification<Commande> spec = Specification
                .where(hasNoDevice())
                .and(createdBefore(threshold));
        return commandeRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    public int countCompensationAttempts(Transaction transaction) {
        return compensationAttemptRepository.countByTransaction(transaction);
    }

    // ===== ACTIONS — intervention manuelle explicite =====

    /**
     * Résolution manuelle d'un message AMBIGUOUS/UNMATCHED : l'admin
     * tranche lui-même quelle Commande correspond réellement, après
     * examen du contenu brut. Contourne le matching automatique, mais
     * reste tracé (created_by = l'admin, via TnbAuditorAware).
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
    // Ajout à AdminSupervisionService

    /**
     * Relance manuelle d'une compensation bloquée en COMPENSATION_MANUAL_REVIEW —
     * l'admin décide de retenter malgré l'épuisement des 3 tentatives
     * automatiques (ex. il a constaté que la flotte a été réapprovisionnée
     * depuis). Republie directement sur le topic de compensation, sans reset
     * du compteur de CompensationAttempt : la nouvelle tentative s'ajoute à
     * l'historique existant plutôt que de repartir de zéro, pour garder une
     * traçabilité complète du nombre réel de tentatives (automatiques + manuelles).
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

        Commande lastFailedCommande = executionCommandes.get(executionCommandes.size() - 1);

        compensationProducer.publishManualRetry(transaction, lastFailedCommande);

        log.warn("Transaction [{}] : relance manuelle de compensation par un admin (tentative {})",
                transactionId, countCompensationAttempts(transaction) + 1);
    }

    @Transactional
    public void dismissAsUnrelated(UUID messageId) {
        MessageOperateurBrut message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", messageId));

        message.setProcessingStatus(MessageProcessingStatus.CLOSED_UNRELATED);
        log.info("Message [{}] rejeté manuellement comme hors-sujet par un admin", messageId);
    }

    @Transactional
    public void forceReroute(UUID commandeId) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", commandeId));

        commandRoutingProducer.publishForRouting(commande);
        log.info("Commande [{}] republiée manuellement pour routage par un admin", commandeId);
    }
}
