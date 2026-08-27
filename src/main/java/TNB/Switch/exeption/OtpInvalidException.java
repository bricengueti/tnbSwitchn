package TNB.Switch.exeption;

public class OtpInvalidException extends BusinessException {
    public OtpInvalidException() {
        super("OTP_INVALID", "Code OTP invalide");
    }
}