package com.example.event.service;

import com.example.event.dto.VoucherDTO;
import com.example.event.dto.request.CreateVoucherReq;
import com.example.event.dto.request.SearchVoucherReq;
import com.example.event.dto.request.UpdateVoucherReq;
import com.example.event.entity.Reservation;
import com.example.event.entity.Voucher;
import org.springframework.data.domain.Page;

import java.util.List;

public interface VoucherService {
    VoucherDTO createVoucher(CreateVoucherReq req);

    VoucherDTO updateVoucher(String id, UpdateVoucherReq req);

    VoucherDTO findVoucherById(String id);

    VoucherDTO findVoucherOfShowByCode(String showId, String code);

    List<VoucherDTO> findVoucherByShowIdOrVoucherSystem(String showId);

    Page<VoucherDTO> getVouchersSearch(SearchVoucherReq req);

    void deleteVoucher(String id);

    void restoreVoucher(String id);

    void release(String voucherId);

    Voucher validateVoucherForReservation(String voucherId, Reservation reservation);

    void validateVoucher(Voucher voucher);

    void reserveVoucher(String voucherId);
}
