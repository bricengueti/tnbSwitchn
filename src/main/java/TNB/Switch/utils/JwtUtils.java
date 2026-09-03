package TNB.Switch.utils;

import TNB.Switch.config.TokenPair;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    // ==================== SECRETS ====================

    @Value("${tnb.security.jwt.secret}")
    private String userSecret;

    @Value("${tnb.security.device-jwt.secret}")
    private String deviceSecret;

    // ==================== EXPIRATIONS ====================

    @Value("${tnb.security.jwt.access-token-expiration:86400000}")
    private long accessTokenExpiration;

    @Value("${tnb.security.jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    @Value("${tnb.security.device-jwt.expiration:604800000}")
    private long deviceTokenExpiration;

    @Value("${tnb.security.jwt.issuer}")
    private String issuer;

    // ==================== CLÉS ====================

    private SecretKey getUserSigningKey() {
        return Keys.hmacShaKeyFor(userSecret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getDeviceSigningKey() {
        return Keys.hmacShaKeyFor(deviceSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== GÉNÉRATION USER ====================

    public String generateAccessToken(UUID userId, String phoneNumber, boolean isAdmin) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim("phoneNumber", phoneNumber)
                .claim("isAdmin", isAdmin)
                .claim("type", "ACCESS")
                .claim("audience", "USER")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getUserSigningKey())
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
                .claim("audience", "USER")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getUserSigningKey())
                .compact();
    }

    public TokenPair generateTokenPair(UUID userId, String phoneNumber, boolean isAdmin) {

        return new TokenPair(
                generateAccessToken(userId, phoneNumber, isAdmin),
                generateRefreshToken(userId, phoneNumber)
        );
    }

    // ==================== GÉNÉRATION DEVICE ====================

    public String generateDeviceToken(UUID deviceId, String pairingCode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + deviceTokenExpiration);

        return Jwts.builder()
                .subject(deviceId.toString())
                .issuer(issuer)
                .claim("pairingCode", pairingCode)
                .claim("type", "ACCESS")
                .claim("audience", "DEVICE")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getDeviceSigningKey())
                .compact();
    }

    // ==================== VALIDATION USER ====================

    public Claims validateUserToken(String token) {
        return Jwts.parser()
                .verifyWith(getUserSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = validateUserToken(token);
            return "ACCESS".equals(claims.get("type", String.class))
                    && "USER".equals(claims.get("audience", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isValidRefreshToken(String token) {
        try {
            Claims claims = validateUserToken(token);
            return "REFRESH".equals(claims.get("type", String.class))
                    && "USER".equals(claims.get("audience", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ==================== VALIDATION DEVICE ====================

    public Claims validateDeviceToken(String token) {
        return Jwts.parser()
                .verifyWith(getDeviceSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidDeviceToken(String token) {
        try {
            Claims claims = validateDeviceToken(token);
            return "ACCESS".equals(claims.get("type", String.class))
                    && "DEVICE".equals(claims.get("audience", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ==================== EXTRACTION USER ====================

    public UUID getUserId(String token) {
        return UUID.fromString(validateUserToken(token).getSubject());
    }

    public String getPhoneNumber(String token) {
        return validateUserToken(token).get("phoneNumber", String.class);
    }

    public boolean getIsAdmin(String token) {
        Boolean isAdmin = validateUserToken(token).get("isAdmin", Boolean.class);
        return Boolean.TRUE.equals(isAdmin);
    }

    // ==================== EXTRACTION DEVICE ====================

    public UUID getDeviceId(String token) {
        return UUID.fromString(validateDeviceToken(token).getSubject());
    }

    public String getPairingCode(String token) {
        return validateDeviceToken(token).get("pairingCode", String.class);
    }
}