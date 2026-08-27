package TNB.Switch.mapper;

import TNB.Switch.DTO.response.PendingCompensationResponse;
import TNB.Switch.DTO.response.UserSummaryResponse;
import TNB.Switch.entity.Transaction;

import java.util.function.Function;

public class PendingCompensationMapper implements Function<Transaction, PendingCompensationResponse> {

    private final UserSummaryMapper userSummaryMapper = new UserSummaryMapper();

    @Override
    public PendingCompensationResponse apply(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        UserSummaryResponse client = userSummaryMapper.apply(transaction.getClient());

        return new PendingCompensationResponse(
                transaction.getId(),
                client,
                transaction.getAmount(),
                0, // attemptsCount à calculer ailleurs
                transaction.getCreatedAt() // lastAttemptAt à calculer ailleurs
        );
    }
}