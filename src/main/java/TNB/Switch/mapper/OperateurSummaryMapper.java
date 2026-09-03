package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.Operateur;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
public class OperateurSummaryMapper implements Function<Operateur, OperateurSummaryResponse> {

    @Override
    public OperateurSummaryResponse apply(Operateur operateur) {
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