package com.example.event.service;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.DiscountType;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.VoucherScope;
import com.example.event.constant.VoucherStatus;
import com.example.event.dto.VoucherDTO;
import com.example.event.dto.request.CreateVoucherReq;
import com.example.event.dto.request.UpdateVoucherReq;
import com.example.event.entity.Reservation;
import com.example.event.entity.Show;
import com.example.event.entity.TicketType;
import com.example.event.entity.Voucher;
import com.example.event.exception.AppException;
import com.example.event.mapper.VoucherMapper;
import com.example.event.repository.ReservationRepository;
import com.example.event.repository.ShowRepository;
import com.example.event.repository.TicketTypeRepository;
import com.example.event.repository.VoucherRepository;
import com.example.event.service.Impl.VoucherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceImplTest {

    @InjectMocks
    private VoucherServiceImpl voucherService;

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private ShowRepository showRepository;
    @Mock
    private VoucherMapper voucherMapper;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SecurityUtils securityUtils;

    private Show sampleShow;
    private TicketType sampleTicketType;
    private Voucher sampleVoucher;

    @BeforeEach
    void setUp() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-admin-123");

        sampleShow = new Show();
        sampleShow.setId("show-123");

        sampleTicketType = new TicketType();
        sampleTicketType.setId("ticket-type-123");

        sampleVoucher = new Voucher();
        sampleVoucher.setId("voucher-123");
        sampleVoucher.setCode("SUMMER2026");
        sampleVoucher.setName("Khuyến mãi hè");
        sampleVoucher.setQuantity(100);
        sampleVoucher.setReservedQuantity(0);
        sampleVoucher.setUsedQuantity(0);
        sampleVoucher.setIsUnlimited(false);
        sampleVoucher.setDiscountType(DiscountType.PERCENT);
        sampleVoucher.setDiscountValue(20L);
        sampleVoucher.setMinOrderValue(200000L);
        sampleVoucher.setMaxDiscountValue(50000L);
        sampleVoucher.setScope(VoucherScope.SYSTEM);
        sampleVoucher.setStatus(VoucherStatus.ACTIVE);
        sampleVoucher.setStartTime(LocalDateTime.now().minusDays(1));
        sampleVoucher.setEndTime(LocalDateTime.now().plusDays(7));
        sampleVoucher.setVersion(1L);

        when(voucherMapper.toDTO(any())).thenAnswer(invocation -> {
            Voucher v = invocation.getArgument(0);
            return VoucherDTO.builder()
                    .id(v.getId())
                    .code(v.getCode())
                    .name(v.getName())
                    .discountValue(v.getDiscountValue())
                    .status(v.getStatus())
                    .build();
        });
    }

    @Nested
    @DisplayName("Tests for createVoucher")
    class CreateVoucherTests {

        @Test
        @DisplayName("Tạo voucher phạm vi SYSTEM thành công -> Lưu trạng thái DRAFT")
        void createVoucher_Success_SystemScope() {
            // Given
            CreateVoucherReq req = new CreateVoucherReq();
            req.setName("Giảm giá mở bán");
            req.setCode("OPENING50");
            req.setQuantity(50);
            req.setIsUnlimited(false);
            req.setStartTime(LocalDateTime.now().plusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(10));
            req.setDiscountType(DiscountType.PERCENT);
            req.setDiscountValue(10L);
            req.setMaxDiscountValue(30000L);
            req.setMinOrderValue(100000L);
            req.setScope(VoucherScope.SYSTEM);

            when(voucherRepository.existsVoucherByCode("OPENING50")).thenReturn(false);

            // When
            VoucherDTO result = voucherService.createVoucher(req);

            // Then
            assertNotNull(result);
            assertEquals("OPENING50", result.getCode());
            verify(voucherRepository).save(argThat(v -> v.getStatus() == VoucherStatus.DRAFT &&
                    v.getScope() == VoucherScope.SYSTEM &&
                    v.getShow() == null));
        }

        @Test
        @DisplayName("Tạo voucher phạm vi ORGANIZER thành công -> Gắn đúng Show và TicketTypes")
        void createVoucher_Success_OrganizerScope() {
            // Given
            CreateVoucherReq req = new CreateVoucherReq();
            req.setName("Voucher cho Show VIP");
            req.setCode("SHOWVIP20");
            req.setQuantity(30);
            req.setStartTime(LocalDateTime.now().plusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(5));
            req.setDiscountType(DiscountType.VALUE);
            req.setDiscountValue(50000L);
            req.setMinOrderValue(300000L);
            req.setScope(VoucherScope.ORGANIZER);
            req.setShowId("show-123");
            req.setTicketTypeIds(List.of("ticket-type-123"));

            when(voucherRepository.existsVoucherByCode("SHOWVIP20")).thenReturn(false);
            when(showRepository.findById("show-123")).thenReturn(Optional.of(sampleShow));
            when(ticketTypeRepository.findAllById(List.of("ticket-type-123"))).thenReturn(List.of(sampleTicketType));

            // When
            VoucherDTO result = voucherService.createVoucher(req);

            // Then
            assertNotNull(result);
            verify(voucherRepository).save(argThat(v -> v.getScope() == VoucherScope.ORGANIZER &&
                    v.getShow() != null &&
                    v.getShow().getId().equals("show-123") &&
                    v.getTicketTypes().size() == 1));
        }

        @Test
        @DisplayName("Tạo voucher trùng mã Code -> ném VOUCHER_VALIDATION_ERROR")
        void createVoucher_DuplicateCode_ThrowsAppException() {
            // Given
            CreateVoucherReq req = new CreateVoucherReq();
            req.setName("Voucher trùng");
            req.setCode("SUMMER2026");
            req.setQuantity(10);
            req.setStartTime(LocalDateTime.now().plusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(2));
            req.setDiscountType(DiscountType.VALUE);
            req.setDiscountValue(10000L);
            req.setMinOrderValue(50000L);
            req.setScope(VoucherScope.SYSTEM);

            when(voucherRepository.existsVoucherByCode("SUMMER2026")).thenReturn(true);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.createVoucher(req));
            assertEquals(ErrorCode.VOUCHER_VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("Ngày bắt đầu trong quá khứ -> ném VOUCHER_VALIDATION_ERROR")
        void createVoucher_PastStartTime_ThrowsAppException() {
            // Given
            CreateVoucherReq req = new CreateVoucherReq();
            req.setName("Voucher quá khứ");
            req.setCode("PASTSTART");
            req.setQuantity(10);
            req.setStartTime(LocalDateTime.now().minusDays(1)); // Quá khứ
            req.setEndTime(LocalDateTime.now().plusDays(2));
            req.setDiscountType(DiscountType.VALUE);
            req.setDiscountValue(10000L);
            req.setMinOrderValue(50000L);
            req.setScope(VoucherScope.SYSTEM);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.createVoucher(req));
            assertEquals(ErrorCode.VOUCHER_VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("Scope ORGANIZER nhưng không tìm thấy Show -> ném SHOW_NOT_FOUND")
        void createVoucher_OrganizerScope_ShowNotFound_ThrowsAppException() {
            // Given
            CreateVoucherReq req = new CreateVoucherReq();
            req.setName("Voucher Show");
            req.setCode("SHOWMISSING");
            req.setQuantity(10);
            req.setStartTime(LocalDateTime.now().plusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(2));
            req.setDiscountType(DiscountType.VALUE);
            req.setDiscountValue(10000L);
            req.setMinOrderValue(50000L);
            req.setScope(VoucherScope.ORGANIZER);
            req.setShowId("show-non-existent");
            req.setTicketTypeIds(List.of("ticket-type-123"));

            when(voucherRepository.existsVoucherByCode(anyString())).thenReturn(false);
            when(showRepository.findById("show-non-existent")).thenReturn(Optional.empty());

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.createVoucher(req));
            assertEquals(ErrorCode.SHOW_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Scope ORGANIZER nhưng một số TicketType không tồn tại -> ném TICKET_TYPE_NOT_FOUND")
        void createVoucher_OrganizerScope_TicketTypeNotFound_ThrowsAppException() {
            // Given
            CreateVoucherReq req = new CreateVoucherReq();
            req.setName("Voucher Show");
            req.setCode("TICKETMISSING");
            req.setQuantity(10);
            req.setStartTime(LocalDateTime.now().plusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(2));
            req.setDiscountType(DiscountType.VALUE);
            req.setDiscountValue(10000L);
            req.setMinOrderValue(50000L);
            req.setScope(VoucherScope.ORGANIZER);
            req.setShowId("show-123");
            req.setTicketTypeIds(List.of("ticket-type-123", "ticket-type-missing"));

            when(voucherRepository.existsVoucherByCode(anyString())).thenReturn(false);
            when(showRepository.findById("show-123")).thenReturn(Optional.of(sampleShow));
            when(ticketTypeRepository.findAllById(any())).thenReturn(List.of(sampleTicketType)); // Chỉ tìm thấy 1/2

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.createVoucher(req));
            assertEquals(ErrorCode.TICKET_TYPE_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Tests for updateVoucher")
    class UpdateVoucherTests {

        @Test
        @DisplayName("Cập nhật voucher thành công")
        void updateVoucher_Success() {
            // Given
            UpdateVoucherReq req = new UpdateVoucherReq();
            req.setVersion(1L);
            req.setName("Tên voucher mới");
            req.setCode("SUMMER2026NEW");
            req.setQuantity(150);
            req.setStartTime(LocalDateTime.now().minusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(10));
            req.setStatus(VoucherStatus.ACTIVE);
            req.setDiscountType(DiscountType.PERCENT);
            req.setDiscountValue(20L); // Giữ nguyên discountValue vì đã start
            req.setMinOrderValue(200000L);
            req.setMaxDiscountValue(50000L);

            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);
            when(voucherRepository.existsByCodeAndIdNot(eq("SUMMER2026NEW"), eq("voucher-123"))).thenReturn(false);

            // When
            VoucherDTO updated = voucherService.updateVoucher("voucher-123", req);

            // Then
            assertNotNull(updated);
            assertEquals("SUMMER2026NEW", updated.getCode());
            verify(voucherRepository).save(sampleVoucher);
        }

        @Test
        @DisplayName("Voucher không tồn tại -> ném VOUCHER_NOT_FOUND")
        void updateVoucher_NotFound_ThrowsAppException() {
            // Given
            UpdateVoucherReq req = new UpdateVoucherReq();
            when(voucherRepository.findVoucherById("voucher-999")).thenReturn(null);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.updateVoucher("voucher-999", req));
            assertEquals(ErrorCode.VOUCHER_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Lệch version (Optimistic Lock) -> ném CONCURRENT_UPDATE")
        void updateVoucher_ConcurrentUpdate_ThrowsAppException() {
            // Given
            UpdateVoucherReq req = new UpdateVoucherReq();
            req.setVersion(99L); // DB đang là version 1L
            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.updateVoucher("voucher-123", req));
            assertEquals(ErrorCode.CONCURRENT_UPDATE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher đang có người giữ chỗ (reservedQuantity > 0) -> Không cho chuyển về DRAFT")
        void updateVoucher_ActiveToDraft_WhenReservedQtyGreaterThanZero_ThrowsAppException() {
            // Given
            sampleVoucher.setReservedQuantity(2);
            UpdateVoucherReq req = new UpdateVoucherReq();
            req.setVersion(1L);
            req.setName("Test");
            req.setCode("TESTCODE");
            req.setQuantity(50);
            req.setStartTime(sampleVoucher.getStartTime());
            req.setEndTime(sampleVoucher.getEndTime());
            req.setStatus(VoucherStatus.DRAFT); // Chuyển về DRAFT
            req.setDiscountType(DiscountType.PERCENT);
            req.setDiscountValue(20L);
            req.setMinOrderValue(100000L);

            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.updateVoucher("voucher-123", req));
            assertEquals(ErrorCode.VOUCHER_VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("Số lượng mới nhỏ hơn số lượng đang giữ chỗ (quantity < reservedQuantity) -> Báo lỗi")
        void updateVoucher_QuantityLessThanReserved_ThrowsAppException() {
            // Given
            sampleVoucher.setReservedQuantity(10);
            UpdateVoucherReq req = new UpdateVoucherReq();
            req.setVersion(1L);
            req.setName("Test");
            req.setCode("TESTCODE");
            req.setQuantity(5); // Nhỏ hơn reservedQuantity (10)
            req.setStartTime(sampleVoucher.getStartTime());
            req.setEndTime(sampleVoucher.getEndTime());
            req.setStatus(VoucherStatus.ACTIVE);
            req.setDiscountType(DiscountType.PERCENT);
            req.setDiscountValue(20L);
            req.setMinOrderValue(100000L);

            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.updateVoucher("voucher-123", req));
            assertEquals(ErrorCode.VOUCHER_VALIDATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher đã bắt đầu -> Không được thay đổi giá trị giảm giá (discountValue)")
        void updateVoucher_AlreadyStarted_CannotChangeDiscountValue() {
            // Given
            sampleVoucher.setStartTime(LocalDateTime.now().minusHours(2));
            sampleVoucher.setStatus(VoucherStatus.ACTIVE);

            UpdateVoucherReq req = new UpdateVoucherReq();
            req.setVersion(1L);
            req.setName("Test");
            req.setCode("SUMMER2026");
            req.setQuantity(100);
            req.setStartTime(sampleVoucher.getStartTime());
            req.setEndTime(sampleVoucher.getEndTime());
            req.setStatus(VoucherStatus.ACTIVE);
            req.setDiscountType(DiscountType.PERCENT);
            req.setDiscountValue(50L); // Thay đổi từ 20% -> 50%
            req.setMinOrderValue(sampleVoucher.getMinOrderValue());
            req.setMaxDiscountValue(sampleVoucher.getMaxDiscountValue());

            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.updateVoucher("voucher-123", req));
            assertEquals(ErrorCode.VOUCHER_VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Tests for find & query")
    class FindAndQueryTests {

        @Test
        @DisplayName("Tìm voucher theo ID thành công")
        void findVoucherById_Success() {
            // Given
            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When
            VoucherDTO dto = voucherService.findVoucherById("voucher-123");

            // Then
            assertNotNull(dto);
            assertEquals("voucher-123", dto.getId());
        }

        @Test
        @DisplayName("Tìm voucher của Show theo Code thành công khi scope hợp lệ")
        void findVoucherOfShowByCode_Success() {
            // Given
            sampleVoucher.setScope(VoucherScope.ORGANIZER);
            sampleVoucher.setShow(sampleShow);

            when(showRepository.findShowById("show-123")).thenReturn(sampleShow);
            when(voucherRepository.findVoucherByCode("SUMMER2026")).thenReturn(sampleVoucher);

            // When
            VoucherDTO dto = voucherService.findVoucherOfShowByCode("show-123", "SUMMER2026");

            // Then
            assertNotNull(dto);
            assertEquals("SUMMER2026", dto.getCode());
        }

        @Test
        @DisplayName("Voucher của Show khác áp dụng vào Show này -> ném VOUCHER_NOT_APPLICABLE")
        void findVoucherOfShowByCode_ShowScopeMismatch_ThrowsAppException() {
            // Given
            Show otherShow = new Show();
            otherShow.setId("show-other-999");

            sampleVoucher.setScope(VoucherScope.ORGANIZER);
            sampleVoucher.setShow(otherShow);

            when(showRepository.findShowById("show-123")).thenReturn(sampleShow);
            when(voucherRepository.findVoucherByCode("SUMMER2026")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.findVoucherOfShowByCode("show-123", "SUMMER2026"));

            assertEquals(ErrorCode.VOUCHER_NOT_APPLICABLE, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Tests for validateVoucher & validateVoucherForReservation")
    class ValidationLogicTests {

        @Test
        @DisplayName("Voucher ở trạng thái DRAFT/PAUSED -> ném VOUCHER_NOT_ACTIVE")
        void validateVoucher_NotActive_ThrowsAppException() {
            // Given
            sampleVoucher.setStatus(VoucherStatus.DRAFT);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.validateVoucher(sampleVoucher));
            assertEquals(ErrorCode.VOUCHER_NOT_ACTIVE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher chưa đến ngày bắt đầu -> ném VOUCHER_NOT_STARTED")
        void validateVoucher_NotStarted_ThrowsAppException() {
            // Given
            sampleVoucher.setStartTime(LocalDateTime.now().plusDays(2));

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.validateVoucher(sampleVoucher));
            assertEquals(ErrorCode.VOUCHER_NOT_STARTED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher đã quá hạn kết thúc -> ném VOUCHER_EXPIRED")
        void validateVoucher_Expired_ThrowsAppException() {
            // Given
            sampleVoucher.setEndTime(LocalDateTime.now().minusDays(1));

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.validateVoucher(sampleVoucher));
            assertEquals(ErrorCode.VOUCHER_EXPIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher hết lượt sử dụng (reservedQuantity >= quantity) -> ném VOUCHER_EXHAUSTED")
        void validateVoucher_Exhausted_ThrowsAppException() {
            // Given
            sampleVoucher.setQuantity(50);
            sampleVoucher.setReservedQuantity(50);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.validateVoucher(sampleVoucher));
            assertEquals(ErrorCode.VOUCHER_EXHAUSTED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Đơn hàng chưa đạt giá trị tối thiểu (minOrderValue) -> ném VOUCHER_MIN_ORDER_NOT_MET")
        void validateVoucherForReservation_MinOrderNotMet_ThrowsAppException() {
            // Given
            Reservation res = new Reservation();
            res.setShow(sampleShow);
            res.setTotalAmount(100000L); // Nhỏ hơn minOrderValue = 200000L

            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class,
                    () -> voucherService.validateVoucherForReservation("voucher-123", res));

            assertEquals(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Tests for reserve & release")
    class ReserveAndReleaseTests {

        @Test
        @DisplayName("Giữ chỗ voucher thành công khi còn lượt")
        void reserveVoucher_Success() {
            // Given
            when(voucherRepository.increaseReservedQuantity("voucher-123")).thenReturn(1);

            // When & Then
            assertDoesNotThrow(() -> voucherService.reserveVoucher("voucher-123"));
            verify(voucherRepository).increaseReservedQuantity("voucher-123");
        }

        @Test
        @DisplayName("Giữ chỗ voucher thất bại khi đã hết lượt (updated = 0) -> ném VOUCHER_EXHAUSTED")
        void reserveVoucher_Exhausted_ThrowsAppException() {
            // Given
            when(voucherRepository.increaseReservedQuantity("voucher-123")).thenReturn(0);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.reserveVoucher("voucher-123"));
            assertEquals(ErrorCode.VOUCHER_EXHAUSTED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Giải phóng voucher đã giữ chỗ thành công")
        void release_Success() {
            // When
            voucherService.release("voucher-123");

            // Then
            verify(voucherRepository).decreaseReservedQuantity("voucher-123");
        }
    }

    @Nested
    @DisplayName("Tests for delete & restore")
    class DeleteAndRestoreTests {

        @Test
        @DisplayName("Xóa mềm voucher thành công khi chưa phát sinh đơn hay giữ chỗ")
        void deleteVoucher_Success() {
            // Given
            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);
            when(reservationRepository.existsByVoucherIdAndDeletedAtIsNull("voucher-123")).thenReturn(false);

            // When
            voucherService.deleteVoucher("voucher-123");

            // Then
            assertNotNull(sampleVoucher.getDeletedAt());
            assertTrue(sampleVoucher.getCode().contains("-deleted-"));
            verify(voucherRepository).save(sampleVoucher);
        }

        @Test
        @DisplayName("Voucher đã bị xóa trước đó -> ném VOUCHER_ALREADY_DELETED")
        void deleteVoucher_AlreadyDeleted_ThrowsAppException() {
            // Given
            sampleVoucher.setDeletedAt(LocalDateTime.now());
            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.deleteVoucher("voucher-123"));
            assertEquals(ErrorCode.VOUCHER_ALREADY_DELETED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Voucher đang có đơn giữ chỗ hoặc đã sử dụng -> ném VOUCHER_HAS_RESERVATIONS")
        void deleteVoucher_HasReservations_ThrowsAppException() {
            // Given
            sampleVoucher.setUsedQuantity(1);
            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.deleteVoucher("voucher-123"));
            assertEquals(ErrorCode.VOUCHER_HAS_RESERVATIONS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Khôi phục voucher trong thùng rác thành công")
        void restoreVoucher_Success() {
            // Given
            sampleVoucher.setDeletedAt(LocalDateTime.now());
            sampleVoucher.setName("Khuyến mãi hè-deleted-1700000000");
            sampleVoucher.setCode("SUMMER2026-deleted-1700000000");

            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);
            when(voucherRepository.existsByNameAndIdNot(anyString(), anyString())).thenReturn(false);
            when(voucherRepository.existsByCodeAndIdNot(anyString(), anyString())).thenReturn(false);

            // When
            voucherService.restoreVoucher("voucher-123");

            // Then
            assertNull(sampleVoucher.getDeletedAt());
            assertEquals("Khuyến mãi hè", sampleVoucher.getName());
            assertEquals("SUMMER2026", sampleVoucher.getCode());
            verify(voucherRepository).save(sampleVoucher);
        }

        @Test
        @DisplayName("Voucher không nằm trong thùng rác -> ném VOUCHER_NOT_IN_TRASH")
        void restoreVoucher_NotInTrash_ThrowsAppException() {
            // Given
            sampleVoucher.setDeletedAt(null);
            when(voucherRepository.findVoucherById("voucher-123")).thenReturn(sampleVoucher);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> voucherService.restoreVoucher("voucher-123"));
            assertEquals(ErrorCode.VOUCHER_NOT_IN_TRASH, ex.getErrorCode());
        }
    }
}
