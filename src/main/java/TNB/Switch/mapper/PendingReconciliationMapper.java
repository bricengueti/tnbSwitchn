package TNB.Switch.mapper;


import TNB.Switch.DTO.response.DeviceSummaryResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.DTO.response.PendingReconciliationResponse;
import TNB.Switch.entity.MessageOperateurBrut;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
public class PendingReconciliationMapper implements Function<MessageOperateurBrut, PendingReconciliationResponse> {

    private final DeviceSummaryMapper deviceSummaryMapper = new DeviceSummaryMapper();
    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();

    @Override
    public PendingReconciliationResponse apply(MessageOperateurBrut message) {
        if (message == null) {
            return null;
        }

        DeviceSummaryResponse device = deviceSummaryMapper.apply(message.getDevice());
        OperateurSummaryResponse operateur = operateurSummaryMapper.apply(message.getOperateur());

        String reviewHint = switch (message.getMatchingStatus()) {
            case UNMATCHED -> "Aucune commande candidate trouvée - vérifier le montant ou la période";
            case AMBIGUOUS -> "Plusieurs commandes candidates - sélectionner manuellement";
            default -> "À examiner";
        };

        return new PendingReconciliationResponse(
                message.getId(),
                device,
                operateur,
                message.getRawContent(),
                message.getReceivedAt(),
                message.getIaClassification(),
                message.getIaConfidence(),
                message.getIaExtractedAmount(),
                message.getMatchingStatus(),
                reviewHint
        );
    }
}