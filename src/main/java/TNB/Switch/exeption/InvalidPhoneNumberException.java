package TNB.Switch.exeption;

/**
 * Exception levée lorsqu'un numéro de téléphone est invalide ou
 * n'appartient pas à l'opérateur attendu.
 */
public class InvalidPhoneNumberException extends RuntimeException {

    public InvalidPhoneNumberException(String message) {
        super(message);
    }

    public InvalidPhoneNumberException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidPhoneNumberException(String phoneNumber, String operateurCode) {
        super(String.format("Le numéro %s n'appartient pas à l'opérateur %s",
                phoneNumber, operateurCode));
    }
}