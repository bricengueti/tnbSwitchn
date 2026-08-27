package TNB.Switch.messaging;

import TNB.Switch.entity.MessageOperateurBrut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProducer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public ReconciliationProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${tnb.reconciliation.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(MessageOperateurBrut message) {
        ReconciliationEvent event = new ReconciliationEvent(message.getId());
        kafkaTemplate.executeInTransaction(operations ->
                operations.send(topic, message.getId().toString(), event)
        );
        log.info("Message [{}] publié pour réconciliation", message.getId());
    }
}