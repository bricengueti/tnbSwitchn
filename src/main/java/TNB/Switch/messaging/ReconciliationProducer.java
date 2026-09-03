package TNB.Switch.messaging;

import TNB.Switch.entity.MessageOperateurBrut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Émet un ticket de réconciliation sur le topic Kafka.
 * Appelé par ReconciliationService.receiveRawMessage() après la
 * persistance immédiate du message opérateur brut.
 *
 * =====================================================================
 *                    RECONCILIATION PRODUCER
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  ReconciliationService.receiveRawMessage()                         │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  1. MessageOperateurBrut créé et persisté                   │   │
 *  │  │     status = PENDING_AI                                    │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  2. ReconciliationProducer.publish(message)                 │   │
 *  │  │                                                             │   │
 *  │  │     event = new ReconciliationEvent(message.getId())       │   │
 *  │  │     kafkaTemplate.executeInTransaction(                    │   │
 *  │  │         operations.send(topic, key, event)                 │   │
 *  │  │     )                                                       │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │                    KAFKA BROKER                             │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  reconciliation-topic                                │   │   │
 *  │  │  │  [ticket1] [ticket2] [ticket3] ...                 │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  ReconciliationConsumer.consume()                           │   │
 *  │  │  → IA → handleIaResult()                                   │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 *  LÉGENDE :
 *    ───  = Flux normal
 *    Ticket = { messageId: UUID }
 * =====================================================================
 */
@Component
public class ReconciliationProducer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public ReconciliationProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${tnb.reconciliation.topic:tnb.reconciliation}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Publie un ticket de réconciliation pour le message opérateur brut.
     * Le ticket ne contient que l'ID du message — le consumer recharge
     * l'entité complète depuis la base.
     */
    public void publish(MessageOperateurBrut message) {
        ReconciliationEvent event = new ReconciliationEvent(message.getId());

        kafkaTemplate.executeInTransaction(operations ->
                operations.send(topic, message.getId().toString(), event)
        );

        log.info("Message [{}] publié pour réconciliation sur le topic [{}]",
                message.getId(), topic);
    }
}