package TNB.Switch.service;


import TNB.Switch.repository.IdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyCleanupJob(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // Tous les jours à 3h du matin
    @Transactional
    public void cleanupExpiredKeys() {
        int deleted = idempotencyRepository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("{} clés d'idempotence expirées supprimées", deleted);
        }
    }
}