package TNB.Switch.DTO.request;

import TNB.Switch.enums.OperateurType;
import TNB.Switch.enums.OfferType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateOperateurRequest(
        @NotBlank(message = "Le code est obligatoire")
        @Size(max = 20, message = "Le code ne doit pas dépasser 20 caractères")
        String code,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String nom,

        @NotNull(message = "Le type est obligatoire")
        OperateurType type,

        List<String> phonePrefixes,

        @NotBlank(message = "Le template de retrait est obligatoire")
        String withdrawalTemplateContent,

        @NotNull(message = "Les templates d'exécution sont obligatoires")
        Map<OfferType, String> executionTemplatesContent
) {}