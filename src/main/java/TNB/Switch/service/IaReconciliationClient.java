package TNB.Switch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client synchrone du service IA. Appelé depuis un consumer Kafka
 * (ReconciliationConsumer) — s'il lève une exception (timeout, service
 * indisponible), Kafka retente automatiquement via @RetryableTopic,
 * remplaçant le mécanisme PENDING_AI_RETRY qui aurait nécessité un
 * scheduler dédié.
 */
@Service
public class IaReconciliationClient {

    private final RestClient restClient;

    public IaReconciliationClient(
            RestClient.Builder restClientBuilder,
            @Value("${tnb.ia.reconciliation.base-url}") String baseUrl,
            @Value("${tnb.ia.reconciliation.timeout-ms}") int timeoutMs) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Lève une RuntimeException (via onStatus/erreur réseau) si le
     * service IA échoue — c'est volontaire : c'est ce qui déclenche le
     * retry Kafka côté ReconciliationConsumer. Aucun fallback silencieux
     * ici, contrairement à l'ancienne version avec circuit breaker.
     */
    public IaExtractionResult classify(String rawContent) {
        return restClient.post()
                .uri("/classify")
                .body(new ClassifyRequest(rawContent))
                .retrieve()
                .body(IaExtractionResult.class);
    }

    private record ClassifyRequest(String rawContent) {}
}