package TNB.Switch.DTO.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCreditOfferRequest(
        String label,
        UUID operateurId,
        BigDecimal price,
        BigDecimal creditAmount,
        BigDecimal offerFeePercentage,  // ✅ Nouveau (défaut 0)
        String executionTemplateContent
) {}