package TNB.Switch.entity;

import TNB.Switch.enums.OfferType;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Offre de recharge (CREDIT, DATA, EXCHANGE_MO).
 *
 * L'offre est un produit pur : elle décrit ce qui est vendu,
 * sans connaître les opérateurs. Les opérateurs sont déterminés
 * par la transaction (fromOperateur, toOperateur).
 */
@Entity
@Table(name = "offer")
public class Offer extends BaseAuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private OfferType type;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    // ❌ SUPPRIMER sourceOperator
    // @ManyToOne(optional = false)
    // @JoinColumn(name = "source_operateur_id", nullable = false)
    // private Operateur sourceOperator;

    // ❌ SUPPRIMER destinationOperator
    // @ManyToOne
    // @JoinColumn(name = "destination_operateur_id")
    // private Operateur destinationOperator;

    @Column(name = "price", nullable = false, precision = 19, scale = 0)
    private BigDecimal price;

    // Type CREDIT uniquement
    @Column(name = "credit_amount", precision = 19, scale = 0)
    private BigDecimal creditAmount;

    // Type DATA uniquement
    @Column(name = "data_volume_mb")
    private Integer dataVolumeMb;

    @Column(name = "data_validity_days")
    private Integer dataValidityDays;

    // Type EXCHANGE_MO uniquement
    @Column(name = "exchange_rate", precision = 10, scale = 4)
    private BigDecimal exchangeRate;

    @Column(name = "min_amount", precision = 19, scale = 0)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 0)
    private BigDecimal maxAmount;

    @Column(name = "offer_fee_percentage", nullable = false, precision = 10, scale = 4)
    private BigDecimal offerFeePercentage = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ═══════════════════════════════════════════════════════
    //  CONSTRUCTEURS
    // ═══════════════════════════════════════════════════════

    protected Offer() {}

    public Offer(OfferType type, String label) {
        this.type = type;
        this.label = label;
        this.offerFeePercentage = BigDecimal.ZERO;
    }

    public Offer(OfferType type, String label, BigDecimal offerFeePercentage) {
        this.type = type;
        this.label = label;
        this.offerFeePercentage = offerFeePercentage != null ? offerFeePercentage : BigDecimal.ZERO;
    }

    // ═══════════════════════════════════════════════════════
    //  GETTERS / SETTERS
    // ═══════════════════════════════════════════════════════

    public OfferType getType() { return type; }
    public void setType(OfferType type) { this.type = type; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

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

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // ═══════════════════════════════════════════════════════
    //  MÉTHODES UTILES
    // ═══════════════════════════════════════════════════════

    public void activer() { this.active = true; }
    public void desactiver() { this.active = false; }

    /**
     * Calcule le montant des frais à appliquer sur un montant donné.
     */
    public BigDecimal calculateFee(BigDecimal amount) {
        if (amount == null || offerFeePercentage == null || offerFeePercentage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(offerFeePercentage).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Vérifie si l'offre est de type CREDIT.
     */
    public boolean isCredit() {
        return type == OfferType.CREDIT;
    }

    /**
     * Vérifie si l'offre est de type DATA.
     */
    public boolean isData() {
        return type == OfferType.DATA;
    }

    /**
     * Vérifie si l'offre est de type EXCHANGE_MO.
     */
    public boolean isExchangeMo() {
        return type == OfferType.EXCHANGE_MO;
    }
}