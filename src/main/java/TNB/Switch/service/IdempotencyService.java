package TNB.Switch.service;

import TNB.Switch.annotation.ThrowingSupplier;
import TNB.Switch.entity.IdempotencyRecord;
import TNB.Switch.exeption.IdempotencyConflictException;
import TNB.Switch.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public <T> T executeOnce(String idempotencyKey, ThrowingSupplier<T> action) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        // Vérifier si la clé existe déjà
        Optional<IdempotencyRecord> existing = idempotencyRepository.findById(redisKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            // Si la clé est expirée, on la supprime et on continue
            if (record.getExpiresAt().isBefore(Instant.now())) {
                idempotencyRepository.delete(record);
            } else {
                // Clé encore valide → conflit
                throw new IdempotencyConflictException(idempotencyKey);
            }
        }

        // Créer la clé avec le statut "PROCESSING"
        IdempotencyRecord record = new IdempotencyRecord(
                redisKey,
                "PROCESSING",
                Instant.now().plus(DEFAULT_TTL)
        );
        idempotencyRepository.save(record);

        try {
            T result = action.get();

            // Succès : mettre à jour le statut (optionnel, pour traçabilité)
            record.setStatus("COMPLETED");
            idempotencyRepository.save(record);

            return result;
        } catch (Throwable t) {
            // Échec : supprimer la clé pour permettre un retry
            idempotencyRepository.delete(record);

            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
    }
}