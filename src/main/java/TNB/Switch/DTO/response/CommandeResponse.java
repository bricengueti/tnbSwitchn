package TNB.Switch.DTO.response;

import TNB.Switch.enums.CommandPhase;

import java.time.Instant;
import java.util.UUID;

public record CommandeResponse(
        UUID id,
        UUID transactionId,
        CommandPhase phase,
        DeviceSummaryResponse device,
        OperateurSummaryResponse operateur,
        String resolvedContent,
        Instant createdAt
) {}