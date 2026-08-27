package TNB.Switch.exeption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        log.warn("Ressource introuvable : {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getErrorCode(), e.getMessage(), null);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException e) {
        log.info("Conflit d'idempotence : {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e.getErrorCode(), e.getMessage(), null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        // Niveau WARN, jamais ERROR : une règle métier violée n'est pas un
        // dysfonctionnement du système — évite de polluer les alertes ELK
        // configurées sur le niveau ERROR.
        log.warn("Règle métier violée [{}] : {}", e.getErrorCode(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getErrorCode(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> details = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(err ->
                details.put(err.getField(), err.getDefaultMessage())
        );
        log.warn("Erreur de validation : {}", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Requête invalide", details);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("Accès refusé : {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accès refusé", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        // Ne JAMAIS exposer le message brut de la contrainte SQL au client
        // (fuite de noms de colonnes/contraintes internes) — logué en
        // interne uniquement, avec la stack complète pour investigation.
        log.error("Violation d'intégrité en base", e);
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "La requête entre en conflit avec une donnée existante", null);
    }

    // Filet de sécurité final : toute exception non prévue explicitement.
    // Niveau ERROR volontaire — c'est ce niveau qui doit déclencher une
    // alerte côté ELK/monitoring, contrairement aux exceptions métier.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Erreur inattendue non gérée", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur inattendue est survenue", null);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String errorCode, String message, Map<String, String> details) {
        String traceId = MDC.get("traceId");
        ErrorResponse body = new ErrorResponse(errorCode, message, Instant.now(), traceId, details);
        return ResponseEntity.status(status).body(body);
    }
}