package TNB.Switch.DTO.response;


import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Enveloppe standard pour toute réponse paginée — évite d'exposer
 * directement l'objet Page de Spring Data (couplage indésirable à
 * l'implémentation) et normalise le format de réponse API.
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}