package TNB.Switch.mapper;

import TNB.Switch.DTO.response.DeviceSummaryResponse;
import TNB.Switch.DTO.response.MessageOperateurBrutResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.MessageOperateurBrut;

import java.util.function.Function;

public class MessageOperateurBrutMapper implements Function<MessageOperateurBrut, MessageOperateurBrutResponse> {

    private final DeviceSummaryMapper deviceSummaryMapper = new DeviceSummaryMapper();
    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();

    @Override
    public MessageOperateurBrutResponse apply(MessageOperateurBrut message) {
        if (message == null) {
            return null;
        }

        DeviceSummaryResponse device = deviceSummaryMapper.apply(message.getDevice());
        OperateurSummaryResponse operateur = operateurSummaryMapper.apply(message.getOperateur());

        return new MessageOperateurBrutResponse(
                message.getId(),
                device,
                operateur,
                message.getRawContent(),
                message.getReceivedAt(),
                message.getProcessingStatus(),
                message.getIaClassification(),
                message.getIaConfidence(),
                message.getIaExtractedAmount(),
                message.getIaExtractedPhoneNumber(),
                message.getIaExtractedReference(),
                message.getIaModelVersion(),
                message.getIaRetryCount(),
                message.getMatchingStatus(),
                message.getMatchedCommande() != null ? message.getMatchedCommande().getId() : null
        );
    }
}