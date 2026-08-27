package TNB.Switch.DTO.response;

import TNB.Switch.enums.OperateurType;

import java.util.UUID;

public record OperateurResponse(
        UUID id,
        String code,
        String nom,
        OperateurType type,
        boolean actif,
        String withdrawalTemplateContent   // ✅ Ajouté
) {}