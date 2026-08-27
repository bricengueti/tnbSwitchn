package TNB.Switch.exeption;


/** Conflit d'idempotence — HTTP 409, action déjà en cours/traitée. */
public class IdempotencyConflictException extends TnbException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("IDEMPOTENCY_CONFLICT",
                "Action déjà traitée ou en cours pour la clé d'idempotence : %s".formatted(idempotencyKey));
    }
}