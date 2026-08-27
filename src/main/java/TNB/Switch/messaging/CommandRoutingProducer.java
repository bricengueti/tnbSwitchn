package TNB.Switch.messaging;

import TNB.Switch.entity.Commande;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Émet un "ticket" sur le bon tube pneumatique (topic) selon la phase de
 * la commande — RETRAIT part sur withdrawal-topic, EXÉCUTION sur
 * execution-topic. Appelé par TransactionService juste après la création
 * d'une Commande.
 *
 * =====================================================================
 *                    COMMAND ROUTING PRODUCER
 * =====================================================================
 *
 *  TransactionService.confirmOtpAndQueueWithdrawal()
 *  ou TransactionService.queueExecutionCommand()
 *                │
 *                ▼
 *      ┌─────────────────────┐
 *      │  Commande créée     │
 *      │  phase = WITHDRAWAL │
 *      │  ou EXECUTION       │
 *      └──────────┬──────────┘
 *                 │
 *                 ▼
 *      ┌─────────────────────┐
 *      │  switch (phase)     │
 *      ├─────────────────────┤
 *      │  WITHDRAWAL →       │
 *      │    withdrawal-topic │
 *      │  EXECUTION   →      │
 *      │    execution-topic  │
 *      └─────────────────────┘
 *                 │
 *                 ▼
 *      ┌─────────────────────────────────────┐
 *      │ kafkaTemplate.executeInTransaction │
 *      │   operations.send(topic, key, event)│
 *      └─────────────────────────────────────┘
 *                 │
 *                 ▼
 *      ┌─────────────────────────────────────┐
 *      │           KAFKA BROKER              │
 *      │  ┌─────────────────────────────┐   │
 *      │  │ withdrawal-topic            │   │
 *      │  │ partition 0,1,2             │   │
 *      │  └─────────────────────────────┘   │
 *      │  ┌─────────────────────────────┐   │
 *      │  │ execution-topic             │   │
 *      │  │ partition 0,1,2             │   │
 *      │  └─────────────────────────────┘   │
 *      └─────────────────────────────────────┘
 *                 │
 *                 ▼
 *      ┌─────────────────────────────────────┐
 *      │  CommandRoutingConsumer             │
 *      │  - consumeWithdrawal()              │
 *      │  - consumeExecution()               │
 *      └─────────────────────────────────────┘
 *                 │
 *                 ▼
 *      ┌─────────────────────────────────────┐
 *      │  RoutingService.routeSingleCommand()│
 *      │  + DeviceService.markHolds()        │
 *      │  + CommandDispatcher.dispatch()     │
 *      └─────────────────────────────────────┘
 *
 * =====================================================================
 *  LÉGENDE :
 *    ───  = Flux normal
 *    Topic = Destination Kafka
 *    Event = { commandeId: UUID }
 * =====================================================================
 */
@Component
public class CommandRoutingProducer {

    private static final Logger log = LoggerFactory.getLogger(CommandRoutingProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String withdrawalTopic;
    private final String executionTopic;

    public CommandRoutingProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${tnb.routing.withdrawal-topic}") String withdrawalTopic,
            @Value("${tnb.routing.execution-topic}") String executionTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.withdrawalTopic = withdrawalTopic;
        this.executionTopic = executionTopic;
    }

    /**
     * Publie une commande pour routage.
     * - WITHDRAWAL → withdrawal-topic
     * - EXECUTION → execution-topic
     *
     * Le message est un ticket ({ commandeId: UUID }) envoyé en transaction.
     */
    public void publishForRouting(Commande commande) {
        String topic = switch (commande.getPhase()) {
            case WITHDRAWAL -> withdrawalTopic;
            case EXECUTION -> executionTopic;
        };
        CommandRoutingEvent event = new CommandRoutingEvent(commande.getId());

        kafkaTemplate.executeInTransaction(operations ->
                operations.send(topic, commande.getId().toString(), event)
        );

        log.info("Commande [{}] publiée sur le topic {}", commande.getId(), topic);
    }
}