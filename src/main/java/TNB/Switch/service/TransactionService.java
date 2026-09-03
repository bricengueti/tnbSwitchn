package TNB.Switch.service;

import TNB.Switch.annotation.IdempotencyKey;
import TNB.Switch.annotation.Idempotent;
import TNB.Switch.entity.*;
import TNB.Switch.enums.CommandPhase;
import TNB.Switch.enums.OfferType;
import TNB.Switch.enums.TransactionStatus;
import TNB.Switch.exeption.IllegalStateTransitionException;
import TNB.Switch.exeption.InvalidPhoneNumberException;
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
 * Inclut la validation des numéros de téléphone via PhoneNumberValidationService.
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
    private final PhoneNumberValidationService phoneNumberValidationService;  // ✅ AJOUT
    private final OperateurService operateurService;  // ✅ AJOUT

    public TransactionService(TransactionRepository transactionRepository,
                              CommandeRepository commandeRepository,
                              CommandRoutingProducer commandRoutingProducer,
                              CompensationProducer compensationProducer,
                              CommandTemplateResolver templateResolver,
                              AuthService authService,
                              PhoneNumberValidationService phoneNumberValidationService,
                              OperateurService operateurService) {
        this.transactionRepository = transactionRepository;
        this.commandeRepository = commandeRepository;
        this.commandRoutingProducer = commandRoutingProducer;
        this.compensationProducer = compensationProducer;
        this.templateResolver = templateResolver;
        this.authService = authService;
        this.phoneNumberValidationService = phoneNumberValidationService;
        this.operateurService = operateurService;
    }

    // ==================== CRÉATION ====================

    /**
     * Crée la transaction (idempotente via @Idempotent + contrainte DB).
     * Statut initial WAIT_OTP. Déclenche immédiatement l'envoi d'un OTP.
     *
     * ✅ Validation des numéros de téléphone intégrée.
     */
    @Idempotent
    @Transactional("transactionManager")
    public Transaction createTransaction(
            @IdempotencyKey String idempotencyKey,
            User client,
            Offer offer,
            String destinationPhoneNumber,
            String payerPhoneNumber,
            Operateur fromOperateur,    // ✅ AJOUT
            Operateur toOperateur) {    // ✅ AJOUT

        // 1. Validation des opérateurs
        if (fromOperateur == null || toOperateur == null) {
            throw new IllegalArgumentException("Les opérateurs source et destination sont obligatoires");
        }

        // 2. Validation : l'opérateur source supporte le type d'offre
        if (!fromOperateur.supportsOfferType(offer.getType())) {
            throw new InvalidPhoneNumberException(
                    String.format("L'opérateur source [%s] ne supporte pas le type d'offre [%s]",
                            fromOperateur.getCode(), offer.getType())
            );
        }

        // 3. Validation EXCHANGE_MO
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

        // 4. ✅ Validation des numéros de téléphone
        validatePhoneNumbers(payerPhoneNumber, destinationPhoneNumber, fromOperateur, toOperateur, offer);

        // 5. Création de la transaction
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
                    fromOperateur,
                    toOperateur
            );
            saved = transactionRepository.save(transaction);
            log.info("Transaction créée [{}] pour client [{}], offre [{}], fromOperateur [{}], toOperateur [{}]",
                    saved.getId(), client.getId(), offer.getId(),
                    fromOperateur.getCode(), toOperateur.getCode());

        } catch (DataIntegrityViolationException e) {
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

    /**
     * Point d'entrée unique de confirmation : le client transmet le
     * transactionId et le code OTP. Vérifie le code PUIS lance le retrait.
     */
    @Transactional("transactionManager")
    public Transaction confirmOtpAndQueueWithdrawal(UUID transactionId, String otpCode) {
        Transaction transaction = findTransaction(transactionId);

        if (transaction.getStatus() != TransactionStatus.WAIT_OTP) {
            throw new IllegalStateException(
                    "Cette transaction n'est plus en attente de confirmation OTP (statut actuel : %s)"
                            .formatted(transaction.getStatus())
            );
        }

        // Vérification du code OTP
        authService.verifyOtp(transaction.getClient().getPhoneNumber(), otpCode);

        // Transition vers la file d'attente du retrait
        transitionTo(transaction, TransactionStatus.QUEUE_WITHDRAWAL);

        // ✅ Résoudre le template de retrait DEPUIS L'OPÉRATEUR DESTINATION
        Operateur toOperateur = transaction.getToOperateur();
        if (toOperateur == null) {
            throw new IllegalStateException("L'opérateur destination n'est pas défini pour la transaction");
        }
        if (toOperateur.getWithdrawalCommandTemplate() == null) {
            throw new IllegalStateException(
                    "L'opérateur [%s] n'a pas de gabarit de retrait configuré"
                            .formatted(toOperateur.getCode())
            );
        }

        String resolvedContent = templateResolver.resolve(
                toOperateur.getWithdrawalCommandTemplate(),
                transaction,
                null,
                null
        );

        // Créer la commande de retrait
        Commande withdrawalCommande = new Commande(
                transaction,
                CommandPhase.WITHDRAWAL,
                toOperateur,
                resolvedContent
        );
        commandeRepository.save(withdrawalCommande);

        // Publier pour routage
        commandRoutingProducer.publishForRouting(withdrawalCommande);

        log.info("Transaction [{}] : OTP validé, retrait mis en file, commande [{}] publiée vers opérateur [{}]",
                transactionId, withdrawalCommande.getId(), toOperateur.getCode());

        return transaction;
    }

    // ==================== ROUTAGE ====================

    @Transactional("transactionManager")
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

    @Transactional
    public void handleWithdrawalResult(UUID transactionId, boolean success) {
        Transaction transaction = findTransaction(transactionId);

        if (!success) {
            transitionTo(transaction, TransactionStatus.WITHDRAWAL_FAILED);
            transaction.setCompletedAt(Instant.now());
            log.info("Transaction [{}] : retrait échoué, aucun débit effectif", transactionId);
            return;
        }

        transitionTo(transaction, TransactionStatus.WITHDRAWAL_DONE);
        log.info("Transaction [{}] : retrait confirmé réussi", transactionId);

        queueExecutionCommand(transaction);
    }

    @Transactional("transactionManager")
    public void handleExecutionResult(UUID transactionId, UUID failedCommandeId, boolean success) {
        Transaction transaction = findTransaction(transactionId);

        if (success) {
            transitionTo(transaction, TransactionStatus.EXECUTE_COMMAND_DONE);
            transaction.setCompletedAt(Instant.now());
            log.info("Transaction [{}] : exécution confirmée réussie, transaction terminée", transactionId);
            return;
        }

        transitionTo(transaction, TransactionStatus.EXECUTE_COMMAND_FAILED);
        log.warn("Transaction [{}] : exécution échouée après retrait réussi — déclenchement compensation",
                transactionId);

        transitionTo(transaction, TransactionStatus.COMPENSATION_IN_PROGRESS);

        Commande failedCommande = commandeRepository.findById(failedCommandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", failedCommandeId));

        compensationProducer.publishCompensation(transaction, failedCommande);
    }

    // ==================== COMPENSATION ====================

    @Transactional("transactionManager")
    public void requeueForCompensationRetry(UUID transactionId) {
        Transaction transaction = findTransaction(transactionId);
        transitionTo(transaction, TransactionStatus.QUEUE_EXECUTE_COMMAND);
        log.info("Transaction [{}] : remise en file pour retry de compensation", transactionId);
    }

    @Transactional("transactionManager")
    public void markCompensationManualReview(UUID transactionId) {
        Transaction transaction = findTransaction(transactionId);
        transitionTo(transaction, TransactionStatus.COMPENSATION_MANUAL_REVIEW);
        log.warn("Transaction [{}] : bascule en reprise manuelle admin (3 tentatives épuisées)", transactionId);
    }

    // ==================== PRIVÉES ====================

    /**
     * ✅ Valide les numéros de téléphone.
     */
    private void validatePhoneNumbers(String payerPhoneNumber,
                                      String destinationPhoneNumber,
                                      Operateur fromOperateur,
                                      Operateur toOperateur,
                                      Offer offer) {
        // Pour CREDIT et DATA, seul le numéro du destinataire est requis
        if (offer.getType() == OfferType.CREDIT || offer.getType() == OfferType.DATA) {
            if (destinationPhoneNumber == null || destinationPhoneNumber.isBlank()) {
                throw new IllegalArgumentException(
                        "Un numéro de destination est requis pour les offres CREDIT et DATA"
                );
            }
            phoneNumberValidationService.validatePhoneNumberBelongsToOperateur(
                    destinationPhoneNumber, toOperateur
            );
            // Le payerPhoneNumber n'est pas requis pour CREDIT/DATA
            // (le client paie depuis son propre compte)
            return;
        }

        // Pour EXCHANGE_MO, les deux numéros sont requis
        if (offer.getType() == OfferType.EXCHANGE_MO) {
            // Payer
            if (payerPhoneNumber == null || payerPhoneNumber.isBlank()) {
                throw new IllegalArgumentException(
                        "Un numéro source (payer) est requis pour les offres EXCHANGE_MO"
                );
            }
            phoneNumberValidationService.validatePhoneNumberBelongsToOperateur(
                    payerPhoneNumber, fromOperateur
            );

            // Destination
            if (destinationPhoneNumber == null || destinationPhoneNumber.isBlank()) {
                throw new IllegalArgumentException(
                        "Un numéro de destination est requis pour les offres EXCHANGE_MO"
                );
            }
            phoneNumberValidationService.validatePhoneNumberBelongsToOperateur(
                    destinationPhoneNumber, toOperateur
            );

            // Vérifier que les numéros sont différents
            if (payerPhoneNumber.replaceAll("[^0-9]", "")
                    .equals(destinationPhoneNumber.replaceAll("[^0-9]", ""))) {
                throw new InvalidPhoneNumberException(
                        "Le numéro de l'émetteur et du destinataire ne peuvent pas être identiques"
                );
            }
        }
    }

    /**
     * Met en file la commande d'exécution.
     */
    private void queueExecutionCommand(Transaction transaction) {
        transitionTo(transaction, TransactionStatus.QUEUE_EXECUTE_COMMAND);

        // ✅ Récupérer le template d'exécution depuis l'opérateur source
        Operateur fromOperateur = transaction.getFromOperateur();
        if (fromOperateur == null) {
            throw new IllegalStateException("L'opérateur source n'est pas défini pour la transaction");
        }

        CommandTemplate executionTemplate = fromOperateur.getExecutionTemplateForOfferType(
                transaction.getOffer().getType()
        );
        if (executionTemplate == null) {
            throw new IllegalStateException(
                    "L'opérateur [%s] n'a pas de template d'exécution pour le type d'offre [%s]"
                            .formatted(fromOperateur.getCode(), transaction.getOffer().getType())
            );
        }

        String resolvedContent = templateResolver.resolve(
                executionTemplate,
                transaction,
                transaction.getDestinationPhoneNumber(),
                transaction.getPayerPhoneNumber()
        );

        // ✅ L'opérateur d'exécution est l'opérateur source (fromOperateur)
        Commande executionCommande = new Commande(
                transaction,
                CommandPhase.EXECUTION,
                fromOperateur,
                resolvedContent
        );
        commandeRepository.save(executionCommande);

        commandRoutingProducer.publishForRouting(executionCommande);

        log.info("Transaction [{}] : exécution mise en file, commande [{}] publiée, opérateur [{}]",
                transaction.getId(), executionCommande.getId(), fromOperateur.getCode());
    }

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