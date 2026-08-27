package TNB.Switch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

/**
 * Base pour toute entité MUTABLE avec cycle de vie métier
 * (activable/désactivable), avec verrouillage optimiste obligatoire.
 * Utilisée par : Device, Offre, Utilisateur, FlotteDevice, Admin.
 */
@MappedSuperclass
public abstract class BaseAuditableEntity extends BaseLedgerEntity {

    // Verrouillage optimiste — INDISPENSABLE sur Device et FlotteDevice
    // (cf. race condition sur l'affectation device évoquée précédemment)
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public boolean isDeleted() { return deleted; }

    public void markDeleted(UUID by) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = by;
    }
}