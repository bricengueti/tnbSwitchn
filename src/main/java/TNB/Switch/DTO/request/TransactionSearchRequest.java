package TNB.Switch.DTO.request;

import TNB.Switch.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtres de recherche admin — chaque champ est optionnel, null = filtre
 * ignoré (cohérent avec les Specifications combinables).
 */
public record TransactionSearchRequest(
        TransactionStatus status,
        UUID userId,
        Instant createdFrom,
        Instant createdTo,
        BigDecimal minAmount
) {}