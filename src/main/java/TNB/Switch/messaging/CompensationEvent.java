package TNB.Switch.messaging;


import java.math.BigDecimal;
import java.util.UUID;

/**
 * Publié quand une EXÉCUTION échoue après un RETRAIT réussi (CDC §8.4 —
 * cas critique : argent débité côté source, jamais livré côté destination).
 * Le consumer (pas encore écrit) devra décider : retry sur un device
 * alternatif du même opérateur, ou bascule en reprise manuelle admin
 * après épuisement des tentatives (Annexe 15 pt 2/3, jamais tranché
 * avec le client à ce stade).
 */
/**
 * isManualRetry distingue une republication automatique (depuis
 * TransactionService.handleExecutionResult, comptée dans les 3 tentatives
 * max) d'une relance manuelle admin après épuisement (AdminSupervisionService,
 * qui ne doit PAS être bloquée par la limite automatique).
 */
public record CompensationEvent(
        UUID transactionId,
        UUID failedCommandeId,
        BigDecimal amount,
        boolean isManualRetry
) {}