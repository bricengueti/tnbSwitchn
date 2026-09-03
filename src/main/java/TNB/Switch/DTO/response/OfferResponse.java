package TNB.Switch.DTO.response;

import TNB.Switch.enums.OfferType;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        OfferType type,
        String label,
        BigDecimal price,
        BigDecimal creditAmount,
        Integer dataVolumeMb,
        Integer dataValidityDays,
        BigDecimal exchangeRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal offerFeePercentage,
        boolean active
        // ❌ Plus de sourceOperatorId, destinationOperatorId
) {}