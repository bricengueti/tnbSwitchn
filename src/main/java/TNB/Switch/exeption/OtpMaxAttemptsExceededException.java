package TNB.Switch.exeption;

public class OtpMaxAttemptsExceededException extends BusinessException {
    public OtpMaxAttemptsExceededException() {
        super("OTP_MAX_ATTEMPTS_EXCEEDED", "Nombre maximal de tentatives atteint pour ce code OTP");
    }
}