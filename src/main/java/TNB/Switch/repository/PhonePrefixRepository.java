package TNB.Switch.repository;

import TNB.Switch.entity.Operateur;
import TNB.Switch.entity.PhonePrefix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhonePrefixRepository extends JpaRepository<PhonePrefix, UUID> {

    /**
     * Recherche un préfixe par sa valeur.
     */
    Optional<PhonePrefix> findByPrefix(String prefix);

    /**
     * Vérifie si un préfixe existe déjà.
     */
    boolean existsByPrefix(String prefix);

    /**
     * Récupère tous les préfixes d'un opérateur.
     */
    List<PhonePrefix> findByOperateurAndActiveTrue(Operateur operateur);

    /**
     * Récupère tous les préfixes d'un opérateur (y compris inactifs).
     */
    List<PhonePrefix> findByOperateur(Operateur operateur);

    /**
     * Recherche un préfixe par sa valeur et son opérateur.
     */
    Optional<PhonePrefix> findByPrefixAndOperateur(String prefix, Operateur operateur);

    /**
     * Récupère tous les préfixes actifs.
     */
    List<PhonePrefix> findByActiveTrue();

    /**
     * Compte les préfixes par opérateur.
     */
    @Query("SELECT COUNT(p) FROM PhonePrefix p WHERE p.operateur = :operateur AND p.active = true")
    long countActiveByOperateur(@Param("operateur") Operateur operateur);

    /**
     * Vérifie si un préfixe est valide pour un opérateur donné.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM PhonePrefix p WHERE p.operateur = :operateur " +
            "AND p.prefix = :prefix AND p.active = true")
    boolean isValidPrefixForOperateur(@Param("operateur") Operateur operateur,
                                      @Param("prefix") String prefix);
}