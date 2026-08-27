package TNB.Switch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "fleet_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fleet_balance_device_operator",
                columnNames = {"device_id", "operateur_id"}
        )
)
public class FleetBalance extends BaseAuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private Device device;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operateur_id", nullable = false, updatable = false)
    private Operateur operateur;

    // Numéro commercial du couple (device, opérateur) — destinataire réel
    // du retrait client (cf. §7ter.3). Migré depuis Device : un device
    // physique peut porter plusieurs puces d'opérateurs différents,
    // chacune avec son propre numéro commercial.
    @Column(name = "commercial_number", nullable = false, length = 20, updatable = false)
    private String commercialNumber;

    // Montants en FCFA — BigDecimal plutôt que long pour rester compatible
    // avec d'éventuelles sous-unités futures et éviter tout piège de double.
    @Column(name = "credit_balance", nullable = false, precision = 19, scale = 0)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    @Column(name = "wallet_balance", nullable = false, precision = 19, scale = 0)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    protected FleetBalance() {
        // requis par JPA
    }

    public FleetBalance(Device device, Operateur operateur, String commercialNumber) {
        this.device = device;
        this.operateur = operateur;
        this.commercialNumber = commercialNumber;
    }

    public Device getDevice() { return device; }
    public Operateur getOperateur() { return operateur; }
    public String getCommercialNumber() { return commercialNumber; }
    public BigDecimal getCreditBalance() { return creditBalance; }
    public void setCreditBalance(BigDecimal creditBalance) { this.creditBalance = creditBalance; }
    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }
}