package TNB.Switch.DTO.request;

import java.math.BigDecimal;

/**
 * Ajustement manuel admin d'un solde flotte (CDC §7.7).
 * signedAmount positif = crédit, négatif = débit.
 */
public record ManualAdjustmentRequest(
        BigDecimal signedAmount,
        String justification
) {
}