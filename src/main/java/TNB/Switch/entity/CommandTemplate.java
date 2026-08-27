package TNB.Switch.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Gabarit de commande envoyé au device. Le contenu peut contenir des
 * placeholders (ex. {phoneNumber}, {amount}) résolus au moment de la
 * création de la Commande réelle, jamais au moment de la définition
 * de l'offre par l'admin.
 */
@Embeddable
public class CommandTemplate {

    @Column(name = "template_content", nullable = false, length = 500)
    private String content;

    protected CommandTemplate() {
        // requis par JPA
    }

    public CommandTemplate(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Le contenu d'un gabarit de commande ne peut pas être vide"
            );
        }
        this.content = content;
    }

    public String getContent() { return content; }
}