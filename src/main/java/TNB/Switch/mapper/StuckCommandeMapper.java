package TNB.Switch.mapper;


import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.DTO.response.StuckCommandeResponse;
import TNB.Switch.entity.Commande;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
public class StuckCommandeMapper implements Function<Commande, StuckCommandeResponse> {

    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();

    @Override
    public StuckCommandeResponse apply(Commande commande) {
        if (commande == null) {
            return null;
        }

        OperateurSummaryResponse operateur = operateurSummaryMapper.apply(commande.getOperateur());

        return new StuckCommandeResponse(
                commande.getId(),
                commande.getTransaction().getId(),
                operateur,
                commande.getCreatedAt()
        );
    }
}