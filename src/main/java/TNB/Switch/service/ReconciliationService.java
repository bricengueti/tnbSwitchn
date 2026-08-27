package TNB.Switch.service;

import TNB.Switch.entity.*;
import TNB.Switch.enums.*;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.messaging.ReconciliationProducer;
import TNB.Switch.repository.MessageOperateurBrutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final MessageOperateurBrutRepository messageRepository;
    private final ReconciliationProducer reconciliationProducer;
    private final FleetBalanceService fleetBalanceService;
    private final TransactionService transactionService;
    private final double confidenceThreshold;
    private final int reconciliationWindowSeconds;
    private final NotificationService notificationService;


    public ReconciliationService(
            MessageOperateurBrutRepository messageRepository,
            ReconciliationProducer reconciliationProducer,
            FleetBalanceService fleetBalanceService,
            TransactionService transactionService,
            @Value("${tnb.ia.reconciliation.confidence-threshold}") double confidenceThreshold,
            @Value("${tnb.routing.sms-reconciliation-timeout-seconds}") int reconciliationWindowSeconds, NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.reconciliationProducer = reconciliationProducer;
        this.fleetBalanceService = fleetBalanceService;
        this.transactionService = transactionService;
        this.confidenceThreshold = confidenceThreshold;
        this.reconciliationWindowSeconds = reconciliationWindowSeconds;
        this.notificationService = notificationService;
    }

    /**
     * ÉTAPE 1 (CDC §9.3bis) — persistance immédiate, AVANT toute
     * publication Kafka. Appelé par le handler STOMP (pas encore écrit).
     */
    @Transactional
    public MessageOperateurBrut receiveRawMessage(Device device, Operateur operateur, String rawContent) {
        MessageOperateurBrut message = new MessageOperateurBrut(device, operateur, rawContent, Instant.now());
        MessageOperateurBrut saved = messageRepository.save(message);

        log.info("Message opérateur brut reçu [{}] du device [{}]", saved.getId(), device.getId());

        reconciliationProducer.publish(saved);

        return saved;
    }

    /**
     * ÉTAPES 3 et 4 (CDC §9.3bis) — appelé par ReconciliationConsumer
     * APRÈS un appel IA réussi (le retry sur échec IA est géré par Kafka
     * en amont, cette méthode ne voit que des résultats IA valides).
     */
    @Transactional
    public void handleIaResult(UUID messageId, IaExtractionResult result) {
        MessageOperateurBrut message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", messageId));

        message.setIaClassification(result.classification());
        message.setIaConfidence(result.confidence());
        message.setIaExtractedAmount(result.extractedAmount());
        message.setIaExtractedPhoneNumber(result.extractedPhoneNumber());
        message.setIaExtractedReference(result.extractedReference());
        message.setIaModelVersion(result.modelVersion());
        message.setProcessingStatus(MessageProcessingStatus.CLASSIFIED);

        applyDeterministicValidation(message, result);
    }

    private void applyDeterministicValidation(MessageOperateurBrut message, IaExtractionResult result) {
        if (result.classification() == IaClassification.UNRELATED) {
            message.setProcessingStatus(MessageProcessingStatus.CLOSED_UNRELATED);
            log.debug("Message [{}] classifié UNRELATED, clôturé sans impact", message.getId());
            return;
        }

        if (result.confidence() < confidenceThreshold) {
            escalateToManualReview(message, "confiance IA insuffisante (%.2f < %.2f)"
                    .formatted(result.confidence(), confidenceThreshold), MatchingStatus.AMBIGUOUS);
            return;
        }

        Instant windowStart = message.getReceivedAt().minusSeconds(reconciliationWindowSeconds);
        List<Commande> candidates = messageRepository.findMatchingCandidatesAnyPhase(
                message.getDevice(), message.getOperateur(), result.extractedAmount(), windowStart
        );

        if (candidates.isEmpty()) {
            escalateToManualReview(message, "aucune commande candidate trouvée", MatchingStatus.UNMATCHED);
            return;
        }

        if (candidates.size() > 1) {
            escalateToManualReview(message, "%d commandes candidates, ambiguïté"
                    .formatted(candidates.size()), MatchingStatus.AMBIGUOUS);
            return;
        }

        reconcile(message, candidates.get(0), result.classification() == IaClassification.SUCCESS);
    }

    private void escalateToManualReview(MessageOperateurBrut message, String reason, MatchingStatus matchingStatus) {
        message.setMatchingStatus(matchingStatus);
        message.setProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
        log.warn("Message [{}] escaladé en reprise manuelle ({}) : {}",
                message.getId(), matchingStatus, reason);

        notificationService.alertAdmin(
                "Message opérateur %s escaladé (%s) : %s".formatted(message.getId(), matchingStatus, reason)
        );
    }

    private void reconcile(MessageOperateurBrut message, Commande commande, boolean success) {
        message.setMatchedCommande(commande);
        message.setMatchingStatus(MatchingStatus.MATCHED);
        message.setProcessingStatus(MessageProcessingStatus.RECONCILED);

        Transaction transaction = commande.getTransaction();

        if (success) {
            applyFleetMovement(commande, transaction);
        }

        log.info("Message [{}] réconcilié avec la commande [{}], succès={}",
                message.getId(), commande.getId(), success);

        if (commande.getPhase() == CommandPhase.WITHDRAWAL) {
            transactionService.handleWithdrawalResult(transaction.getId(), success);
        } else {
            transactionService.handleExecutionResult(transaction.getId(), commande.getId(), success);
        }
    }

    /**
     * Sens du mouvement dépendant de la phase (§7ter.3) :
     * - WITHDRAWAL réussi : le client vient de transférer les fonds vers
     *   le wallet de l'agent (device/opérateur) -> CRÉDIT.
     * - EXECUTION réussie : l'agent consomme son solde pour honorer la
     *   prestation (recharge, ou dépôt côté échange MO) -> DÉBIT.
     */
    private void applyFleetMovement(Commande commande, Transaction transaction) {
        FleetBalance balance = fleetBalanceService.getBalance(
                commande.getDevice(), commande.getOperateur()
        );

        BigDecimal amount = transaction.getAmount();
        boolean isExchange = transaction.getOffer().getType() == OfferType.EXCHANGE_MO;

        if (commande.getPhase() == CommandPhase.WITHDRAWAL) {
            fleetBalanceService.creditWallet(balance.getId(), amount,
                    FleetMovementReason.TRANSACTION_CREDIT, null, transaction.getId());
        } else if (isExchange) {
            fleetBalanceService.debitWallet(balance.getId(), amount,
                    FleetMovementReason.TRANSACTION_DEBIT, null, transaction.getId());
        } else {
            fleetBalanceService.debitCredit(balance.getId(), amount,
                    FleetMovementReason.TRANSACTION_DEBIT, null, transaction.getId());
        }
    }
}