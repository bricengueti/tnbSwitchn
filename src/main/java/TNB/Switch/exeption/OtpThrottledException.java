package TNB.Switch.exeption;


public class OtpThrottledException extends BusinessException {
    public OtpThrottledException(long secondsRemaining) {
        super("OTP_THROTTLED",
                "Veuillez patienter %d secondes avant de redemander un code".formatted(secondsRemaining));
    }
}