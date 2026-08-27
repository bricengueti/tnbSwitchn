package TNB.Switch.DTO.response;

import TNB.Switch.enums.FleetMovementReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FleetMovementResponse(
        UUID id,
        UUID fleetBalanceId,
        BigDecimal amount,
        FleetMovementReason reason,
        String justification,
        UUID transactionId,
        Instant createdAt
) {}