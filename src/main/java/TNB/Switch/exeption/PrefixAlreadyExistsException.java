package TNB.Switch.exeption;

public class PrefixAlreadyExistsException extends RuntimeException {
    public PrefixAlreadyExistsException(String message) {
        super(message);
    }
}