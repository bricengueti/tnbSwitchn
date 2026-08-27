package TNB.Switch.specification;

import TNB.Switch.entity.Transaction;
import TNB.Switch.enums.TransactionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtres composables pour la recherche admin de transactions.
 * Chaque méthode retourne null si le critère n'est pas fourni — Spring Data
 * ignore alors ce filtre (pattern standard des Specifications combinables).
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> hasUser(UUID userId) {
        return (root, query, cb) ->
                userId == null ? null : cb.equal(root.get("client").get("id"), userId);  // ✅ client
    }

    public static Specification<Transaction> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from == null) return cb.lessThanOrEqualTo(root.get("createdAt"), to);
            if (to == null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            return cb.between(root.get("createdAt"), from, to);
        };
    }

    public static Specification<Transaction> amountAtLeast(BigDecimal min) {
        return (root, query, cb) ->
                min == null ? null : cb.greaterThanOrEqualTo(root.get("amount"), min);
    }
}