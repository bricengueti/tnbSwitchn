package TNB.Switch.service;

import TNB.Switch.entity.Otp;
import TNB.Switch.enums.OtpStatus;
import TNB.Switch.repository.OtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Marque proactivement EXPIRED les OTP PENDING dont le TTL est dépassé.
 * Sans ce job, un OTP jamais soumis reste PENDING indéfiniment en base —
 * pas un bug fonctionnel (AuthService.validateOtp vérifie déjà
 * l'expiration au moment d'un essai), mais un état incohérent visible en
 * base et dans tout futur écran de consultation d'historique OTP.
 */
@Service
public class OtpCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OtpCleanupService.class);

    private final OtpRepository otpRepository;

    public OtpCleanupService(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    // Toutes les 5 minutes — pas besoin d'une fréquence plus fine, le TTL
    // OTP lui-même est de l'ordre de quelques minutes (tnb.otp.ttl-seconds).
    @Scheduled(fixedRate = 300_000)
    @Transactional("transactionManager")
    public void expireStaleOtps() {
        List<Otp> expired = otpRepository.findExpiredPending(OtpStatus.PENDING, Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        for (Otp otp : expired) {
            otp.setStatus(OtpStatus.EXPIRED);
        }

        log.info("{} OTP marqué(s) EXPIRED", expired.size());
    }
}