package TNB.Switch.DTO.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateDataOfferRequest(
        @NotBlank String label,
        @Positive BigDecimal price,
        @Positive int dataVolumeMb,
        @Positive int dataValidityDays,
        BigDecimal offerFeePercentage
        // ❌ SUPPRIMER String executionTemplateContent
) {}