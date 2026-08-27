package TNB.Switch.repository;

import TNB.Switch.entity.Commande;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.MessageOperateurBrut;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.CommandPhase;
import TNB.Switch.enums.MessageProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageOperateurBrutRepository
        extends JpaRepository<MessageOperateurBrut, UUID>,
        JpaSpecificationExecutor<MessageOperateurBrut> {

    List<MessageOperateurBrut> findByProcessingStatus(MessageProcessingStatus status);

    List<MessageOperateurBrut> findByProcessingStatusOrderByReceivedAtAsc(
            MessageProcessingStatus status
    );

    @Query("""
        SELECT c FROM Commande c
        JOIN c.transaction t
        WHERE c.device = :device
        AND c.operateur = :operateur
        AND c.phase = :phase
        AND t.amount = :amount
        AND c.createdAt >= :windowStart
        AND c.id NOT IN (
            SELECT m.matchedCommande.id FROM MessageOperateurBrut m
            WHERE m.matchedCommande IS NOT NULL
        )
        ORDER BY c.createdAt ASC
        """)
    List<Commande> findMatchingCandidates(
            @Param("device") Device device,
            @Param("operateur") Operateur operateur,
            @Param("phase") CommandPhase phase,
            @Param("amount") BigDecimal amount,
            @Param("windowStart") Instant windowStart
    );
    // Ajout à MessageOperateurBrutRepository

    @Query("""
    SELECT c FROM Commande c
    JOIN c.transaction t
    WHERE c.device = :device
    AND c.operateur = :operateur
    AND t.amount = :amount
    AND c.createdAt >= :windowStart
    AND c.id NOT IN (
        SELECT m.matchedCommande.id FROM MessageOperateurBrut m
        WHERE m.matchedCommande IS NOT NULL
    )
    ORDER BY c.createdAt ASC
    """)
    List<Commande> findMatchingCandidatesAnyPhase(
            @Param("device") Device device,
            @Param("operateur") Operateur operateur,
            @Param("amount") BigDecimal amount,
            @Param("windowStart") Instant windowStart
    );

    List<MessageOperateurBrut> findByProcessingStatusAndIaRetryCountLessThan(
            MessageProcessingStatus status, int maxRetries
    );
}