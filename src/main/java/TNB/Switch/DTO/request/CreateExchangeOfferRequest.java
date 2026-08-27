package TNB.Switch.DTO.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateExchangeOfferRequest(
        String label,
        UUID sourceOperateurId,
        UUID destinationOperateurId,
        // ❌ exchangeFee supprimé
        BigDecimal exchangeRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal offerFeePercentage,  // ✅ Nouveau (défaut 0)
        String executionTemplateContent
) {}