package TNB.Switch.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * Représente un préfixe de numéro de téléphone associé à un opérateur.
 * Exemple: MTN → 65, 66, 67, 68, 69
 *          ORANGE → 62, 63, 64
 *          CAMTEL → 60, 61
 */
@Entity
@Table(name = "phone_prefix",
        uniqueConstraints = @UniqueConstraint(name = "uk_phone_prefix", columnNames = "prefix"))
public class PhonePrefix extends BaseAuditableEntity {

    @NotBlank(message = "Le préfixe est obligatoire")
    @Size(min = 1, max = 5, message = "Le préfixe doit contenir entre 1 et 5 caractères")
    @Pattern(regexp = "^[0-9]+$", message = "Le préfixe doit contenir uniquement des chiffres")
    @Column(name = "prefix", nullable = false, length = 5, unique = true)
    private String prefix;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operateur_id", nullable = false)
    private Operateur operateur;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // ═══════════════════════════════════════════════════════
    //  CONSTRUCTEURS
    // ═══════════════════════════════════════════════════════

    protected PhonePrefix() {}

    public PhonePrefix(String prefix, Operateur operateur) {
        this.prefix = prefix;
        this.operateur = operateur;
    }

    public PhonePrefix(String prefix, Operateur operateur, String description) {
        this(prefix, operateur);
        this.description = description;
    }

    // ═══════════════════════════════════════════════════════
    //  GETTERS / SETTERS
    // ═══════════════════════════════════════════════════════

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public Operateur getOperateur() { return operateur; }
    public void setOperateur(Operateur operateur) { this.operateur = operateur; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // ═══════════════════════════════════════════════════════
    //  MÉTHODES UTILES
    // ═══════════════════════════════════════════════════════

    public void activer() { this.active = true; }
    public void desactiver() { this.active = false; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhonePrefix that = (PhonePrefix) o;
        return Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix);
    }
}