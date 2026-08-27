package TNB.Switch.utils;

import TNB.Switch.config.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {

    @Value("${tnb.security.jwt.secret}")
    private String secret;

    @Value("${tnb.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${tnb.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${tnb.security.jwt.issuer}")
    private String issuer;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== GÉNÉRATION ====================

    public String generateAccessToken(UUID userId, String phoneNumber, boolean isAdmin) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("phoneNumber", phoneNumber)
                .claim("isAdmin", isAdmin)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId, String phoneNumber) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("phoneNumber", phoneNumber)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public TokenPair generateTokenPair(UUID userId, String phoneNumber, boolean isAdmin) {
        return new TokenPair(
                generateAccessToken(userId, phoneNumber, isAdmin),
                generateRefreshToken(userId, phoneNumber)
        );
    }

    // ==================== VALIDATION ====================

    /**
     * Lève JwtException (signature invalide, token expiré, mal formé...)
     * si le token ne peut pas être validé — jamais de retour silencieux
     * null/false ici, c'est à l'appelant de décider comment réagir
     * (cf. JwtAuthenticationFilter, qui attrape explicitement).
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = validateToken(token);
            return "ACCESS".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidRefreshToken(String token) {
        try {
            Claims claims = validateToken(token);
            return "REFRESH".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ==================== EXTRACTION ====================

    public UUID getUserId(String token) {
        return UUID.fromString(validateToken(token).getSubject());
    }

    public String getPhoneNumber(String token) {
        return validateToken(token).get("phoneNumber", String.class);
    }

    public boolean getIsAdmin(String token) {
        Boolean isAdmin = validateToken(token).get("isAdmin", Boolean.class);
        return Boolean.TRUE.equals(isAdmin);
    }
}