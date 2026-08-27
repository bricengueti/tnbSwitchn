package TNB.Switch.entity;


import TNB.Switch.enums.OtpStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "otp", indexes = {
        @Index(name = "idx_otp_user_status", columnList = "user_id, status, created_at")
})
public class Otp extends BaseLedgerEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // Hash du code, jamais le code en clair (CDC §13 — données sensibles).
    @Column(name = "code_hash", nullable = false, updatable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OtpStatus status = OtpStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    protected Otp() {
        // requis par JPA
    }

    public Otp(User user, String codeHash, Instant expiresAt) {
        this.user = user;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public User getUser() { return user; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public OtpStatus getStatus() { return status; }
    public void setStatus(OtpStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void incrementAttemptCount() { this.attemptCount++; }
}