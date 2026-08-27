package TNB.Switch.messaging;
import TNB.Switch.entity.MessageOperateurBrut;
import TNB.Switch.enums.MessageProcessingStatus;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.repository.MessageOperateurBrutRepository;
import TNB.Switch.service.IaExtractionResult;
import TNB.Switch.service.IaReconciliationClient;
import TNB.Switch.service.NotificationService;
import TNB.Switch.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Reçoit le ticket "message à réconcilier", appelle l'IA de façon
 * SYNCHRONE. Si l'appel échoue (timeout, service IA down), l'exception
 * remonte et @RetryableTopic fait retenter Kafka automatiquement — c'est
 * ça qui remplace PENDING_AI_RETRY + le scheduler qu'on n'a jamais écrit.
 * Après épuisement, handleDlt() bascule le message en AMBIGUOUS.
 */
@Component
public class ReconciliationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationConsumer.class);

    private final MessageOperateurBrutRepository messageRepository;
    private final IaReconciliationClient iaClient;
    private final ReconciliationService reconciliationService;
    private final NotificationService notificationService;


    public ReconciliationConsumer(MessageOperateurBrutRepository messageRepository,
                                  IaReconciliationClient iaClient,
                                  ReconciliationService reconciliationService, NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.iaClient = iaClient;
        this.reconciliationService = reconciliationService;
        this.notificationService = notificationService;
    }

    @RetryableTopic(
            attempts = "${tnb.ia.reconciliation.max-retries}",
            backoff = @org.springframework.retry.annotation.Backoff(
                    delayExpression = "${tnb.routing.retry-delay-seconds} * 1000"
            ),
            dltStrategy = DltStrategy.FAIL_ON_ERROR
            // Pas de include() ciblé ici, contrairement à CommandRoutingConsumer :
            // toute exception (timeout IA, service down, erreur réseau) doit
            // déclencher un retry — l'IA est un service externe, on ne sait
            // pas a priori quel type d'exception une panne va lever.
    )
    @KafkaListener(topics = "${tnb.reconciliation.topic}")
    public void consume(@Payload ReconciliationEvent event, Acknowledgment ack) {
        MessageOperateurBrut message = messageRepository.findById(event.messageId())
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", event.messageId()));

        // Appel SYNCHRONE — si ça lève, on ne catch rien ici : l'exception
        // doit remonter pour que @RetryableTopic fasse son travail.
        IaExtractionResult result = iaClient.classify(message.getRawContent());

        reconciliationService.handleIaResult(message.getId(), result);

        ack.acknowledge();
    }

    /**
     * Dernier arrêt après tnb.ia.reconciliation.max-retries tentatives
     * infructueuses contre le service IA. Le message bascule en AMBIGUOUS
     * (reprise manuelle admin) plutôt que de rester bloqué indéfiniment.
     */
    @DltHandler
    public void handleDlt(@Payload ReconciliationEvent event) {
        MessageOperateurBrut message = messageRepository.findById(event.messageId())
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", event.messageId()));

        message.setProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
        log.error("Message [{}] : service IA injoignable après épuisement des tentatives", message.getId());

        notificationService.alertAdmin(
                "Message opérateur %s : service IA de réconciliation injoignable, reprise manuelle requise"
                        .formatted(message.getId())
        );
    }
}