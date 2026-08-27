package TNB.Switch.exeption;
/** Violation d'une règle métier — correspond à un HTTP 400/409 selon le cas. */
public class BusinessException extends TnbException {
    public BusinessException(String errorCode, String message) {
        super(errorCode, message);
    }
}