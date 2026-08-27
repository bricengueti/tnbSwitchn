package TNB.Switch.DTO.request;

import TNB.Switch.enums.OperateurType;

public record CreateOperateurRequest(
        String code,
        String nom,
        OperateurType type,
        String withdrawalTemplateContent
) {}