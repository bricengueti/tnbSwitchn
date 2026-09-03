package TNB.Switch.controller;

import TNB.Switch.DTO.request.CreatePhonePrefixRequest;
import TNB.Switch.DTO.request.UpdatePhonePrefixRequest;
import TNB.Switch.DTO.response.PhonePrefixResponse;
import TNB.Switch.entity.PhonePrefix;
import TNB.Switch.service.PhonePrefixService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/phone-prefixes")
@PreAuthorize("hasRole('ADMIN')")
public class PhonePrefixController {

    private final PhonePrefixService phonePrefixService;

    public PhonePrefixController(PhonePrefixService phonePrefixService) {
        this.phonePrefixService = phonePrefixService;
    }

    /**
     * Crée un nouveau préfixe.
     */
    @PostMapping
    public ResponseEntity<PhonePrefixResponse> createPrefix(
            @Valid @RequestBody CreatePhonePrefixRequest request) {
        PhonePrefix prefix = phonePrefixService.createPrefix(
                request.operateurId(),
                request.prefix(),
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PhonePrefixResponse.fromEntity(prefix));
    }

    /**
     * Récupère tous les préfixes.
     */
    @GetMapping
    public ResponseEntity<List<PhonePrefixResponse>> getAllPrefixes() {
        List<PhonePrefix> prefixes = phonePrefixService.getAllPrefixes();
        return ResponseEntity.ok(prefixes.stream()
                .map(PhonePrefixResponse::fromEntity)
                .toList());
    }

    /**
     * Récupère les préfixes d'un opérateur.
     */
    @GetMapping("/operateur/{operateurId}")
    public ResponseEntity<List<PhonePrefixResponse>> getPrefixesByOperateur(
            @PathVariable UUID operateurId) {
        List<PhonePrefix> prefixes = phonePrefixService.getPrefixesByOperateur(operateurId);
        return ResponseEntity.ok(prefixes.stream()
                .map(PhonePrefixResponse::fromEntity)
                .toList());
    }

    /**
     * Récupère les préfixes actifs d'un opérateur.
     */
    @GetMapping("/operateur/{operateurId}/active")
    public ResponseEntity<List<PhonePrefixResponse>> getActivePrefixesByOperateur(
            @PathVariable UUID operateurId) {
        List<PhonePrefix> prefixes = phonePrefixService.getActivePrefixesByOperateur(operateurId);
        return ResponseEntity.ok(prefixes.stream()
                .map(PhonePrefixResponse::fromEntity)
                .toList());
    }

    /**
     * Récupère un préfixe par son ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PhonePrefixResponse> getPrefixById(@PathVariable UUID id) {
        PhonePrefix prefix = phonePrefixService.getPrefixById(id);
        return ResponseEntity.ok(PhonePrefixResponse.fromEntity(prefix));
    }

    /**
     * Met à jour un préfixe.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PhonePrefixResponse> updatePrefix(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePhonePrefixRequest request) {
        PhonePrefix prefix = phonePrefixService.updatePrefix(
                id,
                request.prefix(),
                request.description(),
                request.active()
        );
        return ResponseEntity.ok(PhonePrefixResponse.fromEntity(prefix));
    }

    /**
     * Active un préfixe.
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<PhonePrefixResponse> activatePrefix(@PathVariable UUID id) {
        PhonePrefix prefix = phonePrefixService.activatePrefix(id);
        return ResponseEntity.ok(PhonePrefixResponse.fromEntity(prefix));
    }

    /**
     * Désactive un préfixe.
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<PhonePrefixResponse> deactivatePrefix(@PathVariable UUID id) {
        PhonePrefix prefix = phonePrefixService.deactivatePrefix(id);
        return ResponseEntity.ok(PhonePrefixResponse.fromEntity(prefix));
    }

    /**
     * Supprime un préfixe.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrefix(@PathVariable UUID id) {
        phonePrefixService.deletePrefix(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Supprime tous les préfixes d'un opérateur.
     */
    @DeleteMapping("/operateur/{operateurId}")
    public ResponseEntity<Void> deleteAllPrefixesByOperateur(@PathVariable UUID operateurId) {
        phonePrefixService.deleteAllPrefixesByOperateur(operateurId);
        return ResponseEntity.noContent().build();
    }
}