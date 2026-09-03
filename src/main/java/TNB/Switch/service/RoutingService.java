package TNB.Switch.service;

import TNB.Switch.entity.Commande;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.FleetBalance;
import TNB.Switch.entity.Operateur;
import TNB.Switch.enums.CommandPhase;
import TNB.Switch.enums.DeviceStatus;
import TNB.Switch.enums.OfferType;
import TNB.Switch.exeption.IllegalStateTransitionException;
import TNB.Switch.exeption.NoAvailableDeviceException;
import TNB.Switch.messaging.CommandDispatcher;
import TNB.Switch.repository.CommandeRepository;
import TNB.Switch.repository.DeviceRepository;
import TNB.Switch.repository.FleetBalanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Moteur de routage — l'agent d'accueil de l'agence tnbSwitch.
 *
 * ANALOGIE GÉNÉRALE : imagine une agence avec plusieurs guichetiers (les
 * Device) et une file de clients à servir (les Commande en attente, sans
 * device assigné). Ce service joue le rôle de l'agent d'accueil : il
 * regarde la file, cherche un guichetier libre et compétent pour la
 * langue/l'opérateur demandé, lui attribue le client, et l'affiche
 * "occupé" immédiatement pour qu'aucun autre agent d'accueil ne lui envoie
 * un second client en même temps.
 *
 * =====================================================================
 *                    ROUTING SERVICE — SCHÉMA COMPLET
 * =====================================================================
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │                    CommandRoutingConsumer                          │
 *  │                    handleRouting(event)                            │
 *  └─────────────────────────────┬───────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │                    RoutingService.routeSingleCommand(commande)     │
 *  │                                                                     │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  ÉTAPE 1 : Lister les candidats                             │   │
 *  │  │                                                             │   │
 *  │  │  candidates = deviceRepository.findAvailableByOperateur(   │   │
 *  │  │      DeviceStatus.AVAILABLE, commande.operateur            │   │
 *  │  │  )                                                          │   │
 *  │  │                                                             │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  Device A (AVAILABLE)  ← heartbeat le plus récent  │   │   │
 *  │  │  │  Device B (AVAILABLE)                              │   │   │
 *  │  │  │  Device C (AVAILABLE)                              │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                                │                                  │
 *  │                                ▼                                  │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  ÉTAPE 2 : Parcourir les candidats                         │   │
 *  │  │                                                             │   │
 *  │  │  for (Device candidate : candidates) {                     │   │
 *  │  │      assigned = tryAssign(commande, candidate)            │   │
 *  │  │      if (assigned.isPresent()) return assigned            │   │
 *  │  │  }                                                         │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  │                                │                                  │
 *  │                                ▼                                  │
 *  │  ┌─────────────────────────────────────────────────────────────┐   │
 *  │  │  ÉTAPE 3 : tryAssign(commande, candidate)                  │   │
 *  │  │                                                             │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  Vérifier le solde (FleetBalance)                   │   │   │
 *  │  │  │  if (phase == EXECUTION) {                         │   │   │
 *  │  │  │      if (!hasSufficientBalance()) return empty     │   │   │
 *  │  │  │  }                                                  │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  │                                │                          │   │
 *  │  │                                ▼                          │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  Verrouiller le device                              │   │   │
 *  │  │  │  Device locked = deviceService.transitionStatus(   │   │   │
 *  │  │  │      candidate.id, DeviceStatus.HOLDS              │   │   │
 *  │  │  │  )                                                  │   │   │
 *  │  │  │  ← Verrouillage pessimiste (findByIdForUpdate)    │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  │                                │                          │   │
 *  │  │                                ▼                          │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  Assigner et enregistrer                            │   │   │
 *  │  │  │  commande.setDevice(locked)                        │   │   │
 *  │  │  │  commandeRepository.save(commande)                 │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  │                                │                          │   │
 *  │  │                                ▼                          │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  Envoyer au device via STOMP                        │   │   │
 *  │  │  │  commandDispatcher.dispatch(locked, commande)      │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  │                                │                          │   │
 *  │  │                                ▼                          │   │
 *  │  │  ┌─────────────────────────────────────────────────────┐   │   │
 *  │  │  │  Retourner le device assigné                        │   │   │
 *  │  │  │  return Optional.of(locked)                        │   │   │
 *  │  │  └─────────────────────────────────────────────────────┘   │   │
 *  │  └─────────────────────────────────────────────────────────────┘   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *                                │
 *                                ▼
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  SI AUCUN CANDIDAT DISPONIBLE :                                     │
 *  │  → NoAvailableDeviceException                                      │
 *  │  → CommandRoutingConsumer.@RetryableTopic → retry + backoff       │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 * =====================================================================
 *  LÉGENDE :
 *    ───  = Flux normal
 *    - - - = Flux alternatif (candidat suivant)
 *    ••••  = Flux d'échec (NoAvailableDeviceException)
 * =====================================================================
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final CommandeRepository commandeRepository;
    private final DeviceRepository deviceRepository;
    private final FleetBalanceRepository fleetBalanceRepository;
    private final DeviceService deviceService;
    private final CommandDispatcher commandDispatcher;

    public RoutingService(CommandeRepository commandeRepository,
                          DeviceRepository deviceRepository,
                          FleetBalanceRepository fleetBalanceRepository,
                          DeviceService deviceService,
                          CommandDispatcher commandDispatcher) {
        this.commandeRepository = commandeRepository;
        this.deviceRepository = deviceRepository;
        this.fleetBalanceRepository = fleetBalanceRepository;
        this.deviceService = deviceService;
        this.commandDispatcher = commandDispatcher;
    }

    // =====================================================================
    //  ROUTAGE
    // =====================================================================

    /**
     * Route UNE commande précise. C'est ici que se joue toute la logique :
     * trouver un guichetier (Device) libre, compétent (supporte
     * l'opérateur demandé), et disposant du "tiroir-caisse" suffisant
     * (FleetBalance) pour honorer la demande — puis l'affecter.
     *
     * ┌─────────────────────────────────────────────────────────────────┐
     * │  ÉTAPE 1 : Lister les candidats AVAILABLE pour l'opérateur    │
     * │  ───────────────────────────────────────────────────────────── │
     * │  candidates = deviceRepository.findAvailableByOperateur(     │
     * │      DeviceStatus.AVAILABLE, commande.operateur             │
     * │  )                                                            │
     * │  Tri : order by lastHeartbeat DESC (le plus récent d'abord)  │
     * │                                                               │
     * │  ÉTAPE 2 : Parcourir les candidats                            │
     * │  ───────────────────────────────────────────────────────────── │
     * │  for (Device candidate : candidates) {                       │
     * │      assigned = tryAssign(commande, candidate)              │
     * │      if (assigned.isPresent()) return assigned              │
     * │  }                                                           │
     * │                                                               │
     * │  ÉTAPE 3 : Aucun candidat → NoAvailableDeviceException       │
     * └─────────────────────────────────────────────────────────────────┘
     */
    @Transactional("transactionManager")
    public Device routeSingleCommand(Commande commande) {
        Operateur operateur = commande.getOperateur();

        // ÉTAPE 1 — dresser la liste des guichetiers a priori disponibles
        // et compétents pour cet opérateur (triés par heartbeat le plus
        // récent : on privilégie le guichetier le plus "réveillé", donc
        // le plus probablement fiable en ce moment).
        List<Device> candidates = deviceRepository.findAvailableByOperateur(
                DeviceStatus.AVAILABLE, operateur
        );

        if (candidates.isEmpty()) {
            log.warn("Aucun device AVAILABLE pour l'opérateur [{}]", operateur.getCode());
            throw new NoAvailableDeviceException(operateur.getCode());
        }

        log.debug("{} candidats pour l'opérateur [{}]", candidates.size(), operateur.getCode());

        // ÉTAPE 2 — parcourir les candidats un par un. Comme deux agents
        // d'accueil peuvent regarder la même liste au même instant, le
        // guichetier en tête de liste peut déjà avoir été pris par un
        // autre agent entre le moment où on l'a vu "libre" et le moment
        // où on essaie de le verrouiller. On retente alors avec le
        // candidat suivant plutôt que d'abandonner tout de suite —
        // exactement comme on se tournerait vers le guichet d'à côté si
        // celui qu'on visait vient d'être pris.
        for (Device candidate : candidates) {
            Optional<Device> assigned = tryAssign(commande, candidate);
            if (assigned.isPresent()) {
                return assigned.get();
            }
        }

        // ÉTAPE 3 — aucun candidat n'a pu être verrouillé
        log.warn("Aucun device disponible pour l'opérateur [{}] (tous pris ou solde insuffisant)",
                operateur.getCode());
        throw new NoAvailableDeviceException(operateur.getCode());
    }

    // =====================================================================
    //  TENTATIVE D'ASSIGNATION
    // =====================================================================

    /**
     * Tentative d'affectation sur UN guichetier précis. Retourne un
     * Optional vide (plutôt que de lever une exception) si ce candidat
     * précis n'a pas pu être verrouillé ou n'a pas les fonds suffisants —
     * ça permet à l'appelant d'essayer le candidat suivant sans que ce
     * soit un vrai échec du processus global.
     *
     * ┌─────────────────────────────────────────────────────────────────┐
     * │  tryAssign(commande, candidate)                               │
     * │                                                                 │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  1. Vérifier le solde (si phase == EXECUTION)          │   │
     * │  │     if (!hasSufficientBalance()) return empty          │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  2. Verrouiller le device                               │   │
     * │  │     Device locked = deviceService.transitionStatus(    │   │
     * │  │         candidate.id, DeviceStatus.HOLDS               │   │
     * │  │     )                                                   │   │
     * │  │     ← Verrouillage pessimiste (findByIdForUpdate)     │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  3. Assigner et enregistrer                             │   │
     * │  │     commande.setDevice(locked)                         │   │
     * │  │     commandeRepository.save(commande)                  │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  4. Envoyer au device via STOMP                         │   │
     * │  │     commandDispatcher.dispatch(locked, commande)       │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  5. Retourner le device assigné                         │   │
     * │  │     return Optional.of(locked)                         │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * └─────────────────────────────────────────────────────────────────┘
     */
    private Optional<Device> tryAssign(Commande commande, Device candidate) {
        // 1. Vérification du "tiroir-caisse" AVANT de verrouiller le
        // guichetier : inutile de bloquer un device si, de toute façon,
        // il n'a pas les fonds pour honorer la commande — autant laisser
        // ce guichetier libre pour une commande qu'il peut réellement traiter.
        if (commande.getPhase() == CommandPhase.EXECUTION
                && !hasSufficientBalance(candidate, commande)) {
            log.debug("Device [{}] écarté : solde insuffisant pour la commande [{}]",
                    candidate.getId(), commande.getId());
            return Optional.empty();
        }

        try {
            // 2. C'est ici que le verrouillage pessimiste entre en jeu (dans
            // DeviceService.transitionStatus, via findByIdForUpdate) :
            // AVANT que cet appel ne retourne, aucun autre thread ne peut
            // voir ce même device et tenter de le verrouiller en même
            // temps — comme une porte de guichet qui se ferme à clé dès
            // qu'on y entre, empêchant quiconque d'autre d'y entrer aussi.
            Device locked = deviceService.transitionStatus(candidate.getId(), DeviceStatus.HOLDS);

            // 3. Assigner et enregistrer
            commande.setDevice(locked);
            commandeRepository.save(commande);

            // 4. Envoyer au device via STOMP
            commandDispatcher.dispatch(locked, commande);

            // 5. Retourner le device assigné
            log.info("Commande [{}] affectée au device [{}]", commande.getId(), locked.getId());
            return Optional.of(locked);

        } catch (IllegalStateTransitionException e) {
            // Ce candidat vient d'être pris par un autre thread entre le
            // moment où on l'a vu AVAILABLE (étape 1) et notre tentative
            // de verrouillage — pas une erreur, juste "ce guichet vient de
            // fermer, essayons le suivant".
            log.debug("Device [{}] déjà pris entre-temps, candidat suivant", candidate.getId());
            return Optional.empty();
        }
    }

    // =====================================================================
    //  VÉRIFICATION DU SOLDE
    // =====================================================================

    /**
     * Vérifie que le device a de quoi honorer la commande d'EXÉCUTION —
     * comme vérifier que le tiroir-caisse du guichetier contient assez
     * d'argent avant de lui envoyer un client qui veut retirer une
     * certaine somme.
     *
     * ┌─────────────────────────────────────────────────────────────────┐
     * │  hasSufficientBalance(device, commande)                       │
     * │                                                                 │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  offerType = commande.transaction.offer.type           │   │
     * │  │  amount = commande.transaction.amount                  │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  balance = fleetBalanceRepository                      │   │
     * │  │      .findByDeviceAndOperateur(device, operateur)     │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  available = switch (offerType) {                      │   │
     * │  │      EXCHANGE_MO → balance.walletBalance              │   │
     * │  │      CREDIT/DATA  → balance.creditBalance             │   │
     * │  │  }                                                     │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                  │
     * │                              ▼                                  │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │  return available >= amount                            │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * └─────────────────────────────────────────────────────────────────┘
     */
    private boolean hasSufficientBalance(Device device, Commande commande) {
        BigDecimal amount = commande.getTransaction().getAmount();
        Operateur operateur = commande.getOperateur();
        OfferType offerType = commande.getTransaction().getOffer().getType();

        Optional<FleetBalance> balance = fleetBalanceRepository.findByDeviceAndOperateur(device, operateur);
        if (balance.isEmpty()) {
            log.debug("Aucun FleetBalance pour device [{}] / opérateur [{}]",
                    device.getId(), operateur.getCode());
            return false;
        }

        BigDecimal available = offerType == OfferType.EXCHANGE_MO
                ? balance.get().getWalletBalance()
                : balance.get().getCreditBalance();

        boolean sufficient = available.compareTo(amount) >= 0;

        if (!sufficient) {
            log.debug("Solde insuffisant pour device [{}] : disponible={}, demandé={}",
                    device.getId(), available, amount);
        }

        return sufficient;
    }
}