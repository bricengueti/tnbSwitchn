package TNB.Switch.config;
import TNB.Switch.security.TnbPrincipal;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                if (jwtUtils.isValidAccessToken(token)) {
                    TnbPrincipal principal = new TnbPrincipal(
                            jwtUtils.getUserId(token),
                            TnbPrincipal.ActorType.USER,
                            jwtUtils.getIsAdmin(token)
                    );

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Token invalide/expiré : on ne bloque pas ici, on laisse
                // simplement le SecurityContext vide — c'est SecurityConfig
                // qui décidera si l'endpoint exige une authentification.
                log.debug("Token JWT invalide : {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}