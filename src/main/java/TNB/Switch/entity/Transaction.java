package TNB.Switch.entity;

import TNB.Switch.enums.TransactionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tnb_transaction", indexes = {
        @Index(name = "idx_transaction_status", columnList = "status"),
        @Index(name = "idx_transaction_client", columnList = "client_id"),
        @Index(name = "idx_transaction_created_at", columnList = "created_at"),
        @Index(name = "idx_transaction_status_created_at", columnList = "status, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_transaction_client_idempotency", columnNames = {"client_id", "idempotency_key"})
})
public class Transaction extends BaseLedgerEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private User client;

    @ManyToOne(optional = false)
    @JoinColumn(name = "offer_id", nullable = false, updatable = false)
    private Offer offer;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status = TransactionStatus.WAIT_OTP;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    // Uniquement renseigné pour les offres EXCHANGE_MO — numéro du wallet
    // de destination du dépôt, saisi par le client à la création de la
    // transaction. Utilisé par CommandTemplateResolver pour résoudre le
    // placeholder {destinationPhoneNumber} du gabarit d'exécution.
    @Column(name = "destination_phone_number", length = 20, updatable = false)
    private String destinationPhoneNumber;

    @Column(name = "payer_phone_number", length = 20, updatable = false)  // ✅ nom distinct
    private String payerPhoneNumber;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transaction() {
        // requis par JPA
    }

    public Transaction(Offer offer, User client, BigDecimal amount, TransactionStatus status, String idempotencyKey, String destinationPhoneNumber, String payerPhoneNumber, Instant completedAt) {
        this.offer = offer;
        this.client = client;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.destinationPhoneNumber = destinationPhoneNumber;
        this.payerPhoneNumber = payerPhoneNumber;
        this.completedAt = completedAt;
    }

    public User getClient() {
        return client;
    }

    public void setClient(User client) {
        this.client = client;
    }

    public Offer getOffer() {
        return offer;
    }

    public void setOffer(Offer offer) {
        this.offer = offer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDestinationPhoneNumber() {
        return destinationPhoneNumber;
    }

    public void setDestinationPhoneNumber(String destinationPhoneNumber) {
        this.destinationPhoneNumber = destinationPhoneNumber;
    }

    public String getPayerPhoneNumber() {
        return payerPhoneNumber;
    }

    public void setPayerPhoneNumber(String payerPhoneNumber) {
        this.payerPhoneNumber = payerPhoneNumber;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}