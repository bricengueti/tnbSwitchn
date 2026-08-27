package TNB.Switch.entity;

import TNB.Switch.enums.AuditedEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Trace immuable de chaque changement de statut, tous types d'entités
 * confondus (CDC §9.4). Une ligne créée n'est jamais modifiée ni supprimée.
 */
@Entity
@Table(name = "historique_statut")
public class HistoriqueStatut extends BaseLedgerEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 30)
    private AuditedEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    // Nom du statut stocké en String plutôt qu'en enum unique : chaque type
    // d'entité (Transaction, Device, MessageOperateurBrut) a son propre enum
    // de statuts. On perd la contrainte de type au niveau colonne, mais on
    // évite un enum fourre-tout incohérent — le service applicatif garantit
    // la cohérence à l'écriture.
    @Column(name = "status", nullable = false, updatable = false, length = 40)
    private String status;

    // Acteur ayant déclenché le changement (peut différer de created_by
    // hérité si besoin de préciser un contexte, ex. "SYSTEM_TIMEOUT").
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    protected HistoriqueStatut() {
        // requis par JPA
    }

    public HistoriqueStatut(AuditedEntityType entityType, UUID entityId,
                            String status, UUID actorId) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = status;
        this.actorId = actorId;
    }

    public AuditedEntityType getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getStatus() { return status; }
    public UUID getActorId() { return actorId; }
}