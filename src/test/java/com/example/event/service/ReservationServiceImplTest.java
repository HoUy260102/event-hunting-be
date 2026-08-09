package com.example.event.service;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.EventStatus;
import com.example.event.constant.ReservationStatus;
import com.example.event.constant.ShowStatus;
import com.example.event.constant.TicketTierStatus;
import com.example.event.constant.TicketTypeStatus;
import com.example.event.dto.ReservationDTO;
import com.example.event.dto.request.ReservationItemReq;
import com.example.event.dto.request.ReservationReq;
import com.example.event.entity.Event;
import com.example.event.entity.Reservation;
import com.example.event.entity.Seat;
import com.example.event.entity.Show;
import com.example.event.entity.TicketTier;
import com.example.event.entity.TicketType;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.mapper.ReservationMapper;
import com.example.event.repository.ReservationItemRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.repository.SeatRepository;
import com.example.event.repository.ShowRepository;
import com.example.event.repository.TicketTierRepository;
import com.example.event.repository.TicketTypeRepository;
import com.example.event.repository.UserRepository;
import com.example.event.service.Impl.ReservationServiceImpl;
import com.example.event.service.LockService;
import com.example.event.service.TicketQueueService;
import com.example.event.service.VoucherService;
import com.example.event.util.ReservationCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceImplTest {

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationItemRepository reservationItemRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private TicketTierRepository ticketTierRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LockService lockService;
    @Mock
    private ShowRepository showRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private TicketQueueService ticketQueueService;
    @Mock
    private VoucherService voucherService;
    @Mock
    private ReservationCodeGenerator reservationCodeGenerator;
    @Mock
    private SecurityUtils securityUtils;

    private ReservationReq validRequest;
    private User user;
    private Show show;
    private Event event;
    private TicketType ticketType;
    private TicketTier ticketTier;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user-123");

        event = new Event();
        event.setId("event-123");
        event.setStatus(EventStatus.PUBLISHED);

        show = new Show();
        show.setId("show-123");
        show.setEvent(event);
        show.setStatus(ShowStatus.ACTIVE);
        show.setEndTime(LocalDateTime.now().plusDays(1));
        show.setMinOrder(1);
        show.setMaxOrder(10);

        ticketType = new TicketType();
        ticketType.setId("type-123");
        ticketType.setName("VIP");
        ticketType.setStatus(TicketTypeStatus.ACTIVE);

        ticketTier = new TicketTier();
        ticketTier.setId("tier-123");
        ticketTier.setName("Zone A");
        ticketTier.setPrice(200000L);
        ticketTier.setStatus(com.example.event.constant.TicketTierStatus.ACTIVE);
        ticketTier.setSaleStartTime(LocalDateTime.now().minusDays(1));
        ticketTier.setSaleEndTime(LocalDateTime.now().plusDays(1));
        ticketTier.setTicketType(ticketType);

        validRequest = new ReservationReq();
        validRequest.setShowId(show.getId());
        validRequest.setQueueToken("queue-token");
        validRequest.setCustomerEmail("customer@test.com");
        validRequest.setCustomerName("Nguyen Van A");
        validRequest.setCustomerPhone("0123456789");

        ReservationItemReq itemReq = new ReservationItemReq();
        itemReq.setTicketTypeId(ticketType.getId());
        itemReq.setTicketTierId(ticketTier.getId());
        itemReq.setQuantity(2);
        validRequest.setItems(Collections.singletonList(itemReq));
    }

    @Test
    @DisplayName("Tạo reservation thành công và trả về DTO")
    void createReservation_Success() {
        // When
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));
        when(reservationCodeGenerator.generateCode()).thenReturn("R-12345");
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationMapper.toDto(any(Reservation.class))).thenReturn(new ReservationDTO());
        when(ticketTypeRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(ticketTierRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);

        ReservationDTO result = reservationService.createReservation(validRequest);

        // Then
        assertNotNull(result);
        verify(lockService, times(1)).reserveUnassignedTickets(eq(show.getId()), any());
        verify(reservationRepository, times(1)).saveAndFlush(any(Reservation.class));
        verify(ticketTypeRepository, times(1)).incrementReservedQuantity(eq(ticketType.getId()), eq(2), any(), anyString());
        verify(ticketTierRepository, times(1)).incrementReservedQuantity(eq(ticketTier.getId()), eq(2), any(), anyString());

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).saveAndFlush(reservationCaptor.capture());
        Reservation savedReservation = reservationCaptor.getValue();

        assertEquals(show.getId(), savedReservation.getShow().getId());
        assertEquals(user.getId(), savedReservation.getUser().getId());
        assertEquals(ReservationStatus.PENDING, savedReservation.getStatus());
        assertEquals(400000L, savedReservation.getTotalAmount());
        assertEquals(400000L, savedReservation.getFinalAmount());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi show không tồn tại")
    void createReservation_ShowNotFound() {
        // When
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(null);

        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.SHOW_NOT_FOUND, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi user không tồn tại")
    void createReservation_UserNotFound() {
        // Given
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(null);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi số lượng vượt quá giới hạn")
    void createReservation_QuantityExceeded() {
        // Given
        validRequest.getItems().get(0).setQuantity(11);
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.RESERVATION_QUANTITY_EXCEEDED, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi loại vé không tồn tại")
    void createReservation_TicketTypeNotFound() {
        // Given
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.empty());

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TYPE_NOT_FOUND, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi hạng vé không tồn tại")
    void createReservation_TicketTierNotFound() {
        // Given
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.empty());

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TIER_NOT_FOUND, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi loại vé đã hết chỗ")
    void createReservation_TicketTypeSoldOut() {
        // Given
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));
        when(reservationCodeGenerator.generateCode()).thenReturn("R-12345");
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketTypeRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(0);
        when(ticketTierRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TYPE_SOLD_OUT, ex.getErrorCode());
        verify(lockService, times(1)).releaseUnassignedTickets(eq(show.getId()), any());
        verify(reservationRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi hạng vé đã hết chỗ")
    void createReservation_TicketTierSoldOut() {
        // Given
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));
        when(reservationCodeGenerator.generateCode()).thenReturn("R-12345");
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketTypeRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(ticketTierRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(0);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TIER_SOLD_OUT, ex.getErrorCode());
        verify(lockService, times(1)).releaseUnassignedTickets(eq(show.getId()), any());
        verify(reservationRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi loại vé không hoạt động")
    void createReservation_TicketTypeInactive() {
        // Given
        ticketType.setStatus(com.example.event.constant.TicketTypeStatus.INACTIVE);
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TYPE_IN_ACTIVE, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi hạng vé không hoạt động")
    void createReservation_TicketTierInactive() {
        // Given
        ticketTier.setStatus(TicketTierStatus.INACTIVE);
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TIER_IN_ACTIVE, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi hạng vé chưa mở bán")
    void createReservation_TicketTierSaleNotStarted() {
        // Given
        ticketTier.setSaleStartTime(LocalDateTime.now().plusDays(1));
        ticketTier.setSaleEndTime(LocalDateTime.now().plusDays(2));
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TIER_NOT_AVAILABLE, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi hạng vé hết hạn mở bán")
    void createReservation_TicketTierSaleEnded() {
        // Given
        ticketTier.setSaleStartTime(LocalDateTime.now().minusDays(3));
        ticketTier.setSaleEndTime(LocalDateTime.now().minusDays(1));
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.TICKET_TIER_NOT_AVAILABLE, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi số lượng vé không hợp lệ")
    void createReservation_InvalidQuantity() {
        // Given
        show.setMinOrder(0);
        validRequest.getItems().get(0).setQuantity(0);
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.INVALID_QUANTITY, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi số lượng dưới mức tối thiểu")
    void createReservation_MinQuantityNotMet() {
        // Given
        show.setMinOrder(2);
        validRequest.getItems().get(0).setQuantity(1);
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.RESERVATION_QUANTITY_MINIMUM_NOT_MET, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi show đã bị xóa")
    void createReservation_ShowDeleted() {
        // Given
        show.setDeletedAt(LocalDateTime.now());
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.SHOW_NOT_FOUND, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi show bị hoãn")
    void createReservation_ShowPostponed() {
        // Given
        show.setStatus(ShowStatus.POSTPONED);
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.SHOW_POSTPONED, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi event đã bị xóa")
    void createReservation_EventDeleted() {
        // Given
        event.setDeletedAt(LocalDateTime.now());
        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(validRequest));

        // Then
        assertEquals(ErrorCode.EVENT_NOT_FOUND, ex.getErrorCode());
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi giữ ghế không tồn tại")
    void createReservation_SeatNotFound() {
        // Given
        ReservationReq assignedRequest = new ReservationReq();
        assignedRequest.setShowId(show.getId());
        assignedRequest.setQueueToken("queue-token");
        assignedRequest.setCustomerEmail("customer@test.com");
        assignedRequest.setCustomerName("Nguyen Van A");
        assignedRequest.setCustomerPhone("0123456789");

        ReservationItemReq assignedItem = new ReservationItemReq();
        assignedItem.setTicketTypeId(ticketType.getId());
        assignedItem.setTicketTierId(ticketTier.getId());
        assignedItem.setQuantity(1);
        assignedItem.setSeatIds(Collections.singletonList("seat-1"));
        assignedRequest.setItems(Collections.singletonList(assignedItem));

        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));
        when(reservationCodeGenerator.generateCode()).thenReturn("R-12345");
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketTypeRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(ticketTierRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(seatRepository.findSeatById("seat-1")).thenReturn(null);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(assignedRequest));

        // Then
        assertEquals(ErrorCode.SEAT_NOT_FOUND, ex.getErrorCode());
        verify(lockService, times(1)).unlockSeats(eq(show.getId()), any());
        verify(reservationRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thất bại khi ghế đã bị giữ trước đó")
    void createReservation_SeatAlreadyReserved() {
        // Given
        ReservationReq assignedRequest = new ReservationReq();
        assignedRequest.setShowId(show.getId());
        assignedRequest.setQueueToken("queue-token");
        assignedRequest.setCustomerEmail("customer@test.com");
        assignedRequest.setCustomerName("Nguyen Van A");
        assignedRequest.setCustomerPhone("0123456789");

        ReservationItemReq assignedItem = new ReservationItemReq();
        assignedItem.setTicketTypeId(ticketType.getId());
        assignedItem.setTicketTierId(ticketTier.getId());
        assignedItem.setQuantity(1);
        assignedItem.setSeatIds(Collections.singletonList("seat-1"));
        assignedRequest.setItems(Collections.singletonList(assignedItem));

        Seat seat = new Seat();
        seat.setId("seat-1");
        seat.setSeatCode("A1");
        seat.setRowName("A");
        seat.setSeatNumber("1");

        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));
        when(reservationCodeGenerator.generateCode()).thenReturn("R-12345");
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketTypeRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(ticketTierRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(seatRepository.findSeatById("seat-1")).thenReturn(seat);
        when(seatRepository.holdSeat(eq("seat-1"), eq(user.getId()), any())).thenReturn(0);

        // When
        AppException ex = assertThrows(AppException.class, () -> reservationService.createReservation(assignedRequest));

        // Then
        assertEquals(ErrorCode.SEAT_ALREADY_RESERVED, ex.getErrorCode());
        verify(lockService, times(1)).unlockSeats(eq(show.getId()), any());
        verify(reservationRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Tạo reservation thành công với ghế được giữ")
    void createReservation_Success_WithAssignedSeat() {
        // Given
        ReservationReq assignedRequest = new ReservationReq();
        assignedRequest.setShowId(show.getId());
        assignedRequest.setQueueToken("queue-token");
        assignedRequest.setCustomerEmail("customer@test.com");
        assignedRequest.setCustomerName("Nguyen Van A");
        assignedRequest.setCustomerPhone("0123456789");

        ReservationItemReq assignedItem = new ReservationItemReq();
        assignedItem.setTicketTypeId(ticketType.getId());
        assignedItem.setTicketTierId(ticketTier.getId());
        assignedItem.setQuantity(1);
        assignedItem.setSeatIds(Collections.singletonList("seat-1"));
        assignedRequest.setItems(Collections.singletonList(assignedItem));

        Seat seat = new Seat();
        seat.setId("seat-1");
        seat.setSeatCode("A1");
        seat.setRowName("A");
        seat.setSeatNumber("1");

        when(securityUtils.getCurrentUserId()).thenReturn(user.getId());
        when(showRepository.findShowById(show.getId())).thenReturn(show);
        when(userRepository.findUserById(user.getId())).thenReturn(user);
        when(ticketQueueService.validateQueueToken(anyString(), anyString())).thenReturn(true);
        when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString())).thenReturn(600L);
        when(ticketTypeRepository.findById(ticketType.getId())).thenReturn(Optional.of(ticketType));
        when(ticketTierRepository.findById(ticketTier.getId())).thenReturn(Optional.of(ticketTier));
        when(reservationCodeGenerator.generateCode()).thenReturn("R-12345");
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationMapper.toDto(any(Reservation.class))).thenReturn(new ReservationDTO());
        when(ticketTypeRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(ticketTierRepository.incrementReservedQuantity(anyString(), anyInt(), any(), anyString())).thenReturn(1);
        when(seatRepository.findSeatById("seat-1")).thenReturn(seat);
        when(seatRepository.holdSeat(eq("seat-1"), eq(user.getId()), any())).thenReturn(1);

        // When
        ReservationDTO result = reservationService.createReservation(assignedRequest);

        // Then
        assertNotNull(result);
        verify(lockService, times(1)).lockSeats(eq(show.getId()), any(), eq(user.getId()));
        verify(reservationItemRepository, times(1)).saveAll(any());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/show/" + show.getId() + "/seats"), any(Object.class));
    }
}
