package TNB.Switch.controller;

import TNB.Switch.DTO.request.RequestOtpRequest;
import TNB.Switch.DTO.request.ValidateOtpRequest;
import TNB.Switch.DTO.response.AuthResponse;
import TNB.Switch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentification", description = "Gestion de l'authentification par OTP (One-Time Password)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "Demander un OTP",
            description = "Envoie un code OTP par SMS au numéro de téléphone fourni. " +
                    "Si l'utilisateur n'existe pas, il est créé avec le statut PENDING_VERIFICATION. " +
                    "Une limitation de taux (throttling) est appliquée pour éviter les abus."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP envoyé avec succès"),
            @ApiResponse(responseCode = "429", description = "Trop de demandes, veuillez patienter"),
            @ApiResponse(responseCode = "400", description = "Numéro de téléphone invalide")
    })
    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Numéro de téléphone au format international (+237XXXXXXXXX)",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "phoneNumber": "+237658734332"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody RequestOtpRequest request) {
        authService.requestOtp(request.phoneNumber());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Valider un OTP et se connecter",
            description = "Vérifie le code OTP reçu par SMS. Si valide, retourne un token JWT (access + refresh). " +
                    "Si l'utilisateur était en PENDING_VERIFICATION, son compte est activé automatiquement."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Connexion réussie",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Code OTP invalide ou expiré"),
            @ApiResponse(responseCode = "429", description = "Trop de tentatives, veuillez patienter")
    })
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> validateOtp(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Numéro de téléphone et code OTP",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "phoneNumber": "+237658734332",
                                              "code": "123456"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody ValidateOtpRequest request) {
        AuthResponse result = authService.validateOtp(request.phoneNumber(), request.code());
        return ResponseEntity.ok(new AuthResponse(result.tokenPair(), result.isAdmin()));
    }
}
