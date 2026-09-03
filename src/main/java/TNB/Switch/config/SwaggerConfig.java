package TNB.Switch.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "tnbSwitch API",
                version = "1.0",
                description = "Plateforme camerounaise de recharge crédit/data et d'échange Mobile Money, " +
                        "exécutée via une flotte de devices physiques (v1.6). " +
                        "Authentification JWT requise pour les endpoints protégés.",
                contact = @Contact(
                        name = "Support tnbSwitch",
                        email = "tchongouebricengueti@gmail.com"
                )
        ),
        servers = {
                @Server(url = "/api/v1", description = "Default Server URL")
        }
)
@SecurityScheme(
        name = "Bearer Authentication",
        description = "Utilisez le token JWT dans l'en-tête Authorization au format : Bearer {token}",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class SwaggerConfig {

    @Bean
    public OpenAPI customizeOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication"));
    }
}