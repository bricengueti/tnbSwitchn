package TNB.Switch.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle nominal : AVAILABLE -> HOLDS -> AVAILABLE (cf. CDC §9.2).
 * EN_PAUSE / HORS_LIGNE / EN_ERREUR sont transverses : atteignables
 * depuis n'importe quel état, avec sortie vers AVAILABLE jamais
 * automatique pour EN_PAUSE (action admin explicite requise).
 */
public enum DeviceStatus {

    AVAILABLE {
        @Override
        public Set<DeviceStatus> allowedTransitions() {
            return EnumSet.of(HOLDS, PAUSED, OFFLINE, ERROR);
        }
    },
    HOLDS {
        @Override
        public Set<DeviceStatus> allowedTransitions() {
            // Un device en HOLDS peut quand même tomber hors-ligne
            // ou en erreur en pleine exécution USSD.
            return EnumSet.of(AVAILABLE, OFFLINE, ERROR);
        }
    },
    PAUSED {
        @Override
        public Set<DeviceStatus> allowedTransitions() {
            // Sortie de pause : uniquement vers AVAILABLE, action admin explicite.
            return EnumSet.of(AVAILABLE, OFFLINE);
        }
    },
    OFFLINE {
        @Override
        public Set<DeviceStatus> allowedTransitions() {
            // Reconnexion -> AVAILABLE uniquement (jamais HOLDS : on ne reprend
            // pas une commande en cours après une coupure, cf. point ouvert).
            return EnumSet.of(AVAILABLE, PAUSED, ERROR);
        }
    },
    ERROR {
        @Override
        public Set<DeviceStatus> allowedTransitions() {
            // Sortie d'erreur : reprise manuelle admin uniquement.
            return EnumSet.of(AVAILABLE, PAUSED, OFFLINE);
        }
    };

    public abstract Set<DeviceStatus> allowedTransitions();

    public boolean canTransitionTo(DeviceStatus target) {
        return allowedTransitions().contains(target);
    }
}