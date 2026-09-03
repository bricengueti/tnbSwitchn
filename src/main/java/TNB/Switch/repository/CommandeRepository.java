package TNB.Switch.repository;

import TNB.Switch.entity.*;
import TNB.Switch.enums.CommandPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import TNB.Switch.entity.Transaction;
import TNB.Switch.enums.CommandPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour les commandes (retrait et exécution).
 *
 * =====================================================================
 *                    COMMANDE REPOSITORY
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  RECHERCHES DE BASE                                                │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  findByTransaction(transaction) → Toutes les commandes d'une tx   │
 *  │  findByTransactionAndPhase(transaction, phase) → Commande spec.   │
 *  │  findByPhaseAndDeviceIsNull(phase) → Commandes non routées        │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  RECHERCHES POUR ROUTAGE                                           │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  findUnroutedByPhase(phase) → Commandes en file d'attente         │
 *  │  findStaleUnrouted(phase, threshold) → Commandes bloquées         │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  RECHERCHES POUR RÉCONCILIATION                                    │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  findCandidatesForReconciliation(device, operateur, phase, ...)   │
 *  │  → Recherche des commandes candidates (utilisé par MessageRepo)   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 */
public interface CommandeRepository
        extends JpaRepository<Commande, UUID>, JpaSpecificationExecutor<Commande> {

    // ==================== RECHERCHES DE BASE ====================

    /**
     * Récupère toutes les commandes d'une transaction.
     */
    List<Commande> findByTransaction(Transaction transaction);

    /**
     * Récupère la commande d'une transaction pour une phase donnée.
     */
    Optional<Commande> findByTransactionAndPhase(Transaction transaction, CommandPhase phase);

    /**
     * Récupère les commandes non routées (sans device) pour une phase donnée.
     * Utilisé par AdminSupervisionService pour la file de supervision.
     */
    List<Commande> findByPhaseAndDeviceIsNull(CommandPhase phase);

    // ==================== RECHERCHES POUR ROUTAGE ====================

    /**
     * Récupère les commandes non routées pour une phase donnée, triées par date de création.
     * Utilisé pour le traitement FIFO.
     */
    List<Commande> findByPhaseAndDeviceIsNullOrderByCreatedAtAsc(CommandPhase phase);

    /**
     * Récupère les commandes non routées créées avant une date donnée.
     * Utilisé par AdminSupervisionService pour identifier les commandes bloquées.
     */
    @Query("""
        SELECT c FROM Commande c
        WHERE c.device IS NULL
        AND c.phase = :phase
        AND c.createdAt < :threshold
        ORDER BY c.createdAt ASC
        """)
    List<Commande> findUnroutedCreatedBefore(
            @Param("phase") CommandPhase phase,
            @Param("threshold") Instant threshold
    );

    // ==================== RECHERCHES POUR RÉCONCILIATION ====================

    /**
     * Recherche les commandes candidates pour la réconciliation.
     * ⚠️ Utilisée par MessageOperateurBrutRepository.findMatchingCandidates().
     *
     * Critères :
     * - Même device
     * - Même opérateur
     * - Phase spécifiée (généralement WITHDRAWAL)
     * - Même montant
     * - Créée dans la fenêtre temporelle
     * - Non déjà matchée avec un autre message
     */
    @Query("""
        SELECT c FROM Commande c
        JOIN c.transaction t
        WHERE c.device = :device
        AND c.operateur = :operateur
        AND c.phase = :phase
        AND t.amount = :amount
        AND c.createdAt >= :windowStart
        AND c.id NOT IN (
            SELECT m.matchedCommande.id FROM MessageOperateurBrut m
            WHERE m.matchedCommande IS NOT NULL
        )
        ORDER BY c.createdAt ASC
        """)
    List<Commande> findCandidatesForReconciliation(
            @Param("device") Device device,
            @Param("operateur") Operateur operateur,
            @Param("phase") CommandPhase phase,
            @Param("amount") java.math.BigDecimal amount,
            @Param("windowStart") Instant windowStart
    );

    // ==================== STATISTIQUES / MONITORING ====================

    /**
     * Compte les commandes par phase et statut de routage.
     * Utilisé pour le monitoring admin.
     */
    long countByPhaseAndDeviceIsNull(CommandPhase phase);

    /**
     * Compte les commandes d'une transaction par phase.
     */
    long countByTransactionAndPhase(Transaction transaction, CommandPhase phase);
}