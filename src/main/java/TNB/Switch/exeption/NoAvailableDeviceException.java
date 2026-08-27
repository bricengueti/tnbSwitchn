package TNB.Switch.exeption;

public class NoAvailableDeviceException extends BusinessException {
    public NoAvailableDeviceException(String operateurCode) {
        super("NO_AVAILABLE_DEVICE",
                "Aucun device disponible pour l'opérateur %s".formatted(operateurCode));
    }
}