package com.example.event.specification;

import com.example.event.constant.VoucherStatus;
import com.example.event.entity.Voucher;
import org.springframework.data.jpa.domain.Specification;

public class VoucherSpecifiation {
    public static Specification<Voucher> hasName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Voucher> hasId(String id) {
        return (root, query, cb) ->
                id == null ? null : cb.equal(root.get("id"), id);
    }

    public static Specification<Voucher> hasCode(String code) {
        return (root, query, cb) ->
                code == null ? null : cb.equal(root.get("code"), code);
    }

    public static Specification<Voucher> hasShowId(String id) {
        return (root, query, cb) ->
                id == null ? null : cb.equal(root.get("show").get("id"), id);
    }

    public static Specification<Voucher> hasStatus(VoucherStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Voucher> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Voucher> isDeleted() {
        return (root, query, cb) ->
                cb.isNotNull(root.get("deletedAt"));
    }

    public static Specification<Voucher> hasEventOwnerOrCreator(String userId) {
        return (root, query, cb) -> {
            if (userId == null) return null;

            // 1. Voucher được tạo bởi chính người này
            var createdByPredicate = cb.equal(root.get("createdBy"), userId);

            // 2. Voucher áp dụng cho Show thuộc Event của người này
            // Path: Voucher -> show -> event -> user -> id
            var eventOwnerPredicate = cb.equal(root.get("show").get("event").get("user").get("id"), userId);

            return cb.or(createdByPredicate, eventOwnerPredicate);
        };
    }

    public static Specification<Voucher> fetchDetails() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("show", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("ticketTypes", jakarta.persistence.criteria.JoinType.LEFT);
                query.distinct(true);
            }
            return null;
        };
    }

}
