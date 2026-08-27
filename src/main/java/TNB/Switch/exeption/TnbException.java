package TNB.Switch.exeption;
/**
 * Racine commune de toutes les exceptions métier tnbSwitch. Porte un code
 * d'erreur stable (indépendant du message, qui peut évoluer) — c'est ce
 * code qui sera utilisé côté frontend pour distinguer les cas sans parser
 * du texte, et côté ELK pour agréger/filtrer par type d'erreur.
 */
public abstract class TnbException extends RuntimeException {

    private final String errorCode;

    protected TnbException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected TnbException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}