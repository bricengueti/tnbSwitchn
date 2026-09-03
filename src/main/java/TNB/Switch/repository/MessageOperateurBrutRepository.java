package TNB.Switch.repository;

import TNB.Switch.entity.Commande;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.MessageOperateurBrut;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.CommandPhase;
import TNB.Switch.enums.MessageProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository pour les messages opérateurs bruts.
 *
 * =====================================================================
 *                    MESSAGE OPERATEUR BRUT REPOSITORY
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │                    MESSAGE OPERATEUR BRUT                          │
 *  │                                                                     │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  receiveRawMessage()                                        │   │
 *  │  │  → MessageOperateurBrut créé avec status PENDING_AI         │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  findByProcessingStatus(PENDING_AI)                         │   │
 *  │  │  → Messages en attente de traitement IA                    │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  IA → CLASSIFIED                                            │   │
 *  │  │  → applyDeterministicValidation()                           │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  findMatchingCandidates(device, operateur, WITHDRAWAL, ...)  │   │
 *  │  │  → Recherche des commandes WITHDRAWAL candidates            │   │
 *  │  │  ⚠️ UNIQUEMENT WITHDRAWAL (réconciliation critique)         │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                              │                                      │
 *  │                              ▼                                      │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  1 candidat → RECONCILED                                    │   │
 *  │  │  0 ou >1 candidat → AMBIGUOUS (admin)                      │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 *  LÉGENDE :
 *    ───  = Flux normal (WITHDRAWAL)
 *    - - - = Flux d'erreur / reprise manuelle
 *    ••••  = EXECUTION → pas de réconciliation automatique
 * =====================================================================
 */
public interface MessageOperateurBrutRepository
        extends JpaRepository<MessageOperateurBrut, UUID>,
        JpaSpecificationExecutor<MessageOperateurBrut> {

    // ==================== RECHERCHES DE BASE ====================

    /**
     * Recherche les messages par statut de traitement.
     * Utilisé pour le monitoring/supervision.
     */
    List<MessageOperateurBrut> findByProcessingStatus(MessageProcessingStatus status);

    /**
     * Recherche les messages par statut, triés par date de réception (plus ancien d'abord).
     * Utilisé pour le traitement FIFO.
     */
    List<MessageOperateurBrut> findByProcessingStatusOrderByReceivedAtAsc(
            MessageProcessingStatus status
    );

    // ==================== RECHERCHE DE CANDIDATS (WITHDRAWAL) ====================

    /**
     * Recherche les commandes candidates pour la réconciliation.
     * ⚠️ UNIQUEMENT pour la phase spécifiée (généralement WITHDRAWAL).
     *
     * Critères de matching :
     * - Même device
     * - Même opérateur
     * - Phase WITHDRAWAL
     * - Même montant
     * - Créée dans la fenêtre temporelle (ex: 5 minutes)
     * - Non déjà matchée avec un autre message
     *
     * Tri : par date de création ASC (FIFO)
     *
     * ┌─────────────────────────────────────────────────────────────────┐
     * │  findMatchingCandidates(device, operateur, WITHDRAWAL, ...)   │
     * │                                                                 │
     * │  SELECT c FROM Commande c                                     │
     * │  JOIN c.transaction t                                         │
     * │  WHERE c.device = :device                                    │
     * │    AND c.operateur = :operateur                              │
     * │    AND c.phase = :phase           ← ⚠️ UNIQUEMENT WITHDRAWAL │
     * │    AND t.amount = :amount                                    │
     * │    AND c.createdAt >= :windowStart                           │
     * │    AND c.id NOT IN (                                         │
     * │        SELECT m.matchedCommande.id FROM MessageOperateurBrut m│
     * │        WHERE m.matchedCommande IS NOT NULL                  │
     * │    )                                                         │
     * │  ORDER BY c.createdAt ASC                                    │
     * └─────────────────────────────────────────────────────────────────┘
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
    List<Commande> findMatchingCandidates(
            @Param("device") Device device,
            @Param("operateur") Operateur operateur,
            @Param("phase") CommandPhase phase,
            @Param("amount") BigDecimal amount,
            @Param("windowStart") Instant windowStart
    );

    // ==================== RECHERCHE DE CANDIDATS (TOUTES PHASES) ====================

    /**
     * Recherche les commandes candidates pour la réconciliation, TOUTES phases confondues.
     *
     * ⚠️ UTILISATION DÉCONSEILLÉE POUR LA RÉCONCILIATION AUTOMATIQUE.
     *
     * Gardée pour des cas spécifiques (ex: supervision admin, debugging).
     * Pour la réconciliation automatique, utiliser findMatchingCandidates()
     * avec phase = WITHDRAWAL.
     *
     * ┌─────────────────────────────────────────────────────────────────┐
     * │  findMatchingCandidatesAnyPhase(device, operateur, ...)       │
     * │                                                                 │
     * │  ⚠️ Retourne des commandes WITHDRAWAL ET EXECUTION             │
     * │  → À utiliser UNIQUEMENT pour la supervision admin            │
     * └─────────────────────────────────────────────────────────────────┘
     */
    @Query("""
        SELECT c FROM Commande c
        JOIN c.transaction t
        WHERE c.device = :device
        AND c.operateur = :operateur
        AND t.amount = :amount
        AND c.createdAt >= :windowStart
        AND c.id NOT IN (
            SELECT m.matchedCommande.id FROM MessageOperateurBrut m
            WHERE m.matchedCommande IS NOT NULL
        )
        ORDER BY c.createdAt ASC
        """)
    List<Commande> findMatchingCandidatesAnyPhase(
            @Param("device") Device device,
            @Param("operateur") Operateur operateur,
            @Param("amount") BigDecimal amount,
            @Param("windowStart") Instant windowStart
    );

    // ==================== GESTION DES RETRIES ====================

    /**
     * Recherche les messages en échec de traitement IA avec un nombre de retries
     * inférieur à la limite.
     * Utilisé pour le job de retry des messages IA.
     */
    List<MessageOperateurBrut> findByProcessingStatusAndIaRetryCountLessThan(
            MessageProcessingStatus status, int maxRetries
    );
}