package TNB.Switch.mapper;

import TNB.Switch.DTO.response.CommandeResponse;
import TNB.Switch.DTO.response.DeviceSummaryResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.Commande;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
public class CommandeMapper implements Function<Commande, CommandeResponse> {

    private final DeviceSummaryMapper deviceSummaryMapper = new DeviceSummaryMapper();
    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();

    @Override
    public CommandeResponse apply(Commande commande) {
        if (commande == null) {
            return null;
        }

        DeviceSummaryResponse device = commande.getDevice() != null
                ? deviceSummaryMapper.apply(commande.getDevice())
                : null;

        OperateurSummaryResponse operateur = commande.getOperateur() != null
                ? operateurSummaryMapper.apply(commande.getOperateur())
                : null;

        return new CommandeResponse(
                commande.getId(),
                commande.getTransaction().getId(),
                commande.getPhase(),
                device,
                operateur,
                commande.getResolvedContent(),
                commande.getCreatedAt()
        );
    }
}