package TNB.Switch.security;

import java.util.UUID;


import java.util.UUID;

/**
 * Représente l'identité de l'acteur authentifié. Un admin est un simple
 * User avec role=ADMIN — l'information est portée par isAdmin, pas par
 * un ActorType séparé.
 */
public record TnbPrincipal(UUID id, ActorType type, boolean isAdmin) {

    public enum ActorType {
        USER, DEVICE, SYSTEM
    }
}