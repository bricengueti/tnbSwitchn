package TNB.Switch.DTO.request;

import TNB.Switch.enums.CommandPhase;

import java.util.UUID;

public record CommandeSearchRequest(
        UUID transactionId,
        CommandPhase phase,
        UUID deviceId
) {}