package TNB.Switch.entity;

import TNB.Switch.enums.OfferType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "offer")
public class Offer extends BaseAuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private OfferType type;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_operateur_id", nullable = false)
    private Operateur sourceOperator;

    @ManyToOne
    @JoinColumn(name = "destination_operateur_id")
    private Operateur destinationOperator;

    @Column(name = "price", nullable = false, precision = 19, scale = 0)
    private BigDecimal price;

    // Type CREDIT uniquement.
    @Column(name = "credit_amount", precision = 19, scale = 0)
    private BigDecimal creditAmount;

    // Type DATA uniquement.
    @Column(name = "data_volume_mb")
    private Integer dataVolumeMb;

    @Column(name = "data_validity_days")
    private Integer dataValidityDays;

    // Type EXCHANGE_MO uniquement (cf. §7.2 "taux/limites").
    @Column(name = "exchange_rate", precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(name = "min_amount", precision = 19, scale = 0)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 0)
    private BigDecimal maxAmount;

    // ========== FRAIS (commun à tous les types d'offres) ==========

    /**
     * Frais tnbSwitch en pourcentage (ex: 5.0 = 5%).
     * S'applique à TOUS les types d'offres (CREDIT, DATA, EXCHANGE_MO).
     * Défaut = 0% si non spécifié.
     */
    @Column(name = "offer_fee_percentage", nullable = false, precision = 10, scale = 4)
    private BigDecimal offerFeePercentage = BigDecimal.ZERO;

    // Reste sur Offer : contrairement au retrait, l'exécution dépend bien
    // du produit (vente crédit ≠ vente forfait ≠ transfert de float pour
    // échange MO) — pas seulement de l'opérateur.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "content", column = @Column(name = "execution_template_content"))
    })
    private CommandTemplate executionCommandTemplate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Offer() {
        // requis par JPA
    }

    public Offer(OfferType type, String label, Operateur sourceOperator) {
        this.type = type;
        this.label = label;
        this.sourceOperator = sourceOperator;
        this.offerFeePercentage = BigDecimal.ZERO; // défaut
    }

    // ==================== GETTERS / SETTERS ====================

    public OfferType getType() { return type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Operateur getSourceOperator() { return sourceOperator; }
    public void setSourceOperator(Operateur sourceOperator) { this.sourceOperator = sourceOperator; }
    public Operateur getDestinationOperator() { return destinationOperator; }
    public void setDestinationOperator(Operateur destinationOperator) { this.destinationOperator = destinationOperator; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public Integer getDataVolumeMb() { return dataVolumeMb; }
    public void setDataVolumeMb(Integer dataVolumeMb) { this.dataVolumeMb = dataVolumeMb; }
    public Integer getDataValidityDays() { return dataValidityDays; }
    public void setDataValidityDays(Integer dataValidityDays) { this.dataValidityDays = dataValidityDays; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

    public BigDecimal getOfferFeePercentage() { return offerFeePercentage; }
    public void setOfferFeePercentage(BigDecimal offerFeePercentage) {
        this.offerFeePercentage = offerFeePercentage != null ? offerFeePercentage : BigDecimal.ZERO;
    }

    public CommandTemplate getExecutionCommandTemplate() { return executionCommandTemplate; }
    public void setExecutionCommandTemplate(CommandTemplate t) { this.executionCommandTemplate = t; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // ==================== MÉTHODES UTILES ====================

    /**
     * Calcule le montant des frais à appliquer sur un montant donné.
     */
    public BigDecimal calculateFee(BigDecimal amount) {
        if (amount == null || offerFeePercentage == null || offerFeePercentage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(offerFeePercentage).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
}