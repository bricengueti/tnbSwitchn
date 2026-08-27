package TNB.Switch.specification;

import TNB.Switch.entity.MessageOperateurBrut;
import TNB.Switch.enums.MessageProcessingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class MessageOperateurBrutSpecifications {

    private MessageOperateurBrutSpecifications() {}

    public static Specification<MessageOperateurBrut> hasProcessingStatus(MessageProcessingStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("processingStatus"), status);
    }

    public static Specification<MessageOperateurBrut> hasDevice(UUID deviceId) {
        return (root, query, cb) ->
                deviceId == null ? null : cb.equal(root.get("device").get("id"), deviceId);
    }

    public static Specification<MessageOperateurBrut> hasOperateur(UUID operateurId) {
        return (root, query, cb) ->
                operateurId == null ? null : cb.equal(root.get("operateur").get("id"), operateurId);
    }

    public static Specification<MessageOperateurBrut> receivedBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from == null) return cb.lessThanOrEqualTo(root.get("receivedAt"), to);
            if (to == null) return cb.greaterThanOrEqualTo(root.get("receivedAt"), from);
            return cb.between(root.get("receivedAt"), from, to);
        };
    }
}