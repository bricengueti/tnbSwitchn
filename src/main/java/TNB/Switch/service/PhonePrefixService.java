package TNB.Switch.service;

import TNB.Switch.entity.Operateur;
import TNB.Switch.entity.PhonePrefix;
import TNB.Switch.exeption.PhonePrefixNotFoundException;
import TNB.Switch.exeption.PrefixAlreadyExistsException;
import TNB.Switch.repository.PhonePrefixRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PhonePrefixService {

    private static final Logger log = LoggerFactory.getLogger(PhonePrefixService.class);

    private final PhonePrefixRepository phonePrefixRepository;
    private final OperateurService operateurService;

    public PhonePrefixService(PhonePrefixRepository phonePrefixRepository,
                              OperateurService operateurService) {
        this.phonePrefixRepository = phonePrefixRepository;
        this.operateurService = operateurService;
    }

    /**
     * Crée un nouveau préfixe pour un opérateur.
     */
    @Transactional
    public PhonePrefix createPrefix(UUID operateurId, String prefix, String description) {
        // Vérifier que le préfixe n'existe pas déjà
        if (phonePrefixRepository.existsByPrefix(prefix)) {
            throw new PrefixAlreadyExistsException("Le préfixe " + prefix + " existe déjà");
        }

        Operateur operateur = operateurService.getOperateurById(operateurId);

        PhonePrefix phonePrefix = new PhonePrefix(prefix, operateur, description);
        PhonePrefix saved = phonePrefixRepository.save(phonePrefix);

        log.info("Préfixe [{}] ajouté à l'opérateur [{}]", prefix, operateur.getCode());
        return saved;
    }

    /**
     * Récupère tous les préfixes.
     */
    public List<PhonePrefix> getAllPrefixes() {
        return phonePrefixRepository.findAll();
    }

    /**
     * Récupère tous les préfixes actifs.
     */
    public List<PhonePrefix> getActivePrefixes() {
        return phonePrefixRepository.findByActiveTrue();
    }

    /**
     * Récupère les préfixes d'un opérateur.
     */
    public List<PhonePrefix> getPrefixesByOperateur(UUID operateurId) {
        Operateur operateur = operateurService.getOperateurById(operateurId);
        return phonePrefixRepository.findByOperateur(operateur);
    }

    /**
     * Récupère les préfixes actifs d'un opérateur.
     */
    public List<PhonePrefix> getActivePrefixesByOperateur(UUID operateurId) {
        Operateur operateur = operateurService.getOperateurById(operateurId);
        return phonePrefixRepository.findByOperateurAndActiveTrue(operateur);
    }

    /**
     * Récupère un préfixe par son ID.
     */
    public PhonePrefix getPrefixById(UUID id) {
        return phonePrefixRepository.findById(id)
                .orElseThrow(() -> new PhonePrefixNotFoundException("Préfixe non trouvé avec l'ID: " + id));
    }

    /**
     * Récupère un préfixe par sa valeur.
     */
    public PhonePrefix getPrefixByValue(String prefix) {
        return phonePrefixRepository.findByPrefix(prefix)
                .orElseThrow(() -> new PhonePrefixNotFoundException("Préfixe non trouvé: " + prefix));
    }

    /**
     * Met à jour un préfixe.
     */
    @Transactional
    public PhonePrefix updatePrefix(UUID id, String prefix, String description, Boolean active) {
        PhonePrefix phonePrefix = getPrefixById(id);

        if (prefix != null && !prefix.isBlank()) {
            // Vérifier que le nouveau préfixe n'existe pas déjà (sauf pour le même ID)
            if (!phonePrefix.getPrefix().equals(prefix) &&
                    phonePrefixRepository.existsByPrefix(prefix)) {
                throw new PrefixAlreadyExistsException("Le préfixe " + prefix + " existe déjà");
            }
            phonePrefix.setPrefix(prefix);
        }

        if (description != null) {
            phonePrefix.setDescription(description);
        }

        if (active != null) {
            phonePrefix.setActive(active);
        }

        PhonePrefix updated = phonePrefixRepository.save(phonePrefix);
        log.info("Préfixe [{}] mis à jour", updated.getPrefix());
        return updated;
    }

    /**
     * Active un préfixe.
     */
    @Transactional
    public PhonePrefix activatePrefix(UUID id) {
        PhonePrefix phonePrefix = getPrefixById(id);
        phonePrefix.activer();
        log.info("Préfixe [{}] activé", phonePrefix.getPrefix());
        return phonePrefixRepository.save(phonePrefix);
    }

    /**
     * Désactive un préfixe.
     */
    @Transactional
    public PhonePrefix deactivatePrefix(UUID id) {
        PhonePrefix phonePrefix = getPrefixById(id);
        phonePrefix.desactiver();
        log.info("Préfixe [{}] désactivé", phonePrefix.getPrefix());
        return phonePrefixRepository.save(phonePrefix);
    }

    /**
     * Supprime un préfixe (physiquement).
     */
    @Transactional
    public void deletePrefix(UUID id) {
        PhonePrefix phonePrefix = getPrefixById(id);
        phonePrefixRepository.delete(phonePrefix);
        log.info("Préfixe [{}] supprimé", phonePrefix.getPrefix());
    }

    /**
     * Supprime tous les préfixes d'un opérateur.
     */
    @Transactional
    public void deleteAllPrefixesByOperateur(UUID operateurId) {
        Operateur operateur = operateurService.getOperateurById(operateurId);
        List<PhonePrefix> prefixes = phonePrefixRepository.findByOperateur(operateur);
        phonePrefixRepository.deleteAll(prefixes);
        log.info("Tous les préfixes de l'opérateur [{}] supprimés", operateur.getCode());
    }

    /**
     * Vérifie si un préfixe est valide pour un opérateur.
     */
    public boolean isValidPrefixForOperateur(UUID operateurId, String prefix) {
        Operateur operateur = operateurService.getOperateurById(operateurId);
        return phonePrefixRepository.isValidPrefixForOperateur(operateur, prefix);
    }

    /**
     * Compte les préfixes actifs d'un opérateur.
     */
    public long countActivePrefixesByOperateur(UUID operateurId) {
        Operateur operateur = operateurService.getOperateurById(operateurId);
        return phonePrefixRepository.countActiveByOperateur(operateur);
    }
}