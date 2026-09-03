package TNB.Switch.DTO.request;

import java.util.UUID;

public record UpdateWithdrawalTemplateRequest(
        UUID operateurId,
        String withdrawalTemplateContent
) {}
