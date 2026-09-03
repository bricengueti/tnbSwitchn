package TNB.Switch.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaDlqConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaDlqConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> dlqProducerFactory() {
        Map<String, Object> configs = new HashMap<>(kafkaProperties.buildProducerProperties(null));

        // ✅ Déjà non transactionnel
        configs.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean(name = "dlqKafkaTemplate")
    public KafkaTemplate<String, Object> dlqKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }
}