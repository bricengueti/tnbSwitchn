package TNB.Switch.exeption;

public class InvalidOtpException extends BusinessException {
    public InvalidOtpException() {
        super("INVALID_OTP", "Code OTP incorrect");
    }
}