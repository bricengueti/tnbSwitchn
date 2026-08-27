package TNB.Switch.DTO.response;

import TNB.Switch.enums.OfferType;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferResponse(
        UUID id,
        OfferType type,
        String label,
        OperateurSummaryResponse sourceOperator,
        OperateurSummaryResponse destinationOperator,
        BigDecimal price,
        BigDecimal creditAmount,
        Integer dataVolumeMb,
        Integer dataValidityDays,
        BigDecimal exchangeRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        OfferFee offerFee,
        String withdrawalTemplateContent,   // ✅ Template de retrait (depuis l'opérateur)
        String executionTemplateContent,     // ✅ Template d'exécution (spécifique à l'offre)
        boolean active
) {}