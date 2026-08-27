package TNB.Switch.DTO.response;


import TNB.Switch.enums.DeviceStatus;

import java.util.UUID;

public record DeviceSummaryResponse(UUID id, String name, DeviceStatus status) {}