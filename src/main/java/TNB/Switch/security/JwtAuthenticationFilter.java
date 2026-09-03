package TNB.Switch.security;

import TNB.Switch.security.TnbPrincipal;
import TNB.Switch.service.CustomUserDetails;
import TNB.Switch.service.CustomUserDetailsService;
import TNB.Switch.utils.JwtUtils;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, CustomUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                // === CAS 1 : Token USER/ADMIN (charge depuis la base) ===
                if (jwtUtils.isValidAccessToken(token)) {
                    UUID userId = jwtUtils.getUserId(token);
                    CustomUserDetails userDetails = userDetailsService.loadUserById(userId);

                    if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Utilisateur [{}] authentifié via JWT", userId);
                    } else {
                        log.warn("Compte désactivé/suspendu [{}]", userId);
                    }
                    filterChain.doFilter(request, response);
                    return;
                }

                // === CAS 2 : Token DEVICE (authentification forte) ===
                if (jwtUtils.isValidDeviceToken(token)) {
                    UUID deviceId = jwtUtils.getDeviceId(token);
                    String pairingCode = jwtUtils.getPairingCode(token);

                    TnbPrincipal principal = new TnbPrincipal(
                            deviceId,
                            TnbPrincipal.ActorType.DEVICE,
                            false
                    );

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Device [{}] authentifié via JWT", deviceId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Token invalide ou type inconnu
                log.debug("Token JWT non reconnu ou invalide");

            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                log.debug("Authentification JWT échouée : {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}