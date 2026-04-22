package com.example.event.specification;

import com.example.event.constant.TicketStatus;
import com.example.event.entity.Reservation;
import com.example.event.entity.Show;
import com.example.event.entity.Ticket;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TicketSpecification {
    public static Specification<Ticket> hasUserId(String userId) {
        return (root, query, cb) -> {
            if (userId == null || userId.isEmpty()) {
                return null;
            }
            return cb.equal(root.get("user").get("id"), userId);
        };
    }

    public static Specification<Ticket> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Ticket> isDeleted() {
        return (root, query, cb) -> cb.isNotNull(root.get("deletedAt"));
    }

    public static Specification<Ticket> hasOrderBy(LocalDateTime now) {
        return (root, query, cb) -> {
            Join<Ticket, Show> showJoin = root.join("show", JoinType.INNER);
            Expression<Integer> order = cb.<Integer>selectCase()
                    .when(cb.greaterThanOrEqualTo(showJoin.get("startTime"), now), 0)
                    .otherwise(1)
                    .as(Integer.class);
            query.orderBy(
                    cb.asc(order)
            );
            return null;
        };
    }

    public static Specification<Ticket> hasUpcoming() {
        return (root, query, cb) -> {
            return cb.greaterThanOrEqualTo(root.get("show").get("endTime"), LocalDateTime.now());
        };
    }

    public static Specification<Ticket> fetchAll() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("show", JoinType.LEFT);
                root.fetch("event", JoinType.LEFT);
                root.fetch("reservation", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Ticket> fetchReservation() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class) {
                root.fetch("reservation", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Ticket> hasId(String id) {
        return (root, query, cb) -> {
            return (id == null || id.isEmpty()) ? null :
                    cb.equal(root.get("id"), id);
        };
    }

    public static Specification<Ticket> hasReservationId(String id) {
        return (root, query, cb) -> {
            if (id == null || id.isEmpty()) {
                return null;
            }
            Join<Ticket, Reservation> reservationJoin = root.join("reservation", JoinType.INNER);
            return cb.equal(reservationJoin.get("id"), id);
        };
    }

    public static Specification<Ticket> hasShowId(String id) {
        return (root, query, cb) -> {
            if (id == null || id.isEmpty()) {
                return null;
            }
            Join<Ticket, Show> showJoin = root.join("show", JoinType.INNER);
            return cb.equal(showJoin.get("id"), id);
        };
    }

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Ticket> hasFinished() {
        return (root, query, cb) -> {
            return cb.lessThanOrEqualTo(root.get("show").get("endTime"), LocalDateTime.now());
        };
    }
}
