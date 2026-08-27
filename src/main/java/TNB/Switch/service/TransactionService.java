package TNB.Switch.service;

import TNB.Switch.annotation.IdempotencyKey;
import TNB.Switch.annotation.Idempotent;
import TNB.Switch.entity.*;
import TNB.Switch.enums.CommandPhase;
import TNB.Switch.enums.OfferType;
import TNB.Switch.enums.TransactionStatus;
import TNB.Switch.exeption.IllegalStateTransitionException;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.messaging.CommandRoutingProducer;
import TNB.Switch.messaging.CompensationProducer;
import TNB.Switch.repository.CommandeRepository;
import TNB.Switch.repository.TransactionRepository;
import TNB.Switch.resolver.CommandTemplateResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Chef d'orchestre de la state machine Transaction (CDC §9.4).
 *
 * =====================================================================
 *                    TRANSACTION STATE MACHINE
 * =====================================================================
 *
 *                              ┌─────────────────┐
 *                              │    WAIT_OTP     │  ← Transaction créée, OTP envoyé
 *                              └────────┬────────┘
 *                                       │ OTP validé par client
 *                                       ▼
 *                              ┌─────────────────┐
 *                              │QUEUE_WITHDRAWAL │  ← Commande retrait en file Kafka
 *                              └────────┬────────┘
 *                                       │ Device disponible (RoutingService)
 *                                       ▼
 *                              ┌─────────────────┐
 *                              │ ASK_WITHDRAWAL  │  ← Commande envoyée au device
 *                              └────────┬────────┘
 *                                       │ Message opérateur reçu
 *                                       │ (ReconciliationService)
 *              ┌────────────────────────┼────────────────────────┐
 *              │                        │                        │
 *              ▼                        ▼                        ▼
 *   ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
 *   │WITHDRAWAL_FAILED│     │WITHDRAWAL_DONE  │     │   CANCELLED     │
 *   │  (Terminal)     │     └────────┬────────┘     │  (Terminal)     │
 *   └─────────────────┘              │              └─────────────────┘
 *                                    │ Retrait réussi
 *                                    ▼
 *                              ┌─────────────────┐
 *                              │QUEUE_EXECUTE    │  ← Commande exécution en file
 *                              │  _COMMAND       │
 *                              └────────┬────────┘
 *                                       │ Device disponible (RoutingService)
 *                                       ▼
 *                              ┌─────────────────┐
 *                              │ROUTE_EXECUTE    │  ← Commande envoyée au device
 *                              │  _COMMAND       │
 *                              └────────┬────────┘
 *                                       │ Message opérateur reçu
 *                                       │ (ReconciliationService)
 *              ┌────────────────────────┼────────────────────────┐
 *              │                        │                        │
 *              ▼                        ▼                        ▼
 *   ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
 *   │EXECUTE_COMMAND_ │     │EXECUTE_COMMAND_ │     │COMPENSATION_    │
 *   │     DONE        │     │    FAILED       │     │  IN_PROGRESS    │  ← Retrait réussi
 *   │  (Terminal)     │     └────────┬────────┘     └────────┬────────┘  │   mais exécution
 *   └─────────────────┘              │                      │           │   échouée
 *                                    │                      │           │
 *                                    │                      ▼           │
 *                                    │               ┌─────────────────┐│
 *                                    │               │QUEUE_EXECUTE    ││  ← Retry automatique
 *                                    │               │  _COMMAND       ││  (max 3 tentatives)
 *                                    │               └────────┬────────┘│
 *                                    │                        │         │
 *                                    │                        ▼         │
 *                                    │               ┌─────────────────────┐
 *                                    └──────────────►│COMPENSATION_MANUAL │
 *                                                    │      _REVIEW       │  ← Reprise manuelle admin
 *                                                    │  (Terminal)        │
 *                                                    └─────────────────────┘
 *
 * =====================================================================
 * LÉGENDE :
 *   ┌─────────────┐  = État terminal (aucune transition sortante)
 *   ┌─────────────┐  = État intermédiaire (transition possible)
 *   ───────────────  = Transition normale
 *   ─ ─ ─ ─ ─ ─ ─  = Transition de compensation (retry)
 * =====================================================================
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final CommandeRepository commandeRepository;
    private final CommandRoutingProducer commandRoutingProducer;
    private final CompensationProducer compensationProducer;
    private final CommandTemplateResolver templateResolver;
    private final AuthService authService;

    public TransactionService(TransactionRepository transactionRepository,
                              CommandeRepository commandeRepository,
                              CommandRoutingProducer commandRoutingProducer,
                              CompensationProducer compensationProducer,
                              CommandTemplateResolver templateResolver,
                              AuthService authService) {
        this.transactionRepository = transactionRepository;
        this.commandeRepository = commandeRepository;
        this.commandRoutingProducer = commandRoutingProducer;
        this.compensationProducer = compensationProducer;
        this.templateResolver = templateResolver;
        this.authService = authService;
    }

    // ==================== CRÉATION ====================
    // WAIT_OTP ← Transaction créée

    /**
     * Crée la transaction (idempotente via @Idempotent + contrainte DB).
     * Statut initial WAIT_OTP. Déclenche immédiatement l'envoi d'un OTP.
     *
     * @param idempotencyKey Clé d'idempotence (générée par le client)
     * @param client Utilisateur authentifié
     * @param offer Offre sélectionnée
     * @param destinationPhoneNumber Wallet destination (obligatoire EXCHANGE_MO)
     * @param payerPhoneNumber Wallet source (obligatoire EXCHANGE_MO)
     */
    @Idempotent
    @Transactional
    public Transaction createTransaction(
            @IdempotencyKey String idempotencyKey,
            User client,
            Offer offer,
            String destinationPhoneNumber,
            String payerPhoneNumber) {

        // Validation EXCHANGE_MO
        if (offer.getType() == OfferType.EXCHANGE_MO) {
            if (destinationPhoneNumber == null || destinationPhoneNumber.isBlank()) {
                throw new IllegalArgumentException(
                        "Une transaction sur une offre EXCHANGE_MO doit fournir un numéro de destination"
                );
            }
            if (payerPhoneNumber == null || payerPhoneNumber.isBlank()) {
                throw new IllegalArgumentException(
                        "Une transaction sur une offre EXCHANGE_MO doit fournir un numéro source (payer)"
                );
            }
        }

        Transaction saved;
        try {
            Transaction transaction = new Transaction(
                    offer,
                    client,
                    offer.getPrice(),
                    TransactionStatus.WAIT_OTP,
                    idempotencyKey,
                    destinationPhoneNumber,
                    payerPhoneNumber,
                    null
            );
            saved = transactionRepository.save(transaction);
            log.info("Transaction créée [{}] pour client [{}], offre [{}], idempotencyKey [{}]",
                    saved.getId(), client.getId(), offer.getId(), idempotencyKey);

        } catch (DataIntegrityViolationException e) {
            // Idempotence : transaction déjà créée pour cette clé
            // On ne renvoie PAS de nouvel OTP
            log.info("Transaction déjà existante pour idempotencyKey [{}]", idempotencyKey);
            return transactionRepository.findByClientAndIdempotencyKey(client, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Conflit d'idempotence sans transaction retrouvable"
                    ));
        }

        // Envoi OTP pour confirmation
        authService.requestOtp(client.getPhoneNumber());
        log.info("OTP envoyé pour confirmation de la transaction [{}]", saved.getId());

        return saved;
    }

    // ==================== CONFIRMATION OTP + RETRAIT ====================
    // WAIT_OTP → QUEUE_WITHDRAWAL

    /**
     * Point d'entrée unique de confirmation : le client transmet le
     * transactionId et le code OTP. Vérifie le code PUIS lance le retrait.
     * Un seul geste atomique, jamais l'un sans l'autre.
     */
    @Transactional
    public Transaction confirmOtpAndQueueWithdrawal(UUID transactionId, String otpCode) {
        Transaction transaction = findTransaction(transactionId);

        if (transaction.getStatus() != TransactionStatus.WAIT_OTP) {
            throw new IllegalStateException(
                    "Cette transaction n'est plus en attente de confirmation OTP (statut actuel : %s)"
                            .formatted(transaction.getStatus())
            );
        }

        // Vérification du code — lève une exception explicite si invalide
        authService.verifyOtp(transaction.getClient().getPhoneNumber(), otpCode);

        // Transition vers la file d'attente du retrait
        transitionTo(transaction, TransactionStatus.QUEUE_WITHDRAWAL);

        // Résoudre le template de retrait DEPUIS L'OPÉRATEUR (pas depuis l'offre)
        Operateur sourceOperator = transaction.getOffer().getSourceOperator();
        if (sourceOperator.getWithdrawalCommandTemplate() == null) {
            throw new IllegalStateException(
                    "L'opérateur [%s] n'a pas de gabarit de retrait configuré"
                            .formatted(sourceOperator.getCode())
            );
        }

        String resolvedContent = templateResolver.resolve(
                sourceOperator.getWithdrawalCommandTemplate(),
                transaction,
                null,
                null
        );

        // Créer la commande de retrait
        Commande withdrawalCommande = new Commande(
                transaction,
                CommandPhase.WITHDRAWAL,
                sourceOperator,
                resolvedContent
        );
        commandeRepository.save(withdrawalCommande);

        // Publier pour routage
        commandRoutingProducer.publishForRouting(withdrawalCommande);

        log.info("Transaction [{}] : OTP validé, retrait mis en file, commande [{}] publiée",
                transactionId, withdrawalCommande.getId());

        return transaction;
    }

    // ==================== ROUTAGE ====================
    // QUEUE_WITHDRAWAL → ASK_WITHDRAWAL
    // QUEUE_EXECUTE_COMMAND → ROUTE_EXECUTE_COMMAND

    /**
     * Appelé par CommandRoutingConsumer juste après qu'un device a été
     * verrouillé (HOLDS) pour cette commande.
     */
    @Transactional
    public void markCommandRouted(Commande commande) {
        Transaction transaction = commande.getTransaction();

        TransactionStatus target = commande.getPhase() == CommandPhase.WITHDRAWAL
                ? TransactionStatus.ASK_WITHDRAWAL
                : TransactionStatus.ROUTE_EXECUTE_COMMAND;

        transitionTo(transaction, target);
        log.info("Transaction [{}] : commande [{}] routée, statut -> {}",
                transaction.getId(), commande.getId(), target);
    }

    // ==================== RÉSULTATS ====================
    // ASK_WITHDRAWAL → WITHDRAWAL_DONE ou WITHDRAWAL_FAILED

    /**
     * Traite le résultat du retrait (appelé par ReconciliationService).
     */
    @Transactional
    public void handleWithdrawalResult(UUID transactionId, boolean success) {
        Transaction transaction = findTransaction(transactionId);

        if (!success) {
            // ASK_WITHDRAWAL → WITHDRAWAL_FAILED (Terminal)
            transitionTo(transaction, TransactionStatus.WITHDRAWAL_FAILED);
            transaction.setCompletedAt(Instant.now());
            log.info("Transaction [{}] : retrait échoué, aucun débit effectif", transactionId);
            return;
        }

        // ASK_WITHDRAWAL → WITHDRAWAL_DONE
        transitionTo(transaction, TransactionStatus.WITHDRAWAL_DONE);
        log.info("Transaction [{}] : retrait confirmé réussi", transactionId);

        // Enchaîner sur l'exécution → QUEUE_EXECUTE_COMMAND
        queueExecutionCommand(transaction);
    }

    // ROUTE_EXECUTE_COMMAND → EXECUTE_COMMAND_DONE ou EXECUTE_COMMAND_FAILED
    // EXECUTE_COMMAND_FAILED → COMPENSATION_IN_PROGRESS

    /**
     * Traite le résultat de l'exécution (appelé par ReconciliationService).
     * Gère le cas critique : retrait réussi / exécution échouée → compensation.
     */
    @Transactional
    public void handleExecutionResult(UUID transactionId, UUID failedCommandeId, boolean success) {
        Transaction transaction = findTransaction(transactionId);

        if (success) {
            // ROUTE_EXECUTE_COMMAND → EXECUTE_COMMAND_DONE (Terminal)
            transitionTo(transaction, TransactionStatus.EXECUTE_COMMAND_DONE);
            transaction.setCompletedAt(Instant.now());
            log.info("Transaction [{}] : exécution confirmée réussie, transaction terminée", transactionId);
            return;
        }

        // ÉCHEC : retrait réussi mais exécution échouée
        // ROUTE_EXECUTE_COMMAND → EXECUTE_COMMAND_FAILED
        transitionTo(transaction, TransactionStatus.EXECUTE_COMMAND_FAILED);
        log.warn("Transaction [{}] : exécution échouée après retrait réussi — déclenchement compensation",
                transactionId);

        // EXECUTE_COMMAND_FAILED → COMPENSATION_IN_PROGRESS
        transitionTo(transaction, TransactionStatus.COMPENSATION_IN_PROGRESS);

        Commande failedCommande = commandeRepository.findById(failedCommandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", failedCommandeId));

        compensationProducer.publishCompensation(transaction, failedCommande);
    }

    // ==================== COMPENSATION ====================
    // COMPENSATION_IN_PROGRESS → QUEUE_EXECUTE_COMMAND (retry) ou COMPENSATION_MANUAL_REVIEW (épuisé)

    /**
     * Remet une transaction en file d'attente pour retry (appelé par CompensationConsumer).
     * COMPENSATION_IN_PROGRESS → QUEUE_EXECUTE_COMMAND
     */
    @Transactional
    public void requeueForCompensationRetry(UUID transactionId) {
        Transaction transaction = findTransaction(transactionId);
        transitionTo(transaction, TransactionStatus.QUEUE_EXECUTE_COMMAND);
        log.info("Transaction [{}] : remise en file pour retry de compensation", transactionId);
    }

    /**
     * Bascule une transaction en reprise manuelle admin (appelé par CompensationConsumer).
     * COMPENSATION_IN_PROGRESS → COMPENSATION_MANUAL_REVIEW (Terminal)
     */
    @Transactional
    public void markCompensationManualReview(UUID transactionId) {
        Transaction transaction = findTransaction(transactionId);
        transitionTo(transaction, TransactionStatus.COMPENSATION_MANUAL_REVIEW);
        log.warn("Transaction [{}] : bascule en reprise manuelle admin (3 tentatives épuisées)", transactionId);
    }

    // ==================== PRIVÉES ====================

    /**
     * Met en file la commande d'exécution (recharge crédit/data ou dépôt MO).
     * WITHDRAWAL_DONE → QUEUE_EXECUTE_COMMAND
     */
    private void queueExecutionCommand(Transaction transaction) {
        transitionTo(transaction, TransactionStatus.QUEUE_EXECUTE_COMMAND);

        // Résoudre le template d'exécution (spécifique à l'offre)
        String resolvedContent = templateResolver.resolve(
                transaction.getOffer().getExecutionCommandTemplate(),
                transaction,
                transaction.getDestinationPhoneNumber(),
                transaction.getPayerPhoneNumber()
        );

        // Déterminer l'opérateur d'exécution
        Operateur executionOperator = resolveExecutionOperator(transaction);

        Commande executionCommande = new Commande(
                transaction,
                CommandPhase.EXECUTION,
                executionOperator,
                resolvedContent
        );
        commandeRepository.save(executionCommande);

        commandRoutingProducer.publishForRouting(executionCommande);

        log.info("Transaction [{}] : exécution mise en file, commande [{}] publiée, opérateur [{}]",
                transaction.getId(), executionCommande.getId(), executionOperator.getCode());
    }

    /**
     * Résout l'opérateur d'exécution :
     * - EXCHANGE_MO → destinationOperator
     * - CREDIT/DATA → sourceOperator
     */
    private Operateur resolveExecutionOperator(Transaction transaction) {
        Offer offer = transaction.getOffer();
        return offer.getDestinationOperator() != null
                ? offer.getDestinationOperator()
                : offer.getSourceOperator();
    }

    /**
     * Transitionne la transaction vers un nouveau statut (valide via TransactionStateMachine).
     */
    private void transitionTo(Transaction transaction, TransactionStatus target) {
        TransactionStatus current = transaction.getStatus();
        if (!TransactionStateMachine.canTransition(current, target)) {
            throw new IllegalStateTransitionException(
                    "Transaction", transaction.getId(), current, target
            );
        }
        transaction.setStatus(target);
    }

    private Transaction findTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }
}