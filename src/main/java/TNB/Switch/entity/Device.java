package TNB.Switch.entity;

import TNB.Switch.enums.DeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_pairing_code", columnNames = "pairing_code")
)
public class Device extends BaseAuditableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // Identifiant d'appairage unique physique/logique (utilisé au CONNECT STOMP).
    @Column(name = "pairing_code", nullable = false, updatable = false, length = 64)
    private String pairingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.OFFLINE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "device_operator",
            joinColumns = @JoinColumn(name = "device_id"),
            inverseJoinColumns = @JoinColumn(name = "operateur_id")
    )
    private Set<Operateur> supportedOperators = new HashSet<>();

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    // Hash du credential pour l'authentification forte device (§13) —
    // le secret réel ne doit jamais être stocké en clair.
    @Column(name = "credential_hash", nullable = false, length = 255)
    private String credentialHash;

    protected Device() {
        // requis par JPA
    }

    public Device(String name, String pairingCode, String credentialHash) {
        this.name = name;
        this.pairingCode = pairingCode;
        this.credentialHash = credentialHash;
    }

    // Utilitaire simple de lecture, pas de logique de transition ici.
    public boolean supportsOperator(Operateur operateur) {
        return supportedOperators.contains(operateur);
    }

    public void addSupportedOperator(Operateur operateur) {
        this.supportedOperators.add(operateur);
    }

    public void removeSupportedOperator(Operateur operateur) {
        this.supportedOperators.remove(operateur);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPairingCode() { return pairingCode; }
    public DeviceStatus getStatus() { return status; }
    public void setStatus(DeviceStatus status) { this.status = status; }
    public Set<Operateur> getSupportedOperators() { return supportedOperators; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public String getCredentialHash() { return credentialHash; }
}