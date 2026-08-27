package TNB.Switch.security;


import TNB.Switch.entity.Device;
import TNB.Switch.repository.DeviceRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Intercepte la frame CONNECT STOMP des devices — authentification par
 * pairingCode + credential (distincte du JWT client/admin, cf.
 * CustomUserDetails). Injecte un TnbPrincipal(deviceId, DEVICE, false)
 * dans la session STOMP, réutilisé ensuite par TnbAuditorAware pour
 * tracer created_by sur les MessageOperateurBrut transmis par ce device.
 */
@Component
public class DeviceStompAuthInterceptor implements ChannelInterceptor {

    private final DeviceRepository deviceRepository;

    public DeviceStompAuthInterceptor(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String pairingCode = accessor.getFirstNativeHeader("Pairing-Code");
            String credential = accessor.getFirstNativeHeader("Credential");

            if (pairingCode == null || credential == null) {
                throw new IllegalArgumentException(
                        "Connexion device refusée : Pairing-Code et Credential requis"
                );
            }

            Device device = deviceRepository.findByPairingCode(pairingCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Connexion device refusée : pairingCode inconnu"
                    ));

            if (!BCrypt.checkpw(credential, device.getCredentialHash())) {
                throw new IllegalArgumentException("Connexion device refusée : credential invalide");
            }

            TnbPrincipal principal = new TnbPrincipal(device.getId(), TnbPrincipal.ActorType.DEVICE, false);
            // Remplacer la ligne accessor.setUser(...) par :
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of()
            ) {
                @Override
                public String getName() {
                    // Force le "username" STOMP à être l'UUID du device, exploité
                    // ensuite par CommandDispatcher.dispatch() via convertAndSendToUser.
                    return principal.id().toString();
                }
            };
            accessor.setUser(authentication);
        }

        return message;
    }
}
