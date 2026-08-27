package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OperateurResponse;
import TNB.Switch.entity.Operateur;

import java.util.function.Function;

public class OperateurMapper implements Function<Operateur, OperateurResponse> {

    @Override
    public OperateurResponse apply(Operateur operateur) {
        if (operateur == null) {
            return null;
        }

        String withdrawalTemplateContent = operateur.getWithdrawalCommandTemplate() != null
                ? operateur.getWithdrawalCommandTemplate().getContent()
                : null;

        return new OperateurResponse(
                operateur.getId(),
                operateur.getCode(),
                operateur.getNom(),
                operateur.getType(),
                operateur.isActif(),
                withdrawalTemplateContent
        );
    }
}