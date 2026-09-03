package TNB.Switch.service;

import TNB.Switch.enums.IaClassification;

import java.math.BigDecimal;

/**
 * Résultat de l'extraction IA d'un message opérateur.
 */
public record IaExtractionResult(
        IaClassification classification,
        double confidence,
        BigDecimal extractedAmount,
        String extractedPhoneNumber,
        String extractedReference,
        String modelVersion
) {

    /**
     * Retourne un résultat "en attente de reprise manuelle".
     * Utilisé comme fallback en cas d'échec du matching.
     */
    public static IaExtractionResult pendingManualReview() {
        return new IaExtractionResult(
                IaClassification.AMBIGUOUS,
                0.0,
                null,
                null,
                null,
                "unavailable"
        );
    }

    /**
     * Vérifie si le résultat est valide pour une réconciliation automatique.
     */
    public boolean isReconcilable(double confidenceThreshold) {
        return classification != IaClassification.UNRELATED
                && classification != IaClassification.AMBIGUOUS
                && confidence >= confidenceThreshold
                && extractedAmount != null
                && extractedAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}