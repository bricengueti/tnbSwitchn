package TNB.Switch.mapper;

import TNB.Switch.DTO.response.OfferSummaryResponse;
import TNB.Switch.DTO.response.TransactionResponse;
import TNB.Switch.DTO.response.UserSummaryResponse;
import TNB.Switch.entity.Transaction;

import java.util.function.Function;

public class TransactionMapper implements Function<Transaction, TransactionResponse> {

    private final UserSummaryMapper userSummaryMapper = new UserSummaryMapper();
    private final OfferSummaryMapper offerSummaryMapper = new OfferSummaryMapper();

    @Override
    public TransactionResponse apply(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        UserSummaryResponse client = userSummaryMapper.apply(transaction.getClient());
        OfferSummaryResponse offer = offerSummaryMapper.apply(transaction.getOffer());

        return new TransactionResponse(
                transaction.getId(),
                client,
                offer,
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