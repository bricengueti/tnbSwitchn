package TNB.Switch.mapper;

import TNB.Switch.DTO.request.CreateOperateurRequest;
import TNB.Switch.DTO.response.OperateurResponse;
import TNB.Switch.entity.CommandTemplate;
import TNB.Switch.entity.Operateur;
import TNB.Switch.entity.PhonePrefix;
import TNB.Switch.enums.OfferType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class OperateurMapper implements Function<Operateur, OperateurResponse> {

    public Operateur toEntity(CreateOperateurRequest request) {
        Operateur operateur = new Operateur(
                request.code(),
                request.nom(),
                request.type()
        );

        // Template de retrait
        if (request.withdrawalTemplateContent() != null) {
            operateur.setWithdrawalCommandTemplate(
                    new CommandTemplate(request.withdrawalTemplateContent())
            );
        }

        // Templates d'exécution par type d'offre
        if (request.executionTemplatesContent() != null) {
            // ✅ Correction : utiliser HashMap au lieu de var
            Map<OfferType, CommandTemplate> templates = new HashMap<>();
            request.executionTemplatesContent().forEach((offerType, content) -> {
                if (content != null && !content.isBlank()) {
                    templates.put(offerType, new CommandTemplate(content));
                }
            });
            operateur.setExecutionCommandTemplates(templates);
        }

        // Préfixes téléphoniques
        if (request.phonePrefixes() != null) {
            for (String prefix : request.phonePrefixes()) {
                if (prefix != null && !prefix.isBlank()) {
                    operateur.addPhonePrefix(prefix, null);
                }
            }
        }

        return operateur;
    }

    @Override
    public OperateurResponse apply(Operateur operateur) {
        // Construire la Map des templates d'exécution
        // ✅ Correction : utiliser HashMap au lieu de EnumMap
        Map<OfferType, String> executionTemplates = new HashMap<>();
        if (operateur.getExecutionCommandTemplates() != null) {
            operateur.getExecutionCommandTemplates().forEach((offerType, template) -> {
                if (template != null && template.getContent() != null) {
                    executionTemplates.put(offerType, template.getContent());
                }
            });
        }

        // Récupérer la liste des préfixes
        List<String> prefixes = operateur.getPhonePrefixes().stream()
                .map(PhonePrefix::getPrefix)
                .toList();

        return new OperateurResponse(
                operateur.getId(),
                operateur.getCode(),
                operateur.getNom(),
                operateur.getType(),
                operateur.isActif(),
                prefixes,
                operateur.getWithdrawalCommandTemplate() != null
                        ? operateur.getWithdrawalCommandTemplate().getContent()
                        : null,
                executionTemplates
        );
    }
}