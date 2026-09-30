package com.enterpriselab.api.workorders.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.enterpriselab.api.workorders.domain.WorkOrderFilter;

import jakarta.persistence.criteria.Predicate;

/**
 * Criterios del listado: suma un predicado por cada filtro presente y los combina
 * con {@code AND} (REQ-11). Se arma con {@code Specification} y no con JPQL porque
 * Postgres no infiere el tipo de un parámetro nulo en {@code lower(:param)}.
 */
final class WorkOrderSpecifications {

    static final char ESCAPE = '\\';

    private WorkOrderSpecifications() {
    }

    static Specification<WorkOrderEntity> matching(WorkOrderFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.titleText() != null) {
                String pattern = "%" + escape(filter.titleText().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.like(builder.lower(root.get("title")), pattern, ESCAPE));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status().toValue()));
            }
            if (filter.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), filter.priority().toValue()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** {@code %}, {@code _} y la propia barra se buscan literalmente, no como comodines (REQ-7). */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
