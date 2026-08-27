package TNB.Switch.mapper;

import TNB.Switch.DTO.response.HistoriqueStatutResponse;
import TNB.Switch.entity.HistoriqueStatut;

import java.util.function.Function;

public class HistoriqueStatutMapper implements Function<HistoriqueStatut, HistoriqueStatutResponse> {

    @Override
    public HistoriqueStatutResponse apply(HistoriqueStatut historiqueStatut) {
        if (historiqueStatut == null) {
            return null;
        }

        return new HistoriqueStatutResponse(
                historiqueStatut.getId(),
                historiqueStatut.getEntityType(),
                historiqueStatut.getEntityId(),
                historiqueStatut.getStatus(),
                historiqueStatut.getActorId(),
                historiqueStatut.getCreatedAt()
        );
    }
}