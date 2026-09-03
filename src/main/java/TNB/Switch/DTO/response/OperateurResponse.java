package TNB.Switch.DTO.response;

import TNB.Switch.enums.OperateurType;
import TNB.Switch.enums.OfferType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OperateurResponse(
        UUID id,
        String code,
        String nom,
        OperateurType type,
        boolean actif,
        List<String> phonePrefixes,
        String withdrawalTemplateContent,
        Map<OfferType, String> executionTemplatesContent  // ✅ Map<OfferType, String>
) {}