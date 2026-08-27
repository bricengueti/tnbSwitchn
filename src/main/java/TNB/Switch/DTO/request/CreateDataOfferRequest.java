package TNB.Switch.DTO.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDataOfferRequest(
        String label,
        UUID operateurId,
        BigDecimal price,
        int dataVolumeMb,
        int dataValidityDays,
        BigDecimal offerFeePercentage,  // ✅ Nouveau (défaut 0)
        String executionTemplateContent
) {}