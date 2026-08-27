package TNB.Switch.DTO.request;

import TNB.Switch.entity.Device;

public record DeviceRegistration(Device device, String plainCredential) {}