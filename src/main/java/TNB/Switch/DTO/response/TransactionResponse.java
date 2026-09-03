package TNB.Switch.DTO.response;

import TNB.Switch.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UserSummaryResponse client,
        OfferSummaryResponse offer,
        OperateurSummaryResponse fromOperateur,    // ✅ AJOUT
        OperateurSummaryResponse toOperateur,      // ✅ AJOUT
        BigDecimal amount,
        TransactionStatus status,
        String idempotencyKey,
        String destinationPhoneNumber,
        String payerPhoneNumber,
        Instant createdAt,
        Instant completedAt
) {}