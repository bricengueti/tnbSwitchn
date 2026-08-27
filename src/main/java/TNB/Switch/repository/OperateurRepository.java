package TNB.Switch.repository;

import TNB.Switch.entity.Operateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperateurRepository extends JpaRepository<Operateur, UUID> {

    // Utilisé par OperateurSeeder (idempotence du seed) et par le moteur
    // de routage/parsing IA pour résoudre un code_operateur en entité.
    Optional<Operateur> findByCode(String code);

    // Utilisé pour afficher le catalogue d'offres actives côté client
    // (une offre ne doit pas proposer un opérateur désactivé, cf. §7.2).
    List<Operateur> findByActifTrue();

    boolean existsByCode(String code);
}