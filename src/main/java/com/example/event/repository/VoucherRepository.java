package com.example.event.repository;

import com.example.event.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VoucherRepository extends JpaRepository<Voucher, String>, JpaSpecificationExecutor<Voucher> {
    boolean existsVoucherByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    boolean existsByNameAndIdNot(String name, String id);

    Voucher findVoucherById(String id);

    Voucher findVoucherByCode(String code);

    @Query("""
                select v from Voucher v
                where (v.show.id = :showId or v.scope = 'SYSTEM') and v.deletedAt is null and v.status = 'ACTIVE'
            """)
    List<Voucher> findVoucherByShowIdOrSystem(@Param("showId") String showId);

    @Modifying
    @Query("""
                UPDATE Voucher v
                SET v.usedQuantity = v.usedQuantity + 1
                WHERE v.id = :voucherId
                AND v.usedQuantity < v.quantity 
                AND v.deletedAt is null 
                AND v.status = 'ACTIVE'
            """)
    int increaseUsedQuantity(@Param("voucherId") String voucherId);

    @Modifying
    @Query("""
                UPDATE Voucher v
                SET v.reservedQuantity = v.reservedQuantity + 1
                WHERE v.id = :voucherId
                AND v.reservedQuantity < v.quantity 
                AND v.deletedAt is null 
                AND v.status = 'ACTIVE'
            """)
    int increaseReservedQuantity(@Param("voucherId") String voucherId);

    @Modifying
    @Query("""
                UPDATE Voucher v
                SET v.reservedQuantity = v.reservedQuantity - 1
                WHERE v.id = :voucherId
                AND v.reservedQuantity > 0 
                AND v.deletedAt is null 
                AND v.status = 'ACTIVE'
            """)
    int decreaseReservedQuantity(@Param("voucherId") String voucherId);
}
