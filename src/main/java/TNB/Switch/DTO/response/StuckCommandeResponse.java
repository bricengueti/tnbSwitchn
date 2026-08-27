package TNB.Switch.DTO.response;
import java.time.Instant;
import java.util.UUID;

public record StuckCommandeResponse(
        UUID commandeId,
        UUID transactionId,
        OperateurSummaryResponse operateur,
        Instant createdAt
) {}