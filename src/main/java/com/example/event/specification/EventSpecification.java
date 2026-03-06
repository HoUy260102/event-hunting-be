package com.example.event.specification;

import com.example.event.constant.EventStatus;
import com.example.event.entity.Event;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

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
