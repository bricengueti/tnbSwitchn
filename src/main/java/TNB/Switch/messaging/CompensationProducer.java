package TNB.Switch.messaging;

import TNB.Switch.entity.Commande;
import TNB.Switch.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CompensationProducer {

    private static final Logger log = LoggerFactory.getLogger(CompensationProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String compensationTopic;

    public CompensationProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${tnb.routing.compensation-topic}") String compensationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.compensationTopic = compensationTopic;
    }

    /**
     * Appelé automatiquement par TransactionService après un échec
     * d'exécution — compté dans la limite des 3 tentatives.
     */
    public void publishCompensation(Transaction transaction, Commande failedCommande) {
        publish(transaction, failedCommande, false);
    }

    /**
     * Appelé par AdminSupervisionService pour une relance manuelle après
     * épuisement des tentatives automatiques — ignore la limite de 3.
     */
    public void publishManualRetry(Transaction transaction, Commande failedCommande) {
        publish(transaction, failedCommande, true);
    }

    private void publish(Transaction transaction, Commande failedCommande, boolean isManualRetry) {
        CompensationEvent event = new CompensationEvent(
                transaction.getId(), failedCommande.getId(), transaction.getAmount(), isManualRetry
        );

        kafkaTemplate.executeInTransaction(operations ->
                operations.send(compensationTopic, transaction.getId().toString(), event)
        );

        log.warn("Compensation publiée pour la transaction [{}] (manuelle={})",
                transaction.getId(), isManualRetry);
    }
}