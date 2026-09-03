package TNB.Switch.DTO.request;

/**
 * Confirmation OTP d'une transaction en attente (WAIT_OTP).
 */
public record ConfirmOtpRequest(
        String otpCode
) {
}