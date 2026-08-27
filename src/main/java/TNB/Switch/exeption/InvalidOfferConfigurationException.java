package TNB.Switch.exeption;

public class InvalidOfferConfigurationException extends BusinessException {
    public InvalidOfferConfigurationException(String message) {
        super("INVALID_OFFER_CONFIGURATION", message);
    }
}