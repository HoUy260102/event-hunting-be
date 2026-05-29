package com.example.event.specification;

import com.example.event.constant.ReservationStatus;
import com.example.event.entity.Reservation;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class ReservationSpecification {

    public static Specification<Reservation> fetchShowAndEvent() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("show", JoinType.LEFT)
                        .fetch("event", JoinType.LEFT);
                root.fetch("user", JoinType.LEFT);
                root.fetch("payment", JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }

    public static Specification<Reservation> hasId(String id) {
        return (root, query, cb) -> {
            if (id == null || id.trim().isEmpty()) return null;
            String cleanId = id.trim();
            return cb.or(
                cb.equal(root.get("id"), cleanId),
                cb.equal(cb.upper(root.get("code")), cleanId.toUpperCase())
            );
        };
    }

    public static Specification<Reservation> hasCode(String code) {
        return (root, query, cb) ->
                code == null ? null : cb.equal(cb.upper(root.get("code")), code.trim().toUpperCase());
    }

    public static Specification<Reservation> hasShowId(String id) {
        return (root, query, cb) ->
                id == null ? null : cb.equal(root.get("show").get("id"), id);
    }

    public static Specification<Reservation> hasEventId(String id) {
        return (root, query, cb) -> {
            if (id == null) return null;
            return cb.equal(root.get("show").get("event").get("id"), id);
        };
    }

    public static Specification<Reservation> hasEventOwnerId(String id) {
        return (root, query, cb) -> {
            if (id == null) return null;
            return cb.equal(root.get("show").get("event").get("user").get("id"), id);
        };
    }

    public static Specification<Reservation> hasCustomerId(String id) {
        return (root, query, cb) -> {
            if (id == null) return null;
            return cb.equal(root.get("user").get("id"), id);
        };
    }

    public static Specification<Reservation> hasStatus(ReservationStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Reservation> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Reservation> isDeleted() {
        return (root, query, cb) ->
                cb.isNotNull(root.get("deletedAt"));
    }

}
