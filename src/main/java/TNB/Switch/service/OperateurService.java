package TNB.Switch.service;

import TNB.Switch.DTO.request.CreateOperateurRequest;
import TNB.Switch.DTO.request.UpdateOperateurRequest;
import TNB.Switch.DTO.request.UpdateWithdrawalTemplateRequest;
import TNB.Switch.DTO.response.OperateurResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.CommandTemplate;
import TNB.Switch.entity.Operateur;
import TNB.Switch.entity.PhonePrefix;
import TNB.Switch.exeption.InvalidOfferConfigurationException;
import TNB.Switch.exeption.PrefixAlreadyExistsException;
import TNB.Switch.exeption.ResourceNotFoundException;
import TNB.Switch.mapper.OperateurMapper;
import TNB.Switch.mapper.OperateurSummaryMapper;
import TNB.Switch.repository.OperateurRepository;
import TNB.Switch.repository.PhonePrefixRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de gestion des opérateurs (MTN, Orange, Camtel, Yoomee...).
 *
 * =====================================================================
 *                    OPERATEUR SERVICE
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  CRÉATION D'UN OPÉRATEUR                                           │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  createOperateur(request)                                          │
 *  │  → Vérifier code unique                                          │
 *  │  → Créer Operateur avec CommandTemplate                           │
 *  │  → Ajouter les préfixes téléphoniques                            │
 *  │  → Retourner OperateurResponse                                   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  MISE À JOUR DU TEMPLATE DE RETRAIT                               │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  updateWithdrawalTemplate(request)                                │
 *  │  → Vérifier opérateur existant                                   │
 *  │  → Mettre à jour le template                                     │
 *  │  → Retourner OperateurResponse                                   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  GESTION DES PRÉFIXES                                              │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  addPhonePrefix(id, prefix, description)                         │
 *  │  removePhonePrefix(id, prefixId)                                 │
 *  │  getPhonePrefixes(id) → Liste des préfixes                       │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  LECTURE                                                           │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  getOperateur(id) → Détail complet                               │
 *  │  getSummary(id) → Résumé (pour listes)                           │
 *  │  getAllOperateurs() → Tous les opérateurs                        │
 *  │  getActiveOperateurs() → Uniquement les actifs                   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  ACTIVATION / DÉSACTIVATION                                       │
 *  │  ───────────────────────────────────────────────────────────────── │
 *  │  activate(id) → actif = true                                     │
 *  │  deactivate(id) → actif = false                                  │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 */
@Service
public class OperateurService {

    private static final Logger log = LoggerFactory.getLogger(OperateurService.class);

    private final OperateurRepository operateurRepository;
    private final PhonePrefixRepository phonePrefixRepository;
    private final OperateurMapper operateurMapper;
    private final OperateurSummaryMapper operateurSummaryMapper;

    public OperateurService(OperateurRepository operateurRepository,
                            PhonePrefixRepository phonePrefixRepository,
                            OperateurMapper operateurMapper,
                            OperateurSummaryMapper operateurSummaryMapper) {
        this.operateurRepository = operateurRepository;
        this.phonePrefixRepository = phonePrefixRepository;
        this.operateurMapper = operateurMapper;
        this.operateurSummaryMapper = operateurSummaryMapper;
    }
    public OperateurSummaryResponse getOperateurSummary(UUID id) {
        Operateur operateur = operateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", id));
        return operateurSummaryMapper.apply(operateur);
    }

    // =====================================================================
    //  CRÉATION
    // =====================================================================

