package TNB.Switch.DTO.request;


import TNB.Switch.enums.MessageProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record MessageOperateurBrutSearchRequest(
        MessageProcessingStatus processingStatus,
        UUID deviceId,
        UUID operateurId,
        Instant receivedFrom,
        Instant receivedTo
) {}