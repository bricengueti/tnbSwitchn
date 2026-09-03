package TNB.Switch.DTO.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * ASSOMPTION : suppose FleetBalance#getId(), #getDevice(), #getOperateur(),
 * #getCommercialNumber(), #getCreditBalance(), #getWalletBalance() —
 * à ajuster si les noms réels diffèrent.
 */
public record FleetBalanceResponse(
        UUID id,
        DeviceSummaryResponse device,
        OperateurSummaryResponse operateur,
        String commercialNumber,
        BigDecimal creditBalance,
        BigDecimal walletBalance
) {
}