    /**
     * Crée un nouvel opérateur.
     * Le code est unique et immuable après création.
     */
    @Transactional("transactionManager")
    public OperateurResponse createOperateur(CreateOperateurRequest request) {
        // Vérifier l'unicité du code
        if (operateurRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException(
                    "Un opérateur existe déjà avec le code: " + request.code()
            );
        }

        // Vérifier que le template de retrait est renseigné
        if (request.withdrawalTemplateContent() == null || request.withdrawalTemplateContent().isBlank()) {
            throw new InvalidOfferConfigurationException(
                    "Le gabarit de retrait est obligatoire pour créer un opérateur (CDC §7.2)"
            );
        }

        // Créer l'opérateur
        Operateur operateur = new Operateur(
                request.code(),
                request.nom(),
                request.type(),
                new CommandTemplate(request.withdrawalTemplateContent())
        );

        // Ajouter les préfixes si présents
        if (request.phonePrefixes() != null && !request.phonePrefixes().isEmpty()) {
            for (String prefix : request.phonePrefixes()) {
                // Vérifier que le préfixe n'existe pas déjà
                if (phonePrefixRepository.existsByPrefix(prefix)) {
                    throw new PrefixAlreadyExistsException(
                            "Le préfixe " + prefix + " existe déjà pour un autre opérateur"
                    );
                }
                operateur.addPhonePrefix(prefix, null);
            }
        }

        Operateur saved = operateurRepository.save(operateur);

        log.info("Opérateur créé [{}] : {} ({}) avec {} préfixe(s)",
                saved.getId(), saved.getCode(), saved.getNom(),
                saved.getPhonePrefixes().size());

        return operateurMapper.apply(saved);
    }

    // =====================================================================
    //  MISE À JOUR
    // =====================================================================

    /**
     * Met à jour un opérateur existant.
     * Le code ne peut pas être modifié (il est immutable).
     */
    @Transactional("transactionManager")
    public OperateurResponse updateOperateur(UpdateOperateurRequest request) {
        Operateur operateur = findOperateur(request.id());

        // Mise à jour des champs modifiables
        if (request.nom() != null && !request.nom().isBlank()) {
            operateur.setNom(request.nom());
        }

        if (request.type() != null) {
            operateur.setType(request.type());
        }

        if (request.withdrawalTemplateContent() != null && !request.withdrawalTemplateContent().isBlank()) {
            operateur.setWithdrawalCommandTemplate(
                    new CommandTemplate(request.withdrawalTemplateContent())
            );
        }

        log.info("Opérateur [{}] mis à jour", operateur.getId());

        return operateurMapper.apply(operateur);
    }

    /**
     * Met à jour uniquement le template de retrait d'un opérateur.
     * Méthode dédiée pour simplifier les appels administratifs.
     */
    @Transactional("transactionManager")
    public OperateurResponse updateWithdrawalTemplate(UpdateWithdrawalTemplateRequest request) {
        Operateur operateur = findOperateur(request.operateurId());

        if (request.withdrawalTemplateContent() == null || request.withdrawalTemplateContent().isBlank()) {
            throw new InvalidOfferConfigurationException(
                    "Le gabarit de retrait ne peut pas être vide"
            );
        }

        operateur.setWithdrawalCommandTemplate(
                new CommandTemplate(request.withdrawalTemplateContent())
        );

        log.info("Template de retrait mis à jour pour opérateur [{}]", operateur.getId());

        return operateurMapper.apply(operateur);
    }

    // =====================================================================
    //  GESTION DES PRÉFIXES
    // =====================================================================

    /**
     * Ajoute un préfixe téléphonique à un opérateur.
     */
    @Transactional("transactionManager")
    public OperateurResponse addPhonePrefix(UUID operateurId, String prefix, String description) {
        Operateur operateur = findOperateur(operateurId);

        // Vérifier que le préfixe n'existe pas déjà
        if (phonePrefixRepository.existsByPrefix(prefix)) {
            throw new PrefixAlreadyExistsException(
                    "Le préfixe " + prefix + " existe déjà pour un autre opérateur"
            );
        }

        operateur.addPhonePrefix(prefix, description);
        Operateur saved = operateurRepository.save(operateur);

        log.info("Préfixe [{}] ajouté à l'opérateur [{}]", prefix, operateur.getCode());

        return operateurMapper.apply(saved);
    }

