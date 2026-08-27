package TNB.Switch.exeption;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFleetBalanceException extends RuntimeException {

    public InsufficientFleetBalanceException(
            UUID fleetBalanceId, String balanceType, BigDecimal available, BigDecimal requested) {
        super("Solde %s insuffisant sur la flotte [%s] : disponible=%s, demandé=%s"
                .formatted(balanceType, fleetBalanceId, available, requested));
    }
}