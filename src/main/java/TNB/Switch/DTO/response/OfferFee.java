package TNB.Switch.DTO.response;

import java.math.BigDecimal;

public record OfferFee(
        BigDecimal percentage,
        BigDecimal calculatedAmount
) {}