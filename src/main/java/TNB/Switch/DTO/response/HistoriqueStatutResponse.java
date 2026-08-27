package TNB.Switch.DTO.response;


import TNB.Switch.enums.AuditedEntityType;

import java.time.Instant;
import java.util.UUID;

public record HistoriqueStatutResponse(
        UUID id,
        AuditedEntityType entityType,
        UUID entityId,
        String status,
        UUID actorId,
        Instant createdAt
) {}