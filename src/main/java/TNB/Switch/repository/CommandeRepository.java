package TNB.Switch.repository;

import TNB.Switch.entity.Commande;
import TNB.Switch.entity.Transaction;
import TNB.Switch.enums.CommandPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandeRepository
        extends JpaRepository<Commande, UUID>, JpaSpecificationExecutor<Commande> {

    List<Commande> findByTransaction(Transaction transaction);
    Optional<Commande> findByTransactionAndPhase(Transaction transaction, CommandPhase phase);
    List<Commande> findByPhaseAndDeviceIsNull(CommandPhase phase);
}