package TNB.Switch.entity;

import TNB.Switch.enums.OperateurType;
import TNB.Switch.enums.OfferType;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(
        name = "operateur",
        uniqueConstraints = @UniqueConstraint(name = "uk_operateur_code", columnNames = "code")
)
public class Operateur extends BaseAuditableEntity {

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "nom", nullable = false, length = 100)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private OperateurType type;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    // ═══════════════════════════════════════════════════════
    //  PRÉFIXES TÉLÉPHONIQUES
    // ═══════════════════════════════════════════════════════

    /**
     * Liste des préfixes de numéros de téléphone pour cet opérateur.
     * Gérée via l'entité PhonePrefix.
     */
    @OneToMany(mappedBy = "operateur", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PhonePrefix> phonePrefixes = new ArrayList<>();

    // ═══════════════════════════════════════════════════════
    //  TEMPLATES DE COMMANDES
    // ═══════════════════════════════════════════════════════

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "content", column = @Column(name = "withdrawal_template_content"))
    })
    private CommandTemplate withdrawalCommandTemplate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "operateur_execution_templates",
            joinColumns = @JoinColumn(name = "operateur_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "offer_type")
    private Map<OfferType, CommandTemplate> executionCommandTemplates = new EnumMap<>(OfferType.class);

    // ═══════════════════════════════════════════════════════
    //  CONSTRUCTEURS
    // ═══════════════════════════════════════════════════════

    protected Operateur() {}

    public Operateur(String code, String nom, OperateurType type) {
        this.code = code;
        this.nom = nom;
        this.type = type;
    }

    public Operateur(String code, String nom, OperateurType type, CommandTemplate withdrawalCommandTemplate) {
        this(code, nom, type);
        this.withdrawalCommandTemplate = withdrawalCommandTemplate;
    }

    // ═══════════════════════════════════════════════════════
    //  GETTERS / SETTERS
    // ═══════════════════════════════════════════════════════

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public OperateurType getType() { return type; }
    public void setType(OperateurType type) { this.type = type; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public List<PhonePrefix> getPhonePrefixes() { return phonePrefixes; }
    public void setPhonePrefixes(List<PhonePrefix> phonePrefixes) {
        this.phonePrefixes = phonePrefixes != null ? phonePrefixes : new ArrayList<>();
    }

    public CommandTemplate getWithdrawalCommandTemplate() { return withdrawalCommandTemplate; }
    public void setWithdrawalCommandTemplate(CommandTemplate withdrawalCommandTemplate) {
        this.withdrawalCommandTemplate = withdrawalCommandTemplate;
    }

    public Map<OfferType, CommandTemplate> getExecutionCommandTemplates() { return executionCommandTemplates; }
    public void setExecutionCommandTemplates(Map<OfferType, CommandTemplate> executionCommandTemplates) {
        this.executionCommandTemplates = executionCommandTemplates != null ? executionCommandTemplates : new EnumMap<>(OfferType.class);
    }

    // ═══════════════════════════════════════════════════════
    //  MÉTHODES UTILES
    // ═══════════════════════════════════════════════════════

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }

    public CommandTemplate getExecutionTemplateForOfferType(OfferType offerType) {
        return executionCommandTemplates.get(offerType);
    }

    public void addExecutionTemplate(OfferType offerType, CommandTemplate template) {
        this.executionCommandTemplates.put(offerType, template);
    }

    public boolean supportsOfferType(OfferType offerType) {
        return executionCommandTemplates.containsKey(offerType);
    }

    // ═══════════════════════════════════════════════════════
    //  MÉTHODES DE VALIDATION DES NUMÉROS
    // ═══════════════════════════════════════════════════════

    /**
     * Ajoute un préfixe téléphonique à l'opérateur.
     */
    public void addPhonePrefix(PhonePrefix phonePrefix) {
        if (phonePrefix != null) {
            phonePrefix.setOperateur(this);
            this.phonePrefixes.add(phonePrefix);
        }
    }

    /**
     * Ajoute un préfixe téléphonique à l'opérateur (par valeur).
     */
    public void addPhonePrefix(String prefix, String description) {
        PhonePrefix phonePrefix = new PhonePrefix(prefix, this, description);
        this.phonePrefixes.add(phonePrefix);
    }

    /**
     * Supprime un préfixe téléphonique.
     */
    public void removePhonePrefix(PhonePrefix phonePrefix) {
        if (phonePrefix != null) {
            this.phonePrefixes.remove(phonePrefix);
            phonePrefix.setOperateur(null);
        }
    }

    /**
     * Vérifie si un numéro de téléphone appartient à cet opérateur.
     */
    public boolean ownsPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank() || phonePrefixes.isEmpty()) {
            return false;
        }

        // Nettoyer le numéro
        String cleaned = normalizePhoneNumber(phoneNumber);

        // Vérifier si le numéro commence par l'un des préfixes actifs
        return phonePrefixes.stream()
                .filter(PhonePrefix::isActive)
                .anyMatch(prefix -> cleaned.startsWith(prefix.getPrefix()));
    }

    /**
     * Récupère tous les préfixes actifs sous forme de liste de chaînes.
     */
    public List<String> getActivePrefixes() {
        return phonePrefixes.stream()
                .filter(PhonePrefix::isActive)
                .map(PhonePrefix::getPrefix)
                .toList();
    }

    /**
     * Normalise un numéro de téléphone (enlève les caractères non numériques).
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        return phoneNumber.replaceAll("[^0-9]", "");
    }
}