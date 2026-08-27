package TNB.Switch.mapper;

import TNB.Switch.DTO.response.DeviceResponse;
import TNB.Switch.DTO.response.OperateurSummaryResponse;
import TNB.Switch.entity.Device;
import TNB.Switch.entity.Operateur;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DeviceMapper implements Function<Device, DeviceResponse> {

    private final OperateurSummaryMapper operateurSummaryMapper = new OperateurSummaryMapper();

    @Override
    public DeviceResponse apply(Device device) {
        if (device == null) {
            return null;
        }

        List<OperateurSummaryResponse> supportedOperators = device.getSupportedOperators() != null
                ? device.getSupportedOperators().stream()
                .map(operateurSummaryMapper)
                .collect(Collectors.toList())
                : List.of();

        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getPairingCode(),
                device.getStatus(),
                supportedOperators,
                device.getLastHeartbeat()
        );
    }
}