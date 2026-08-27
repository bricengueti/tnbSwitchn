package TNB.Switch.messaging;

import TNB.Switch.entity.Commande;
import TNB.Switch.entity.CompensationAttempt;
import TNB.Switch.entity.Transaction;
import TNB.Switch.enums.CommandPhase;
import TNB.Switch.repository.CommandeRepository;
import TNB.Switch.repository.CompensationAttemptRepository;
import TNB.Switch.repository.TransactionRepository;
import TNB.Switch.service.NotificationService;
import TNB.Switch.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consomme les événements de compensation (retrait réussi, exécution
 * échouée — CDC §8.4, cas critique). Retente jusqu'à 3 fois ; au-delà,
 * bascule en reprise manuelle admin (COMPENSATION_MANUAL_REVIEW), sans
 * remboursement automatique — règle actée avec le client.
 *
 * Le device n'est PAS présélectionné ici : la nouvelle Commande de retry
 * suit le pipeline de routage standard, qui sait déjà choisir un device
 * AVAILABLE — potentiellement le même device qui a échoué, s'il s'est
 * remis en ligne entre-temps (échec souvent transitoire : coupure réseau,
 * device éteint temporairement, pas forcément une panne durable).
 */
@Component
public class CompensationConsumer {

    private static final Logger log = LoggerFactory.getLogger(CompensationConsumer.class);
    private static final int MAX_COMPENSATION_ATTEMPTS = 3;

    private final TransactionRepository transactionRepository;
    private final CommandeRepository commandeRepository;
    private final CompensationAttemptRepository compensationAttemptRepository;
    private final CommandRoutingProducer commandRoutingProducer;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    public CompensationConsumer(TransactionRepository transactionRepository,
                                CommandeRepository commandeRepository,
                                CompensationAttemptRepository compensationAttemptRepository,
                                CommandRoutingProducer commandRoutingProducer,
                                TransactionService transactionService, NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.commandeRepository = commandeRepository;
        this.compensationAttemptRepository = compensationAttemptRepository;
        this.commandRoutingProducer = commandRoutingProducer;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }
    @KafkaListener(topics = "${tnb.routing.compensation-topic}")
    @Transactional
    public void consumeCompensation(@Payload CompensationEvent event, Acknowledgment ack) {
        Transaction transaction = transactionRepository.findById(event.transactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction introuvable pour compensation : " + event.transactionId()
                ));

        Commande failedCommande = commandeRepository.findById(event.failedCommandeId())
                .orElseThrow(() -> new IllegalStateException(
                        "Commande introuvable pour compensation : " + event.failedCommandeId()
                ));

        int attemptsSoFar = compensationAttemptRepository.countByTransaction(transaction);

        // La limite de 3 ne s'applique qu'aux tentatives automatiques —
        // une relance manuelle admin (isManualRetry=true) est un choix humain
        // explicite qui contourne volontairement cette limite.
        if (!event.isManualRetry() && attemptsSoFar >= MAX_COMPENSATION_ATTEMPTS) {
            transactionService.markCompensationManualReview(transaction.getId());
            log.error(
                    "Transaction [{}] : {} tentatives de compensation épuisées, bascule en reprise manuelle admin",
                    transaction.getId(), MAX_COMPENSATION_ATTEMPTS
            );
            notificationService.alertAdmin(
                    "Transaction %s : compensation épuisée après %d tentatives, reprise manuelle requise"
                            .formatted(transaction.getId(), MAX_COMPENSATION_ATTEMPTS)
            );
            ack.acknowledge();
            return;
        }

        Commande retryCommande = new Commande(
                transaction, CommandPhase.EXECUTION,
                failedCommande.getOperateur(), failedCommande.getResolvedContent()
        );
        commandeRepository.save(retryCommande);

        int attemptNumber = attemptsSoFar + 1;
        compensationAttemptRepository.save(
                new CompensationAttempt(transaction, retryCommande, attemptNumber)
        );

        transactionService.requeueForCompensationRetry(transaction.getId());

        commandRoutingProducer.publishForRouting(retryCommande);

        log.warn("Transaction [{}] : tentative de compensation {} ({}) — nouvelle commande [{}] publiée pour routage",
                transaction.getId(), attemptNumber, event.isManualRetry() ? "manuelle" : "automatique",
                retryCommande.getId());

        ack.acknowledge();
    }
}