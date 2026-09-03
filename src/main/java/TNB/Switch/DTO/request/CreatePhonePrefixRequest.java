package TNB.Switch.DTO.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePhonePrefixRequest(
        @NotNull(message = "L'ID de l'opérateur est obligatoire")
        UUID operateurId,

        @NotBlank(message = "Le préfixe est obligatoire")
        @Size(min = 1, max = 5, message = "Le préfixe doit contenir entre 1 et 5 caractères")
        @Pattern(regexp = "^[0-9]+$", message = "Le préfixe doit contenir uniquement des chiffres")
        String prefix,

        String description
) {}