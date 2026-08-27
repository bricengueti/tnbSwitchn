package TNB.Switch.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Un seul KafkaTemplate<String, Object> partagé par tous les producers
 * (CommandRoutingProducer, CompensationProducer, ReconciliationProducer) —
 * JsonSerializer sérialise n'importe quel record passé en payload sans
 * avoir besoin d'un type générique strict par producer.
 *
 * KafkaTransactionManager nécessaire pour que
 * kafkaTemplate.executeInTransaction(...) fonctionne réellement — sans
 * lui, transaction-id-prefix seul (déjà en config) ne suffit pas à
 * garantir l'atomicité recherchée.
 */
@Configuration
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaProducerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configs = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(configs);
        // Le transaction-id-prefix vient déjà de application.properties
        // (spring.kafka.producer.transaction-id-prefix) et est repris par
        // buildProducerProperties — pas besoin de le redéfinir ici.
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }
}