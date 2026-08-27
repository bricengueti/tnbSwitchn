package TNB.Switch.DTO.response;


import TNB.Switch.enums.IaClassification;
import TNB.Switch.enums.MatchingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vue de supervision d'un message en reprise manuelle. Distingue
 * explicitement UNMATCHED (zéro candidat) d'AMBIGUOUS (plusieurs
 * candidats) — le point qu'on avait laissé "stocké mais pas exploité"
 * (CDC Annexe 15 pt 20).
 */
public record PendingReconciliationResponse(
        UUID messageId,
        DeviceSummaryResponse device,
        OperateurSummaryResponse operateur,
        String rawContent,
        Instant receivedAt,
        IaClassification iaClassification,
        Double iaConfidence,
        BigDecimal iaExtractedAmount,
        MatchingStatus matchingStatus,
        String reviewHint
) {}