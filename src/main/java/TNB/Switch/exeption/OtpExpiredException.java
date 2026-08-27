package TNB.Switch.exeption;

public class OtpExpiredException extends BusinessException {
    public OtpExpiredException() {
        super("OTP_EXPIRED", "Le code OTP a expiré, veuillez en demander un nouveau");
    }
}