package TNB.Switch.service;

import TNB.Switch.entity.MessageOperateurBrut;
import TNB.Switch.enums.MessageProcessingStatus;
import TNB.Switch.repository.MessageOperateurBrutRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

import static TNB.Switch.specification.MessageOperateurBrutSpecifications.*;


@Service
public class MessageOperateurBrutSearchService {

    private final MessageOperateurBrutRepository repository;

    public MessageOperateurBrutSearchService(MessageOperateurBrutRepository repository) {
        this.repository = repository;
    }

    public Page<MessageOperateurBrut> search(
            MessageProcessingStatus status, UUID deviceId, UUID operateurId,
            Instant from, Instant to, Pageable pageable) {

        Specification<MessageOperateurBrut> spec = Specification
                .where(hasProcessingStatus(status))
                .and(hasDevice(deviceId))
                .and(hasOperateur(operateurId))
                .and(receivedBetween(from, to));

        return repository.findAll(spec, pageable);
    }
}