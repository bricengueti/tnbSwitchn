package TNB.Switch.repository;


import TNB.Switch.entity.CompensationAttempt;
import TNB.Switch.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompensationAttemptRepository extends JpaRepository<CompensationAttempt, UUID> {
    List<CompensationAttempt> findByTransactionOrderByAttemptNumberAsc(Transaction transaction);
    int countByTransaction(Transaction transaction);
}