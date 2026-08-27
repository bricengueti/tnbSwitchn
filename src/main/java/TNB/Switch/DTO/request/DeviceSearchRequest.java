package TNB.Switch.DTO.request;
import TNB.Switch.enums.DeviceStatus;

import java.util.UUID;

import TNB.Switch.enums.DeviceStatus;

import java.util.UUID;

public record DeviceSearchRequest(DeviceStatus status, UUID operateurId) {}