    /**
     * Supprime un préfixe téléphonique d'un opérateur.
     */
    @Transactional("transactionManager")
    public OperateurResponse removePhonePrefix(UUID operateurId, UUID prefixId) {
        Operateur operateur = findOperateur(operateurId);

        PhonePrefix prefix = phonePrefixRepository.findById(prefixId)
                .orElseThrow(() -> new ResourceNotFoundException("PhonePrefix", prefixId));

        // Vérifier que le préfixe appartient bien à l'opérateur
        if (!prefix.getOperateur().getId().equals(operateurId)) {
            throw new IllegalArgumentException(
                    "Le préfixe n'appartient pas à cet opérateur"
            );
        }

        operateur.removePhonePrefix(prefix);
        phonePrefixRepository.delete(prefix);

        log.info("Préfixe [{}] supprimé de l'opérateur [{}]",
                prefix.getPrefix(), operateur.getCode());

        return operateurMapper.apply(operateur);
    }

    /**
     * Récupère la liste des préfixes d'un opérateur.
     */
    public List<String> getPhonePrefixes(UUID operateurId) {
        Operateur operateur = findOperateur(operateurId);
        return operateur.getPhonePrefixes().stream()
                .map(PhonePrefix::getPrefix)
                .collect(Collectors.toList());
    }

    /**
     * Récupère la liste des préfixes actifs d'un opérateur.
     */
    public List<String> getActivePhonePrefixes(UUID operateurId) {
        Operateur operateur = findOperateur(operateurId);
        return operateur.getActivePrefixes();
    }

    // =====================================================================
    //  ACTIVATION / DÉSACTIVATION
    // =====================================================================

    /**
     * Active un opérateur (le rend disponible pour les offres).
     */
    @Transactional("transactionManager")
    public OperateurResponse activate(UUID id) {
        Operateur operateur = findOperateur(id);
        operateur.activer();
        log.info("Opérateur [{}] activé", id);
        return operateurMapper.apply(operateur);
    }

    /**
     * Désactive un opérateur (le rend indisponible pour les offres).
     * Les offres existantes restent en base mais ne seront plus affichées.
     */
    @Transactional("transactionManager")
    public OperateurResponse deactivate(UUID id) {
        Operateur operateur = findOperateur(id);
        operateur.desactiver();
        log.info("Opérateur [{}] désactivé", id);
        return operateurMapper.apply(operateur);
    }

    /**
     * Récupère un opérateur par son ID (entité brute).
     * ✅ Méthode pour les services internes.
     */
    public Operateur getOperateurById(UUID id) {
        return operateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", id));
    }

    /**
     * Récupère un opérateur par son ID (détail complet).
     */
    public OperateurResponse getOperateur(UUID id) {
        Operateur operateur = getOperateurById(id);
        return operateurMapper.apply(operateur);
    }

    /**
     * Récupère un opérateur par son code (utilisé par le RoutingService).
     */
    public Operateur getOperateurByCode(String code) {
        return operateurRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", code));
    }

    /**
     * Récupère tous les opérateurs actifs.
     */
    public List<OperateurResponse> getActiveOperateurs() {
        return operateurRepository.findByActifTrue().stream()
                .map(operateurMapper)
                .collect(Collectors.toList());
    }

    /**
     * Récupère tous les opérateurs (admin).
     */
    public List<OperateurResponse> getAllOperateurs() {
        return operateurRepository.findAll().stream()
                .map(operateurMapper)
                .collect(Collectors.toList());
    }

    // =====================================================================
    //  VALIDATION
    // =====================================================================

    /**
     * Vérifie si un numéro appartient à un opérateur.
     */
    public boolean isPhoneNumberValidForOperateur(String phoneNumber, UUID operateurId) {
        Operateur operateur = findOperateur(operateurId);
        return operateur.ownsPhoneNumber(phoneNumber);
    }

    /**
     * Trouve l'opérateur propriétaire d'un numéro de téléphone.
     */
    public Operateur findOperateurByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        String cleaned = normalizePhoneNumber(phoneNumber);

        return operateurRepository.findAll().stream()
                .filter(op -> op.ownsPhoneNumber(cleaned))
                .findFirst()
                .orElse(null);
    }

    /**
     * Normalise un numéro de téléphone.
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        return phoneNumber.replaceAll("[^0-9]", "");
    }

    // =====================================================================
    //  MÉTHODE INTERNE
    // =====================================================================

    /**
     * Trouve un opérateur par son ID ou lève une exception.
     */
    private Operateur findOperateur(UUID id) {
        return operateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operateur", id));
    }
}