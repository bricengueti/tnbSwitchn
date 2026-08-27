package TNB.Switch.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "compensation_attempt", indexes = {
        @Index(name = "idx_compensation_transaction", columnList = "transaction_id, created_at")
})
public class CompensationAttempt extends BaseLedgerEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne(optional = false)
    @JoinColumn(name = "commande_id", nullable = false, updatable = false)
    private Commande retryCommande;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    protected CompensationAttempt() {}

    public CompensationAttempt(Transaction transaction, Commande retryCommande, int attemptNumber) {
        this.transaction = transaction;
        this.retryCommande = retryCommande;
        this.attemptNumber = attemptNumber;
    }

    public Transaction getTransaction() { return transaction; }
    public Commande getRetryCommande() { return retryCommande; }
    public int getAttemptNumber() { return attemptNumber; }
}
