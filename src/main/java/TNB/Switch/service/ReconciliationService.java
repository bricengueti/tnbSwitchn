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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service de réconciliation des messages opérateurs.
 *
 * ⚠️ IMPORTANT : La réconciliation automatique ne concerne que les
 * WITHDRAWAL (retraits) car c'est l'étape critique d'encaissement.
 * Pour les EXECUTION, on se base sur la confiance du système et on
 * gère les litiges manuellement.
 *
 * =====================================================================
 *                    RECONCILIATION SERVICE — FENÊTRE DYNAMIQUE
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  RECHERCHE DES CANDIDATS AVEC FENÊTRE DYNAMIQUE                   │
 *  │                                                                     │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  Tentative 1 : Fenêtre standard (5 min)                     │   │
 *  │  │  ────────────────────────────────────────────────────────── │   │
 *  │  │  windowStart = receivedAt - 300s                           │   │
 *  │  │  → Si 1 candidat → réconciliation                          │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │              ┌───────────────┴───────────────┐                     │
 *  │              │                               │                     │
 *  │              ▼                               ▼                     │
 *  │  ┌─────────────────────────────┐ ┌─────────────────────────────┐  │
 *  │  │ 0 candidat ou >1 candidat   │ │ 1 candidat                  │  │
 *  │  └─────────────┬───────────────┘ └─────────────┬───────────────┘  │
 *  │                │                               │                  │
 *  │                ▼                               ▼                  │
 *  │  ┌─────────────────────────────────────────────────────────────┐  │
 *  │  │  Tentative 2 : Fenêtre élargie (15 min)                     │  │
 *  │  │  ────────────────────────────────────────────────────────── │  │
 *  │  │  windowStart = receivedAt - 900s                           │  │
 *  │  │  → Si 1 candidat → réconciliation AVEC AUDIT               │  │
 *  │  └─────────────────────────────────────────────────────────────┘  │
 *  │                              │                                      │
 *  │              ┌───────────────┴───────────────┐                     │
 *  │              │                               │                     │
 *  │              ▼                               ▼                     │
 *  │  ┌─────────────────────────────┐ ┌─────────────────────────────┐  │
 *  │  │ 0 candidat ou >1 candidat   │ │ 1 candidat                  │  │
 *  │  └─────────────┬───────────────┘ └─────────────┬───────────────┘  │
 *  │                │                               │                  │
 *  │                ▼                               ▼                  │
 *  │  ┌─────────────────────────────┐ ┌─────────────────────────────┐  │
 *  │  │  ESCALADE ADMIN             │ │  RÉCONCILIATION AVEC AUDIT  │  │
 *  │  │  (UNMATCHED / AMBIGUOUS)   │ │  + notification admin        │  │
 *  │  └─────────────────────────────┘ └─────────────────────────────┘  │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final MessageOperateurBrutRepository messageRepository;
    private final ReconciliationProducer reconciliationProducer;
    private final FleetBalanceService fleetBalanceService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    private final double confidenceThreshold;
    private final int windowStandardSeconds;
    private final int windowExtendedSeconds;
    private final int auditThresholdSeconds;

    public ReconciliationService(
            MessageOperateurBrutRepository messageRepository,
            ReconciliationProducer reconciliationProducer,
            FleetBalanceService fleetBalanceService,
            TransactionService transactionService,
            NotificationService notificationService,
            @Value("${tnb.ia.reconciliation.confidence-threshold:0.85}") double confidenceThreshold,
            @Value("${tnb.routing.sms-reconciliation-timeout-seconds:300}") int windowStandardSeconds,
            @Value("${tnb.routing.sms-reconciliation-extended-seconds:900}") int windowExtendedSeconds,
            @Value("${tnb.routing.sms-reconciliation-audit-threshold-seconds:600}") int auditThresholdSeconds) {
        this.messageRepository = messageRepository;
        this.reconciliationProducer = reconciliationProducer;
        this.fleetBalanceService = fleetBalanceService;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.confidenceThreshold = confidenceThreshold;
        this.windowStandardSeconds = windowStandardSeconds;
        this.windowExtendedSeconds = windowExtendedSeconds;
        this.auditThresholdSeconds = auditThresholdSeconds;
    }

    // =====================================================================
    //  ÉTAPE 1 : PERSISTANCE IMMÉDIATE
    // =====================================================================

    @Transactional("transactionManager")
    public MessageOperateurBrut receiveRawMessage(Device device, Operateur operateur, String rawContent) {
        MessageOperateurBrut message = new MessageOperateurBrut(device, operateur, rawContent, Instant.now());
        MessageOperateurBrut saved = messageRepository.save(message);

        log.info("Message opérateur brut reçu [{}] du device [{}]", saved.getId(), device.getId());

        reconciliationProducer.publish(saved);

        return saved;
    }

    // =====================================================================
    //  ÉTAPE 2 : TRAITEMENT IA + VALIDATION DÉTERMINISTE
    // =====================================================================

    @Transactional("transactionManager")
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

    // =====================================================================
    //  VALIDATION DÉTERMINISTE AVEC FENÊTRE DYNAMIQUE
    // =====================================================================

    /**
     * ⚠️ IMPORTANT : On ne réconcilie que les WITHDRAWAL (retraits)
     * car c'est l'étape critique d'encaissement.
     *
     * Stratégie de recherche avec fenêtre dynamique :
     * 1. Fenêtre standard (5 min) → recherche rapide
     * 2. Si 0 ou >1 candidat → fenêtre élargie (15 min)
     * 3. Si 1 candidat en fenêtre élargie → réconciliation AVEC audit
     * 4. Sinon → escalade admin
     */
    private void applyDeterministicValidation(MessageOperateurBrut message, IaExtractionResult result) {
        // Cas 1 : UNRELATED → message ignoré
        if (result.classification() == IaClassification.UNRELATED) {
            message.setProcessingStatus(MessageProcessingStatus.CLOSED_UNRELATED);
            log.debug("Message [{}] classifié UNRELATED, clôturé sans impact", message.getId());
            return;
        }

        // Cas 2 : Confiance insuffisante → escalade admin
        if (result.confidence() < confidenceThreshold) {
            escalateToManualReview(message, "confiance IA insuffisante (%.2f < %.2f)"
                    .formatted(result.confidence(), confidenceThreshold), MatchingStatus.AMBIGUOUS);
            return;
        }

        // ================================================================
        //  TENTATIVE 1 : FENÊTRE STANDARD (5 min)
        // ================================================================
        Instant windowStartStandard = message.getReceivedAt().minusSeconds(windowStandardSeconds);
        List<Commande> candidates = findCandidates(message, result, windowStartStandard);

        // Cas 3 : 1 candidat → réconciliation directe
        if (candidates.size() == 1) {
            log.info("Candidat trouvé dans la fenêtre standard ({}s)", windowStandardSeconds);
            reconcile(message, candidates.get(0), result.classification() == IaClassification.SUCCESS);
            return;
        }

        // Log pour traçabilité
        log.info("Fenêtre standard: {} candidats, passage à la fenêtre élargie", candidates.size());

        // ================================================================
        //  TENTATIVE 2 : FENÊTRE ÉLARGIE (15 min)
        // ================================================================
        Instant windowStartExtended = message.getReceivedAt().minusSeconds(windowExtendedSeconds);
        candidates = findCandidates(message, result, windowStartExtended);

        // Cas 4 : 1 candidat en fenêtre élargie → réconciliation AVEC AUDIT
        if (candidates.size() == 1) {
            long delayMinutes = ChronoUnit.MINUTES.between(
                    candidates.get(0).getCreatedAt(),
                    message.getReceivedAt()
            );

            log.warn("Candidat trouvé dans la fenêtre élargie ({}s) - délai: {} min",
                    windowExtendedSeconds, delayMinutes);

            // Audit : alerter admin si délai exceptionnel
            if (delayMinutes * 60 > auditThresholdSeconds) {
                notificationService.alertAdmin(
                        "⚠️ RÉCONCILIATION AVEC DÉLAI EXCEPTIONNEL: Message %s, Commande %s, Délai %d min"
                                .formatted(message.getId(), candidates.get(0).getId(), delayMinutes)
                );
            }

            reconcile(message, candidates.get(0), result.classification() == IaClassification.SUCCESS);
            return;
        }

        // ================================================================
        //  TENTATIVE 3 : ESCALADE ADMIN
        // ================================================================
        if (candidates.isEmpty()) {
            escalateToManualReview(message,
                    "aucune commande WITHDRAWAL candidate (fenêtre standard + élargie)",
                    MatchingStatus.UNMATCHED);
        } else {
            escalateToManualReview(message,
                    candidates.size() + " commandes WITHDRAWAL candidates (fenêtre élargie)",
                    MatchingStatus.AMBIGUOUS);
        }
    }

    // =====================================================================
    //  RECHERCHE DE CANDIDATS
    // =====================================================================

    /**
     * Recherche les commandes candidates pour une fenêtre donnée.
     */
    private List<Commande> findCandidates(MessageOperateurBrut message,
                                          IaExtractionResult result,
                                          Instant windowStart) {
        return messageRepository.findMatchingCandidates(
                message.getDevice(),
                message.getOperateur(),
                CommandPhase.WITHDRAWAL,
                result.extractedAmount(),
                windowStart
        );
    }

    // =====================================================================
    //  ESCALADE ADMIN
    // =====================================================================

    private void escalateToManualReview(MessageOperateurBrut message, String reason, MatchingStatus matchingStatus) {
        message.setMatchingStatus(matchingStatus);
        message.setProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
        log.warn("Message [{}] escaladé en reprise manuelle ({}) : {}",
                message.getId(), matchingStatus, reason);

        notificationService.alertAdmin(
                "Message opérateur %s escaladé (%s) : %s".formatted(message.getId(), matchingStatus, reason)
        );
    }

    // =====================================================================
    //  RÉCONCILIATION (UNIQUEMENT WITHDRAWAL)
    // =====================================================================

    /**
     * Réconcilie un message avec une commande WITHDRAWAL.
     */
    private void reconcile(MessageOperateurBrut message, Commande commande, boolean success) {
        // Vérification de sécurité : on ne réconcilie que les WITHDRAWAL
        if (commande.getPhase() != CommandPhase.WITHDRAWAL) {
            log.warn("Tentative de réconciliation sur une commande non-WITHDRAWAL [{}] - ignorée", commande.getId());
            message.setProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
            notificationService.alertAdmin(
                    "Message %s : tentative de réconciliation sur commande %s (phase %s) - ignorée"
                            .formatted(message.getId(), commande.getId(), commande.getPhase())
            );
            return;
        }

        // 1. Marquer le message comme réconcilié
        message.setMatchedCommande(commande);
        message.setMatchingStatus(MatchingStatus.MATCHED);
        message.setProcessingStatus(MessageProcessingStatus.RECONCILED);

        Transaction transaction = commande.getTransaction();

        // 2. Appliquer le mouvement de flotte si succès
        if (success) {
            applyFleetMovementForWithdrawal(commande, transaction);
        } else {
            log.warn("Retrait WITHDRAWAL [{}] a échoué - aucun mouvement de flotte", commande.getId());
        }

        log.info("Message [{}] réconcilié avec la commande WITHDRAWAL [{}], succès={}",
                message.getId(), commande.getId(), success);

        // 3. Mettre à jour la transaction
        transactionService.handleWithdrawalResult(transaction.getId(), success);
    }

    // =====================================================================
    //  MOUVEMENT DE FLOTTE (UNIQUEMENT WITHDRAWAL)
    // =====================================================================

    /**
     * Applique le mouvement de flotte pour un WITHDRAWAL réussi.
     * WITHDRAWAL réussi → CRÉDIT du wallet.
     */
    private void applyFleetMovementForWithdrawal(Commande commande, Transaction transaction) {
        try {
            FleetBalance balance = fleetBalanceService.getBalance(
                    commande.getDevice(),
                    commande.getOperateur()
            );

            BigDecimal amount = transaction.getAmount();

            fleetBalanceService.creditWallet(
                    balance.getId(),
                    amount,
                    FleetMovementReason.TRANSACTION_CREDIT,
                    "Retrait réussi - réconciliation",
                    transaction.getId()
            );

            log.debug("Crédit wallet [{}] de {} FCFA (WITHDRAWAL réconcilié)", balance.getId(), amount);
        } catch (Exception e) {
            log.error("Erreur lors du mouvement de flotte pour la transaction [{}] : {}",
                    transaction.getId(), e.getMessage(), e);
            // On ne bloque pas la réconciliation en cas d'erreur de flotte
            notificationService.alertAdmin(
                    "⚠️ ERREUR FLOTTE: Transaction %s, Commande %s, Erreur: %s"
                            .formatted(transaction.getId(), commande.getId(), e.getMessage())
            );
        }
    }

    // =====================================================================
    //  MÉTHODE POUR EXECUTION (PAS DE RÉCONCILIATION AUTOMATIQUE)
    // =====================================================================

    /**
     * Traite le succès d'une exécution.
     * ⚠️ Pas de réconciliation automatique pour l'exécution.
     * On se base sur la confiance du système.
     */
    public void handleExecutionSuccess(UUID transactionId) {
        log.info("EXECUTION confirmée pour la transaction [{}] - pas de réconciliation automatique", transactionId);
        transactionService.handleExecutionResult(transactionId, null, true);
    }

    /**
     * Traite l'échec d'une exécution.
     * ⚠️ Déclenche la compensation.
     */
    public void handleExecutionFailure(UUID transactionId, UUID failedCommandeId) {
        log.error("EXECUTION échouée pour la transaction [{}] - déclenchement compensation", transactionId);
        transactionService.handleExecutionResult(transactionId, failedCommandeId, false);
    }

    /**
     * Traite le résultat d'un message d'exécution (succès ou échec).
     * Utilisé par ReconciliationConsumer.
     */
    public void handleExecutionMessage(MessageOperateurBrut message, Commande commande, boolean success) {
        // Vérification de sécurité
        if (commande.getPhase() != CommandPhase.EXECUTION) {
            log.warn("Message d'exécution reçu pour une commande non-EXECUTION [{}]", commande.getId());
            message.setProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
            return;
        }

        // Marquer le message comme réconcilié
        message.setMatchedCommande(commande);
        message.setMatchingStatus(MatchingStatus.MATCHED);
        message.setProcessingStatus(MessageProcessingStatus.RECONCILED);

        log.info("Message d'exécution [{}] réconcilié avec la commande [{}], succès={}",
                message.getId(), commande.getId(), success);

        // Mettre à jour la transaction
        if (success) {
            handleExecutionSuccess(commande.getTransaction().getId());
        } else {
            handleExecutionFailure(commande.getTransaction().getId(), commande.getId());
        }
    }
}