package TNB.Switch.DTO.response;

import TNB.Switch.enums.UserAccountStatus;

import java.util.UUID;

public record UserResponse(UUID id, String phoneNumber, UserAccountStatus accountStatus, boolean isAdmin) {}