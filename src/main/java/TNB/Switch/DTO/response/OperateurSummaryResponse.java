package TNB.Switch.DTO.response;

import TNB.Switch.entity.Operateur;

import java.util.UUID;

public record OperateurSummaryResponse(UUID id, String code, String nom) {

    public static OperateurSummaryResponse fromEntity(Operateur operateur) {
        if (operateur == null) {
            return null;
        }
        return new OperateurSummaryResponse(
                operateur.getId(),
                operateur.getCode(),
                operateur.getNom()
        );
    }
}