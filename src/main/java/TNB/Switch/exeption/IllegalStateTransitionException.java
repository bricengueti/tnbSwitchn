package TNB.Switch.exeption;

import java.util.UUID;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(
            String entiteType, UUID entiteId, Enum<?> depuis, Enum<?> vers) {
        super("Transition invalide sur %s [%s] : %s -> %s"
                .formatted(entiteType, entiteId, depuis, vers));
    }
}