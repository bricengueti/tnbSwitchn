package TNB.Switch.config;

import TNB.Switch.security.TnbPrincipal;
import TNB.Switch.service.CustomUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public class TnbAuditorAware implements AuditorAware<UUID> {

    public static final UUID SYSTEM_AUDITOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(SYSTEM_AUDITOR_ID);
        }

        Object principal = authentication.getPrincipal();

        // Un utilisateur/admin authentifié via CustomUserDetailsService.
        if (principal instanceof CustomUserDetails userDetails) {
            return Optional.ofNullable(userDetails.getId());
        }

        // Un device authentifié via son propre JWT (secret distinct,
        // cf. tnb.security.device-jwt.*) — conserve TnbPrincipal pour
        // ce cas, non couvert par UserDetails.
        if (principal instanceof TnbPrincipal tnbPrincipal) {
            return Optional.ofNullable(tnbPrincipal.id());
        }

        return Optional.of(SYSTEM_AUDITOR_ID);
    }
}