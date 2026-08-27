package TNB.Switch.DTO.response;


import TNB.Switch.enums.IaClassification;
import TNB.Switch.enums.MatchingStatus;
import TNB.Switch.enums.MessageProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MessageOperateurBrutResponse(
        UUID id,
        DeviceSummaryResponse device,
        OperateurSummaryResponse operateur,
        String rawContent,
        Instant receivedAt,
        MessageProcessingStatus processingStatus,
        IaClassification iaClassification,
        Double iaConfidence,
        BigDecimal iaExtractedAmount,
        String iaExtractedPhoneNumber,
        String iaExtractedReference,
        String iaModelVersion,
        int iaRetryCount,
        MatchingStatus matchingStatus,
        UUID matchedCommandeId
) {}