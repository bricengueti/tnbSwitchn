package TNB.Switch.entity;

import TNB.Switch.enums.CommandPhase;
import jakarta.persistence.*;

/**
 * Instruction envoyée à un device (retrait ou exécution).
 * Le statut d'avancement d'une Commande est en réalité porté par le
 * statut de la Transaction parente (cf. TransactionStatus) — pas de
 * statut dupliqué ici, pour éviter deux sources de vérité désynchronisables.
 */
@Entity
@Table(name = "commande", indexes = {
        @Index(name = "idx_commande_transaction", columnList = "transaction_id"),
        @Index(name = "idx_commande_device", columnList = "device_id"),
        @Index(name = "idx_commande_phase_device", columnList = "phase, device_id"),
        @Index(name = "idx_commande_created_at", columnList = "created_at")
})
public class Commande extends BaseLedgerEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, updatable = false, length = 20)
    private CommandPhase phase;

    // Device affecté au moment du routage — nullable tant que la commande
    // est encore en file d'attente (avant matching par le RoutingService).
    @ManyToOne
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operateur_id", nullable = false, updatable = false)
    private Operateur operateur;

    // Contenu résolu à partir du CommandTemplate de l'offre
    // (placeholders {phoneNumber}/{amount}... déjà substitués).
    @Column(name = "resolved_content", nullable = false, length = 500)
    private String resolvedContent;

    protected Commande() {
        // requis par JPA
    }

    public Commande(Transaction transaction, CommandPhase phase,
                    Operateur operateur, String resolvedContent) {
        this.transaction = transaction;
        this.phase = phase;
        this.operateur = operateur;
        this.resolvedContent = resolvedContent;
    }

    public Transaction getTransaction() { return transaction; }
    public CommandPhase getPhase() { return phase; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public Operateur getOperateur() { return operateur; }
    public String getResolvedContent() { return resolvedContent; }
}
