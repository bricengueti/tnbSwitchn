package TNB.Switch.entity;

import TNB.Switch.enums.FleetMovementReason;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Trace immuable de tout mouvement de solde flotte (§7.7 : "motif obligatoire
 * et historique des mouvements"). Une ligne créée n'est jamais modifiée
 * ni supprimée — c'est la preuve d'audit en cas de litige ou de contrôle
 * réglementaire (BEAC/COBAC).
 */
@Entity
@Table(name = "fleet_movement", indexes = {
        @Index(name = "idx_movement_fleet_balance", columnList = "fleet_balance_id, created_at"),
        @Index(name = "idx_movement_transaction", columnList = "transaction_id")
})
public class FleetMovement extends BaseLedgerEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "fleet_balance_id", nullable = false, updatable = false)
    private FleetBalance fleetBalance;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 30)
    private FleetMovementReason reason;

    // Justificatif obligatoire pour tout mouvement manuel (§7.7) — nullable
    // uniquement pour les mouvements automatiques (TRANSACTION_DEBIT/CREDIT)
    // où le motif est déjà porté par la transaction liée.
    @Column(name = "justification", length = 500)
    private String justification;

    // Référence vers la transaction à l'origine du mouvement, si applicable
    // (mouvements automatiques). Nullable pour les ajustements manuels admin.
    @Column(name = "transaction_id")
    private java.util.UUID transactionId;

    protected FleetMovement() {
        // requis par JPA
    }

    public FleetMovement(FleetBalance fleetBalance, BigDecimal amount,
                         FleetMovementReason reason, String justification,
                         java.util.UUID transactionId) {
        this.fleetBalance = fleetBalance;
        this.amount = amount;
        this.reason = reason;
        this.justification = justification;
        this.transactionId = transactionId;
    }

    public FleetBalance getFleetBalance() { return fleetBalance; }
    public BigDecimal getAmount() { return amount; }
    public FleetMovementReason getReason() { return reason; }
    public String getJustification() { return justification; }
    public java.util.UUID getTransactionId() { return transactionId; }
}