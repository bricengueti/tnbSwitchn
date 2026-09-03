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
import org.springframework.transaction.annotation.Transactional;

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
                                  ReconciliationService reconciliationService,
                                  NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.iaClient = iaClient;
        this.reconciliationService = reconciliationService;
        this.notificationService = notificationService;
    }

    /**
     * Consomme les tickets de réconciliation depuis reconciliation-topic.
     */
    @RetryableTopic(
            attempts = "${tnb.ia.reconciliation.max-retries:3}",
            backoff = @org.springframework.retry.annotation.Backoff(
                    delayExpression = "#{${tnb.routing.retry-delay-seconds:30} * 1000}"
            ),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            kafkaTemplate = "defaultRetryTopicKafkaTemplate"  // ⬅️ AJOUT
    )
    @KafkaListener(topics = "${tnb.reconciliation.topic}", containerFactory = "reconciliationKafkaListenerContainerFactory")
    @Transactional
    public void consume(@Payload ReconciliationEvent event, Acknowledgment ack) {
        log.debug("Réception du ticket de réconciliation pour le message [{}]", event.messageId());

        // 1. Charger le message
        MessageOperateurBrut message = messageRepository.findById(event.messageId())
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", event.messageId()));

        log.info("Traitement du message [{}] pour réconciliation", message.getId());

        // 2. Appel IA SYNCHRONE — si ça lève, on ne catch rien ici
        IaExtractionResult result = iaClient.classify(message.getRawContent());

        // 3. Traiter le résultat IA
        reconciliationService.handleIaResult(message.getId(), result);

        // 4. Accusé de réception
        ack.acknowledge();

        log.info("Message [{}] réconcilié avec succès", message.getId());
    }

    /**
     * Dernier arrêt après tnb.ia.reconciliation.max-retries tentatives
     * infructueuses contre le service IA.
     */
    @DltHandler
    public void handleDlt(@Payload ReconciliationEvent event) {
        MessageOperateurBrut message = messageRepository.findById(event.messageId())
                .orElseThrow(() -> new ResourceNotFoundException("MessageOperateurBrut", event.messageId()));

        message.setProcessingStatus(MessageProcessingStatus.AMBIGUOUS);
        messageRepository.save(message);

        log.error("Message [{}] : service IA injoignable après épuisement des tentatives", message.getId());

        notificationService.alertAdmin(
                "Message opérateur %s : service IA de réconciliation injoignable, reprise manuelle requise"
                        .formatted(message.getId())
        );
    }
}