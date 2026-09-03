package TNB.Switch.DTO.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePhonePrefixRequest(
        @Size(min = 1, max = 5, message = "Le préfixe doit contenir entre 1 et 5 caractères")
        @Pattern(regexp = "^[0-9]+$", message = "Le préfixe doit contenir uniquement des chiffres")
        String prefix,

        String description,

        Boolean active
) {}