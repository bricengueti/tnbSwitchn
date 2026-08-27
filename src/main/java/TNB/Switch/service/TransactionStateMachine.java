package TNB.Switch.service;


import TNB.Switch.enums.TransactionStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Table des transitions valides de la state machine Transaction (CDC §9.4
 * + règle de compensation actée en session). Centralisée ici plutôt que
 * dans TransactionStatus — cohérent avec la convention "entités = champs,
 * logique en service".
 */
final class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> TRANSITIONS = new EnumMap<>(TransactionStatus.class);

    static {
        TRANSITIONS.put(TransactionStatus.WAIT_OTP, EnumSet.of(
                TransactionStatus.QUEUE_WITHDRAWAL, TransactionStatus.CANCELLED));
        TRANSITIONS.put(TransactionStatus.QUEUE_WITHDRAWAL, EnumSet.of(
                TransactionStatus.ASK_WITHDRAWAL, TransactionStatus.CANCELLED));
        TRANSITIONS.put(TransactionStatus.ASK_WITHDRAWAL, EnumSet.of(
                TransactionStatus.WITHDRAWAL_DONE, TransactionStatus.WITHDRAWAL_FAILED));
        TRANSITIONS.put(TransactionStatus.WITHDRAWAL_DONE, EnumSet.of(
                TransactionStatus.QUEUE_EXECUTE_COMMAND));
        TRANSITIONS.put(TransactionStatus.QUEUE_EXECUTE_COMMAND, EnumSet.of(
                TransactionStatus.ROUTE_EXECUTE_COMMAND));
        TRANSITIONS.put(TransactionStatus.ROUTE_EXECUTE_COMMAND, EnumSet.of(
                TransactionStatus.EXECUTE_COMMAND_DONE, TransactionStatus.EXECUTE_COMMAND_FAILED));
        TRANSITIONS.put(TransactionStatus.EXECUTE_COMMAND_FAILED, EnumSet.of(
                TransactionStatus.COMPENSATION_IN_PROGRESS));
        TRANSITIONS.put(TransactionStatus.COMPENSATION_IN_PROGRESS, EnumSet.of(
                TransactionStatus.QUEUE_EXECUTE_COMMAND, // nouvelle tentative
                TransactionStatus.COMPENSATION_MANUAL_REVIEW));
        // États terminaux : aucune transition sortante.
        TRANSITIONS.put(TransactionStatus.WITHDRAWAL_FAILED, EnumSet.noneOf(TransactionStatus.class));
        TRANSITIONS.put(TransactionStatus.EXECUTE_COMMAND_DONE, EnumSet.noneOf(TransactionStatus.class));
        TRANSITIONS.put(TransactionStatus.COMPENSATION_MANUAL_REVIEW, EnumSet.noneOf(TransactionStatus.class));
        TRANSITIONS.put(TransactionStatus.CANCELLED, EnumSet.noneOf(TransactionStatus.class));
    }

    private TransactionStateMachine() {}

    static boolean canTransition(TransactionStatus from, TransactionStatus to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TransactionStatus.class)).contains(to);
    }
}
