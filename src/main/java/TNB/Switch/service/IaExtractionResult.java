package TNB.Switch.service;

import TNB.Switch.enums.IaClassification;

import java.math.BigDecimal;

public record IaExtractionResult(
        IaClassification classification,
        double confidence,
        BigDecimal extractedAmount,
        String extractedPhoneNumber,
        String extractedReference,
        String modelVersion
) {
    public static IaExtractionResult pendingManualReview() {
        return new IaExtractionResult(IaClassification.AMBIGUOUS, 0.0, null, null, null, "unavailable");
    }
}