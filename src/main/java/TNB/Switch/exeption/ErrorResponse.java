package TNB.Switch.exeption;

import java.time.Instant;
import java.util.Map;

/**
 * Format d'erreur unique renvoyé par toute l'API. traceId permet de
 * corréler une erreur vue côté client avec la ligne de log correspondante
 * dans ELK, sans exposer la stack trace au client.
 */
public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String traceId,
        Map<String, String> details
) {}