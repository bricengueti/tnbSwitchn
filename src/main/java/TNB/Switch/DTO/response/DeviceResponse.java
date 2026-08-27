package TNB.Switch.DTO.response;


import TNB.Switch.enums.DeviceStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String name,
        String pairingCode,
        DeviceStatus status,
        List<OperateurSummaryResponse> supportedOperators,
        Instant lastHeartbeat
) {}