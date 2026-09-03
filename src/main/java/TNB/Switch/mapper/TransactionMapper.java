package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OfferSummaryResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.DTO.response.TransactionResponse;
import TNB.Switch.DTO.response.UserSummaryResponse;
import TNB.Switch.entity.Transaction;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class TransactionMapper implements Function<Transaction, TransactionResponse> {

    private final UserSummaryMapper userSummaryMapper = new UserSummaryMapper();
    private final OfferSummaryMapper offerSummaryMapper = new OfferSummaryMapper();
    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();  // ✅ AJOUT

    @Override
    public TransactionResponse apply(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        UserSummaryResponse client = userSummaryMapper.apply(transaction.getClient());
        OfferSummaryResponse offer = offerSummaryMapper.apply(transaction.getOffer());

        // ✅ Mapping des opérateurs
        OperateurSummaryResponse fromOperateur = operateurSummaryMapper.apply(transaction.getFromOperateur());
        OperateurSummaryResponse toOperateur = operateurSummaryMapper.apply(transaction.getToOperateur());

        return new TransactionResponse(
                transaction.getId(),
                client,
                offer,
                fromOperateur,      // ✅ AJOUT
                toOperateur,        // ✅ AJOUT
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getIdempotencyKey(),
                transaction.getDestinationPhoneNumber(),
                transaction.getPayerPhoneNumber(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }
}