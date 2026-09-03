package TNB.Switch.config;

import TNB.Switch.messaging.CommandRoutingEvent;
import TNB.Switch.messaging.CompensationEvent;
import TNB.Switch.messaging.ReconciliationEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerFactoriesConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConsumerFactoriesConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    private Map<String, Object> baseConsumerProps() {
        return new HashMap<>(kafkaProperties.buildConsumerProperties(null));
    }

    // Applique ack-mode=MANUAL_IMMEDIATE + concurrency=3, comme le faisait
    // avant l'auto-configuration Boot via spring.kafka.listener.* — nécessaire
    // ici car ces factories custom ne lisent plus ces propriétés automatiquement.
    private <T> void applyListenerDefaults(ConcurrentKafkaListenerContainerFactory<String, T> factory) {
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
    }

    // ================= CommandRoutingEvent =================
    @Bean
    public ConsumerFactory<String, CommandRoutingEvent> commandRoutingConsumerFactory() {
        JsonDeserializer<CommandRoutingEvent> deserializer = new JsonDeserializer<>(CommandRoutingEvent.class);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("TNB.Switch.*");
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CommandRoutingEvent> commandRoutingKafkaListenerContainerFactory(
            ConsumerFactory<String, CommandRoutingEvent> commandRoutingConsumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, CommandRoutingEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(commandRoutingConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        applyListenerDefaults(factory);
        return factory;
    }

    // ================= CompensationEvent =================
    @Bean
    public ConsumerFactory<String, CompensationEvent> compensationConsumerFactory() {
        JsonDeserializer<CompensationEvent> deserializer = new JsonDeserializer<>(CompensationEvent.class);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("TNB.Switch.*");
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CompensationEvent> compensationKafkaListenerContainerFactory(
            ConsumerFactory<String, CompensationEvent> compensationConsumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, CompensationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(compensationConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        applyListenerDefaults(factory);
        return factory;
    }

    // ================= ReconciliationEvent =================
    @Bean
    public ConsumerFactory<String, ReconciliationEvent> reconciliationConsumerFactory() {
        JsonDeserializer<ReconciliationEvent> deserializer = new JsonDeserializer<>(ReconciliationEvent.class);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("TNB.Switch.*");
        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReconciliationEvent> reconciliationKafkaListenerContainerFactory(
            ConsumerFactory<String, ReconciliationEvent> reconciliationConsumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ReconciliationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reconciliationConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        applyListenerDefaults(factory);
        return factory;
    }
}