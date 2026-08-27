package TNB.Switch.DTO.request;

import TNB.Switch.enums.AuditedEntityType;

import java.util.UUID;

public record HistoriqueStatutSearchRequest(
        AuditedEntityType entityType,
        UUID entityId
) {}