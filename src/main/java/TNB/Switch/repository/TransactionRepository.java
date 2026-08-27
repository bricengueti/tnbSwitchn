package TNB.Switch.repository;

import TNB.Switch.entity.Transaction;
import TNB.Switch.entity.User;
import TNB.Switch.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByClientAndIdempotencyKey(User client, String idempotencyKey);

    List<Transaction> findByClientOrderByCreatedAtDesc(User client);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status = :status AND t.createdAt < :threshold
        """)
    List<Transaction> findStaleByStatus(
            @Param("status") TransactionStatus status,
            @Param("threshold") Instant threshold
    );
}