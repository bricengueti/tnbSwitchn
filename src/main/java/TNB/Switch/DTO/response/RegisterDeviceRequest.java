package TNB.Switch.DTO.response;


import java.util.Set;
import java.util.UUID;

public record RegisterDeviceRequest(String name, Set<UUID> operatorIds) {}