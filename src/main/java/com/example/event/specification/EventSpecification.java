package com.example.event.specification;

import com.example.event.constant.EventStatus;
import com.example.event.entity.Event;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class EventSpecification {
    public static Specification<Event> hasName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Event> hasLocation(String location) {
        return (root, query, cb) ->
                location == null ? null : cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%");
    }

    public static Specification<Event> hasOrganizerName(String organizerName) {
        return (root, query, cb) ->
                organizerName == null ? null : cb.like(cb.lower(root.get("organizerName")), "%" + organizerName.toLowerCase() + "%");
    }

    public static Specification<Event> hasId(String id) {
        return (root, query, cb) ->
                id == null ? null : cb.equal(root.get("id"), id);
    }

    public static Specification<Event> hasIdIn(List<String> ids) {
        return (root, query, cb) -> {
            if (ids == null) {
                return null;
            }
            if (ids.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("id").in(ids);
        };
    }

    public static Specification<Event> hasProvinceId(String id) {
        return (root, query, cb) -> {
            if (id == null || id.isEmpty()) {
                return null;
            }
            return cb.equal(root.get("province").get("id"), id);
        };
    }

    public static Specification<Event> hasCategoryId(String id) {
        return (root, query, cb) -> {
            if (id == null || id.isEmpty()) {
                return null;
            }
            return cb.equal(root.get("category").get("id"), id);
        };
    }

    public static Specification<Event> hasCategoryIds(List<String> categoryIds) {
        return (root, query, cb) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return null;
            }
            return root.get("category").get("id").in(categoryIds);
        };
    }

    public static Specification<Event> orderByStatusAndDate(LocalDateTime now) {
        return (root, query, cb) -> {
            Expression<Integer> statusOrder = cb.<Integer>selectCase()
                    .when(cb.greaterThanOrEqualTo(root.get("endTime"), now), 0)
                    .otherwise(1)
                    .as(Integer.class);
            query.orderBy(
                    cb.asc(statusOrder),
                    cb.asc(root.get("startTime"))
            );
            return null;
        };
    }

    public static Specification<Event> hasNextId(String nextId) {
        return (root, query, cb) -> nextId == null ? null :
                cb.greaterThan(root.get("id"), nextId);
    }

    public static Specification<Event> hasMinPrice(Long price) {
        return (root, query, cb) -> price == null ? null :
                cb.greaterThanOrEqualTo(root.get("minPrice"), price);
    }

    public static Specification<Event> isBetweenDates(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            if (startTime == null && endTime == null) {
                return null;
            }
            if (endTime == null) {
                return cb.greaterThanOrEqualTo(root.get("endTime"), startTime);
            }
            if (startTime == null) {
                return cb.lessThanOrEqualTo(root.get("startTime"), endTime);
            }
            return cb.and(
                    cb.lessThanOrEqualTo(root.get("startTime"), endTime),
                    cb.greaterThanOrEqualTo(root.get("endTime"), startTime)
            );
        };
    }

    public static Specification<Event> hasStatus(EventStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Event> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Event> isDeleted() {
        return (root, query, cb) ->
                cb.isNotNull(root.get("deletedAt"));
    }

    private static Specification<Event> isPublicVisible() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EventStatus.PUBLISHED),
                cb.equal(root.get("isDeleted"), false)
        );
    }

    public static Specification<Event> isUpcoming() {
        return isPublicVisible().and((root, query, cb) ->
                cb.greaterThan(root.get("startTime"), LocalDateTime.now())
        );
    }

    public static Specification<Event> isHappening() {
        return isPublicVisible().and((root, query, cb) -> {
            LocalDateTime now = LocalDateTime.now();
            return cb.and(
                    cb.lessThanOrEqualTo(root.get("startTime"), now),
                    cb.greaterThanOrEqualTo(root.get("endTime"), now)
            );
        });
    }

    public static Specification<Event> isFinished() {
        return isPublicVisible().and((root, query, cb) ->
                cb.lessThan(root.get("endTime"), LocalDateTime.now())
        );
    }
}
