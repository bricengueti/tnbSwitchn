package TNB.Switch.DTO.response;

import TNB.Switch.entity.User;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String phoneNumber) {

    public static UserSummaryResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryResponse(
                user.getId(),
                user.getPhoneNumber()
        );
    }
}