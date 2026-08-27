package TNB.Switch.entity;

import TNB.Switch.enums.OperateurType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "operateur",
        uniqueConstraints = @UniqueConstraint(name = "uk_operateur_code", columnNames = "code")
)
public class Operateur extends BaseAuditableEntity {

    // Code métier stable, PAS la clé technique (UUID hérité reste la PK).
    // Immutable après création : sert de clé de matching dans le routage,
    // le parsing IA, les topics Kafka/STOMP.
    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code; // "MTN", "ORANGE", "CAMTEL", "YOOMEE", "NEXTTEL"...

    @Column(name = "nom", nullable = false, length = 100)
    private String nom; // "MTN Cameroun", nom affiché

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private OperateurType type;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    // Gabarit du code de retrait CLIENT, spécifique à l'opérateur et
    // identique quelle que soit l'offre achetée (ex. MTN :
    // "*126*14*{commercialNumber}*{amount}#"). Migré depuis Offer : ce
    // code ne dépend jamais du produit, seulement de l'opérateur —
    // le laisser sur Offer aurait obligé à le dupliquer sur chaque offre
    // du même opérateur, avec le risque qu'une correction en oublie une.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "content", column = @Column(name = "withdrawal_template_content"))
    })
    private CommandTemplate withdrawalCommandTemplate;

    protected Operateur() {}

    public Operateur(String code, String nom, OperateurType type, CommandTemplate withdrawalCommandTemplate) {
        this.code = code;
        this.nom = nom;
        this.type = type;
        this.withdrawalCommandTemplate = withdrawalCommandTemplate;
    }

    public String getCode() { return code; }
    public String getNom() { return nom; }
    public OperateurType getType() { return type; }
    public boolean isActif() { return actif; }
    public CommandTemplate getWithdrawalCommandTemplate() { return withdrawalCommandTemplate; }
    public void setWithdrawalCommandTemplate(CommandTemplate withdrawalCommandTemplate) {
        this.withdrawalCommandTemplate = withdrawalCommandTemplate;
    }

    public void activer() { this.actif = true; }
    public void desactiver() { this.actif = false; }
}