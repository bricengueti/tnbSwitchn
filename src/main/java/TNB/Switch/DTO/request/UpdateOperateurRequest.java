package TNB.Switch.DTO.request;

import TNB.Switch.enums.OperateurType;

import java.util.UUID;

public record UpdateOperateurRequest(
        UUID id,
        String nom,
        OperateurType type,
        String withdrawalTemplateContent
) {}