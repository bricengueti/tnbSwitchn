package TNB.Switch.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Injecte un traceId unique par requête dans le MDC — apparaît alors
 * automatiquement dans chaque ligne de log JSON générée pendant le
 * traitement de cette requête, permettant de corréler tous les logs
 * d'une même transaction HTTP dans Kibana.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Correlation-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER_NAME);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER_NAME, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Nettoyage systématique : évite qu'un traceId "fuite" vers la
            // requête suivante traitée par le même thread (pool réutilisé).
            MDC.remove(MDC_KEY);
        }
    }
}