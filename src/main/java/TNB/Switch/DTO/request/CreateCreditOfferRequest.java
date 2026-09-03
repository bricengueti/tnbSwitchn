package TNB.Switch.DTO.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateCreditOfferRequest(
        @NotBlank String label,
        @Positive BigDecimal price,
        @Positive BigDecimal creditAmount,
        BigDecimal offerFeePercentage
        // ❌ SUPPRIMER String executionTemplateContent
) {}