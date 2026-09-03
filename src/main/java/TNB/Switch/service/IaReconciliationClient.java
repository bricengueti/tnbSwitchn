package TNB.Switch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client synchrone du service IA. Appelé depuis un consumer Kafka
 * (ReconciliationConsumer) — s'il lève une exception (timeout, service
 * indisponible), Kafka retente automatiquement via @RetryableTopic,
 * remplaçant le mécanisme PENDING_AI_RETRY qui aurait nécessité un
 * scheduler dédié.
 *
 * =====================================================================
 *                    IA RECONCILIATION CLIENT
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │                    ReconciliationConsumer                          │
 *  │                    consume(event, ack)                             │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  iaClient.classify(rawContent)                              │   │
 *  │  │                                                             │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  SUCCÈS → retourne IaExtractionResult               │   │   │
 *  │  │  │  ÉCHEC (timeout/5xx) → lève RuntimeException        │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │              ┌───────────────┴───────────────┐                     │
 *  │              │                               │                     │
 *  │              ▼                               ▼                     │
 *  │  ┌─────────────────────┐       ┌─────────────────────────────┐   │
 *  │  │ SUCCÈS              │       │ ÉCHEC                       │   │
 *  │  │ handleIaResult()    │       │ @RetryableTopic             │   │
 *  │  │ → CLASSIFIED        │       │ → retry avec backoff        │   │
 *  │  └─────────────────────┘       └─────────────────────────────┘   │
 *  │                                                     │             │
 *  │                                                     ▼             │
 *  │                                    ┌─────────────────────────────┐│
 *  │                                    │ ÉPUISEMENT (max-retries)    ││
 *  │                                    │ @DltHandler                 ││
 *  │                                    │ → AMBIGUOUS                 ││
 *  │                                    │ → notificationService       ││
 *  │                                    └─────────────────────────────┘│
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 *  LÉGENDE :
 *    ───  = Flux normal
 *    - - - = Flux de retry
 *    ••••  = Flux DLQ
 * =====================================================================
 */
@Service
public class IaReconciliationClient {

    private static final Logger log = LoggerFactory.getLogger(IaReconciliationClient.class);

    private final RestClient restClient;

    public IaReconciliationClient(
            RestClient.Builder restClientBuilder,
            @Value("${tnb.ia.reconciliation.base-url:http://localhost:8081/api}") String baseUrl,
            @Value("${tnb.ia.reconciliation.timeout-ms:10000}") int timeoutMs) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        log.info("IaReconciliationClient initialisé avec baseUrl: {}, timeout: {}ms", baseUrl, timeoutMs);
    }

    /**
     * Appelle le service IA pour classifier un message opérateur.
     * Lève une RuntimeException en cas d'échec (timeout, 5xx, réseau) —
     * c'est volontaire : c'est ce qui déclenche le retry Kafka.
     */
    public IaExtractionResult classify(String rawContent) {
        log.debug("Appel IA pour classification du message...");

        try {
            IaExtractionResult result = restClient.post()
                    .uri("/classify")
                    .body(new ClassifyRequest(rawContent))
                    .retrieve()
                    .body(IaExtractionResult.class);

            if (result == null) {
                throw new IaServiceException("Le service IA a retourné une réponse nulle");
            }

            log.debug("Classification IA: {} (confiance={})", result.classification(), result.confidence());
            return result;

        } catch (Exception e) {
            log.error("Échec de l'appel IA: {}", e.getMessage());
            throw new IaServiceException("Service IA injoignable: " + e.getMessage(), e);
        }
    }

    /**
     * DTO de requête vers le service IA.
     */
    private record ClassifyRequest(String rawContent) {}

    /**
     * Exception spécifique pour l'IA — permet à @RetryableTopic de
     * cibler uniquement cette exception si besoin (via include).
     */
    public static class IaServiceException extends RuntimeException {
        public IaServiceException(String message) { super(message); }
        public IaServiceException(String message, Throwable cause) { super(message, cause); }
    }
}