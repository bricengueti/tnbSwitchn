package TNB.Switch.service;

import TNB.Switch.config.TokenPair;
import TNB.Switch.entity.Otp;
import TNB.Switch.entity.User;
import TNB.Switch.enums.OtpStatus;
import TNB.Switch.enums.UserAccountStatus;
import TNB.Switch.exeption.OtpExpiredException;
import TNB.Switch.exeption.OtpInvalidException;
import TNB.Switch.exeption.OtpMaxAttemptsExceededException;
import TNB.Switch.exeption.OtpThrottledException;
import TNB.Switch.impl.SmsSender;
import TNB.Switch.repository.OtpRepository;
import TNB.Switch.repository.UserRepository;
import TNB.Switch.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final SmsSender smsSender;
    private final JwtUtils jwtUtils;

    private final int otpLength;
    private final int otpTtlSeconds;
    private final int otpMaxAttempts;
    private final int otpThrottleSeconds;

    public AuthService(
            UserRepository userRepository,
            OtpRepository otpRepository,
            SmsSender smsSender,
            JwtUtils jwtUtils,
            @Value("${tnb.otp.length}") int otpLength,
            @Value("${tnb.otp.ttl-seconds}") int otpTtlSeconds,
            @Value("${tnb.otp.max-attempts}") int otpMaxAttempts,
            @Value("${tnb.otp.throttle-seconds}") int otpThrottleSeconds) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.smsSender = smsSender;
        this.jwtUtils = jwtUtils;
        this.otpLength = otpLength;
        this.otpTtlSeconds = otpTtlSeconds;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpThrottleSeconds = otpThrottleSeconds;
    }

    /**
     * Demande un OTP pour un numéro donné. Crée l'utilisateur s'il n'existe
     * pas encore (PENDING_VERIFICATION). Applique le throttling anti
     * brute-force avant toute génération. Réutilisée à la fois pour le
     * login et pour la confirmation d'une transaction (TransactionService) —
     * le client garde le transactionId de son côté, pas besoin de le lier
     * à l'OTP lui-même (option retenue : la plus simple).
     */
    @Transactional
    public void requestOtp(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> userRepository.save(new User(phoneNumber)));

        Instant throttleWindowStart = Instant.now().minusSeconds(otpThrottleSeconds);
        long recentCount = otpRepository.countByUserAndCreatedAtAfter(user, throttleWindowStart);
        if (recentCount > 0) {
            throw new OtpThrottledException(otpThrottleSeconds);
        }

        String code = generateNumericCode(otpLength);
        String codeHash = BCrypt.hashpw(code, BCrypt.gensalt());
        Instant expiresAt = Instant.now().plusSeconds(otpTtlSeconds);

        Otp otp = new Otp(user, codeHash, expiresAt);
        otpRepository.save(otp);

        smsSender.send(phoneNumber, "Votre code tnbSwitch : %s (valide %d min)"
                .formatted(code, otpTtlSeconds / 60));

        log.info("OTP généré pour l'utilisateur [{}]", user.getId());
    }

    /**
     * Vérifie un code OTP SANS émettre de token — extrait de validateOtp
     * pour être réutilisable par TransactionService (confirmation d'une
     * transaction ne doit jamais délivrer un JWT). Lève une exception si
     * invalide/expiré/épuisé ; ne retourne rien si valide, chaque appelant
     * décide de la suite.
     */
    @Transactional
    public void verifyOtp(String phoneNumber, String code) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(OtpInvalidException::new);

        List<Otp> pendingOtps = otpRepository.findByUserAndStatusOrderByCreatedAtDesc(
                user, OtpStatus.PENDING
        );
        if (pendingOtps.isEmpty()) {
            throw new OtpInvalidException();
        }

        Otp latestOtp = pendingOtps.get(0);

        if (Instant.now().isAfter(latestOtp.getExpiresAt())) {
            latestOtp.setStatus(OtpStatus.EXPIRED);
            throw new OtpExpiredException();
        }

        if (latestOtp.getAttemptCount() >= otpMaxAttempts) {
            latestOtp.setStatus(OtpStatus.INVALID);
            throw new OtpMaxAttemptsExceededException();
        }

        if (!BCrypt.checkpw(code, latestOtp.getCodeHash())) {
            latestOtp.incrementAttemptCount();
            if (latestOtp.getAttemptCount() >= otpMaxAttempts) {
                latestOtp.setStatus(OtpStatus.INVALID);
            }
            throw new OtpInvalidException();
        }

        latestOtp.setStatus(OtpStatus.VALIDATED);

        if (user.getAccountStatus() == UserAccountStatus.PENDING_VERIFICATION) {
            user.setAccountStatus(UserAccountStatus.ACTIVE);
        }
    }

    /**
     * Valide un OTP de LOGIN et retourne la paire de tokens — désormais un
     * simple appelant de verifyOtp, qui porte toute la logique de
     * vérification.
     */
    @Transactional
    public AuthResult validateOtp(String phoneNumber, String code) {
        verifyOtp(phoneNumber, code);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(OtpInvalidException::new);

        TokenPair tokens = jwtUtils.generateTokenPair(user.getId(), phoneNumber, user.isAdmin());

        log.info("Connexion réussie pour l'utilisateur [{}], isAdmin={}", user.getId(), user.isAdmin());

        return new AuthResult(tokens, user.isAdmin());
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public record AuthResult(TokenPair tokens, boolean isAdmin) {}
}