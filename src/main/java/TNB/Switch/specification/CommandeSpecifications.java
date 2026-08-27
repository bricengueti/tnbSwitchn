package TNB.Switch.specification;

import TNB.Switch.entity.Commande;
import TNB.Switch.enums.CommandPhase;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class CommandeSpecifications {

    private CommandeSpecifications() {}

    public static Specification<Commande> hasNoDevice() {
        return (root, query, cb) -> cb.isNull(root.get("device"));
    }

    public static Specification<Commande> hasPhase(CommandPhase phase) {
        return (root, query, cb) ->
                phase == null ? null : cb.equal(root.get("phase"), phase);
    }

    public static Specification<Commande> hasTransaction(UUID transactionId) {
        return (root, query, cb) ->
                transactionId == null ? null : cb.equal(root.get("transaction").get("id"), transactionId);
    }

    public static Specification<Commande> createdBefore(Instant threshold) {
        return (root, query, cb) ->
                threshold == null ? null : cb.lessThan(root.get("createdAt"), threshold);
    }
}