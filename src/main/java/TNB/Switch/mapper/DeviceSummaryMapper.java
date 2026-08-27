package TNB.Switch.mapper;

import TNB.Switch.DTO.response.DeviceSummaryResponse;
import TNB.Switch.entity.Device;

import java.util.function.Function;

public class DeviceSummaryMapper implements Function<Device, DeviceSummaryResponse> {

    @Override
    public DeviceSummaryResponse apply(Device device) {
        if (device == null) {
            return null;
        }

        return new DeviceSummaryResponse(
                device.getId(),
                device.getName(),
                device.getStatus()
        );
    }
}