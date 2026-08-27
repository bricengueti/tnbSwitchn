package TNB.Switch.DTO.response;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PendingCompensationResponse(
        UUID transactionId,
        UserSummaryResponse client,
        BigDecimal amount,
        int attemptsCount,
        Instant lastAttemptAt
) {}