package TNB.Switch.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${tnb.routing.withdrawal-topic}")
    private String withdrawalTopic;

    @Value("${tnb.routing.execution-topic}")
    private String executionTopic;

    @Value("${tnb.routing.compensation-topic}")
    private String compensationTopic;

    @Value("${tnb.routing.dlq-topic}")
    private String dlqTopic;

    @Value("${tnb.reconciliation.topic}")
    private String reconciliationTopic;

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1; // à passer à 3 en prod multi-broker

    @Bean
    public NewTopic withdrawalTopic() {
        return TopicBuilder.name(withdrawalTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic executionTopic() {
        return TopicBuilder.name(executionTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic compensationTopic() {
        return TopicBuilder.name(compensationTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic reconciliationTopic() {
        return TopicBuilder.name(reconciliationTopic)
                .partitions(PARTITIONS)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    /**
     * DLQ manuelle, distincte des topics *-dlt auto-générés par
     * @RetryableTopic. Non utilisée activement par les consumers actuels
     * (qui s'appuient tous sur le DLT natif) — conservée pour un usage
     * futur si besoin.
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(dlqTopic)
                .partitions(1)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}