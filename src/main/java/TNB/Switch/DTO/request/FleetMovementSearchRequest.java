package TNB.Switch.DTO.request;
import java.util.UUID;

public record FleetMovementSearchRequest(
        UUID fleetBalanceId,
        UUID transactionId
) {}
