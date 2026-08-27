package TNB.Switch.mapper;

import TNB.Switch.DTO.response.FleetMovementResponse;
import TNB.Switch.entity.FleetMovement;

import java.util.function.Function;

public class FleetMovementMapper implements Function<FleetMovement, FleetMovementResponse> {

    @Override
    public FleetMovementResponse apply(FleetMovement fleetMovement) {
        if (fleetMovement == null) {
            return null;
        }

        return new FleetMovementResponse(
                fleetMovement.getId(),
                fleetMovement.getFleetBalance().getId(),
                fleetMovement.getAmount(),
                fleetMovement.getReason(),
                fleetMovement.getJustification(),
                fleetMovement.getTransactionId(),
                fleetMovement.getCreatedAt()
        );
    }
}