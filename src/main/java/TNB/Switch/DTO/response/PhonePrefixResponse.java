package TNB.Switch.DTO.response;

import TNB.Switch.entity.PhonePrefix;

import java.util.UUID;

public record PhonePrefixResponse(
        UUID id,
        String prefix,
        UUID operateurId,
        String operateurCode,
        String operateurNom,
        String description,
        boolean active
) {
    public static PhonePrefixResponse fromEntity(PhonePrefix phonePrefix) {
        return new PhonePrefixResponse(
                phonePrefix.getId(),
                phonePrefix.getPrefix(),
                phonePrefix.getOperateur().getId(),
                phonePrefix.getOperateur().getCode(),
                phonePrefix.getOperateur().getNom(),
                phonePrefix.getDescription(),
                phonePrefix.isActive()
        );
    }
}