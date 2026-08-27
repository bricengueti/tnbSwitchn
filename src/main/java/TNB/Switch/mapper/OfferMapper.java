package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OfferFee;
import TNB.Switch.DTO.response.OfferResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.Offer;

import java.math.BigDecimal;
import java.util.function.Function;

public class OfferMapper implements Function<Offer, OfferResponse> {

    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();

    @Override
    public OfferResponse apply(Offer offer) {
        if (offer == null) {
            return null;
        }

        OperateurSummaryResponse sourceOperator = operateurSummaryMapper.apply(offer.getSourceOperator());
        OperateurSummaryResponse destinationOperator = offer.getDestinationOperator() != null
                ? operateurSummaryMapper.apply(offer.getDestinationOperator())
                : null;

        // Récupérer le template de retrait depuis l'opérateur source
        String withdrawalTemplateContent = offer.getSourceOperator() != null
                && offer.getSourceOperator().getWithdrawalCommandTemplate() != null
                ? offer.getSourceOperator().getWithdrawalCommandTemplate().getContent()
                : null;

        // Récupérer le template d'exécution depuis l'offre
        String executionTemplateContent = offer.getExecutionCommandTemplate() != null
                ? offer.getExecutionCommandTemplate().getContent()
                : null;

        // Calculer le montant des frais
        BigDecimal feeAmount = offer.calculateFee(offer.getPrice());
        OfferFee offerFee = new OfferFee(offer.getOfferFeePercentage(), feeAmount);

        return new OfferResponse(
                offer.getId(),
                offer.getType(),
                offer.getLabel(),
                sourceOperator,
                destinationOperator,
                offer.getPrice(),
                offer.getCreditAmount(),
                offer.getDataVolumeMb(),
                offer.getDataValidityDays(),
                offer.getExchangeRate(),
                offer.getMinAmount(),
                offer.getMaxAmount(),
                offerFee,
                withdrawalTemplateContent,
                executionTemplateContent,
                offer.isActive()
        );
    }
}