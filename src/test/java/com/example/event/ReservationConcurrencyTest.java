package com.example.event;

import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.constant.*;
import com.example.event.dto.request.ReservationItemReq;
import com.example.event.dto.request.ReservationReq;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.repository.*;
import com.example.event.service.ReservationService;
import com.example.event.service.TicketQueueService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
public class ReservationConcurrencyTest {

    static {
        // Load environment variables from .env file for testing environment
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private TicketTierRepository ticketTierRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationItemRepository reservationItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private TicketQueueService ticketQueueService;

    private Event testEvent;
    private Show testShow;
    private TicketType testTicketType;
    private TicketTier testTicketTier;
    private Seat testSeat;
    private final List<User> testUsers = new ArrayList<>();
    private boolean isExternalSeat = false;

    @BeforeEach
    void setUp() {
        // Mock ticketQueueService
        Mockito.when(ticketQueueService.getRemainingTimeSeconds(anyString(), anyString()))
                .thenReturn(600L);

        // Try to load one of the seat IDs you provided using eager fetch
        String targetSeatId = "01KQVVPZJWH0DVPZCE7E6MH7M1";
        
        List<Seat> seats = entityManager.createQuery(
                "SELECT s FROM Seat s " +
                "JOIN FETCH s.ticketType tt " +
                "JOIN FETCH tt.show sh " +
                "JOIN FETCH sh.event e " +
                "WHERE s.id = :seatId", Seat.class)
                .setParameter("seatId", targetSeatId)
                .getResultList();
        
        testSeat = seats.isEmpty() ? null : seats.get(0);

        if (testSeat != null) {
            isExternalSeat = true;
            testTicketType = testSeat.getTicketType();
            testShow = testTicketType.getShow();
            testEvent = testShow.getEvent();

            // Clear Redis lock for external seat if any leftover exists
            String redisKey = String.format("ticket_seat:{show:%s}:%s:lock", testShow.getId(), testSeat.getId());
            redisTemplate.delete(redisKey);

            // Find an active TicketTier associated with this TicketType
            testTicketTier = ticketTierRepository.findAll().stream()
                    .filter(tier -> tier.getTicketType().getId().equals(testTicketType.getId())
                            && tier.getStatus() == TicketTierStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);

            // If no active tier is found, fallback to find any tier or create a new one
            if (testTicketTier == null) {
                testTicketTier = new TicketTier();
                testTicketTier.setName("VIP Zone Concurrency");
                testTicketTier.setPrice(1000000L);
                testTicketTier.setLimitQuantity(100);
                testTicketTier.setReservedQuantity(0);
                testTicketTier.setSoldQuantity(0);
                testTicketTier.setStatus(TicketTierStatus.ACTIVE);
                testTicketTier.setTicketType(testTicketType);
                testTicketTier.setCreatedAt(LocalDateTime.now());
                testTicketTier = ticketTierRepository.save(testTicketTier);
            }

            // Ensure the seat is AVAILABLE for the concurrent test
            testSeat.setStatus(SeatStatus.AVAILABLE);
            testSeat.setReservedBy(null);
            testSeat.setHoldExpiresAt(null);
            testSeat = seatRepository.save(testSeat);

            System.out.println(">>> Found external seat " + targetSeatId + ". Running test on existing DB data.");
        } else {
            isExternalSeat = false;
            // Fallback: Create mock data
            // 1. Create Event
            testEvent = new Event();
            testEvent.setName("Concurrency Test Event");
            testEvent.setStatus(EventStatus.PUBLISHED);
            testEvent.setCreatedAt(LocalDateTime.now());
            testEvent = eventRepository.save(testEvent);

            // 2. Create Show
            testShow = new Show();
            testShow.setEvent(testEvent);
            testShow.setMinOrder(1);
            testShow.setMaxOrder(50); // Set max order high enough for 50 threads if needed
            testShow.setStartTime(LocalDateTime.now().minusHours(1));
            testShow.setEndTime(LocalDateTime.now().plusHours(5));
            testShow.setStatus(ShowStatus.ACTIVE);
            testShow.setCreatedAt(LocalDateTime.now());
            testShow = showRepository.save(testShow);

            // 3. Create TicketType
            testTicketType = new TicketType();
            testTicketType.setName("VIP Ticket Type");
            testTicketType.setTotalQuantity(100);
            testTicketType.setReservedQuantity(0);
            testTicketType.setSoldQuantity(0);
            testTicketType.setStatus(TicketTypeStatus.ACTIVE);
            testTicketType.setSeatingType(SeatingType.SEATED);
            testTicketType.setShow(testShow);
            testTicketType.setCreatedAt(LocalDateTime.now());
            testTicketType = ticketTypeRepository.save(testTicketType);

            // 4. Create TicketTier
            testTicketTier = new TicketTier();
            testTicketTier.setName("VIP Zone");
            testTicketTier.setPrice(1000000L);
            testTicketTier.setLimitQuantity(100);
            testTicketTier.setReservedQuantity(0);
            testTicketTier.setSoldQuantity(0);
            testTicketTier.setStatus(TicketTierStatus.ACTIVE);
            testTicketTier.setTicketType(testTicketType);
            testTicketTier.setCreatedAt(LocalDateTime.now());
            testTicketTier = ticketTierRepository.save(testTicketTier);

            // 5. Create 1 Single Seat
            testSeat = new Seat();
            testSeat.setRowName("A");
            testSeat.setSeatNumber("1");
            testSeat.setSeatCode("A-1");
            testSeat.setStatus(SeatStatus.AVAILABLE);
            testSeat.setTicketType(testTicketType);
            testSeat.setCreatedAt(LocalDateTime.now());
            testSeat = seatRepository.save(testSeat);

            // Clear Redis lock for fallback seat if any exists
            String redisKey = String.format("ticket_seat:{show:%s}:%s:lock", testShow.getId(), testSeat.getId());
            redisTemplate.delete(redisKey);
            
            System.out.println(">>> External seat not found. Running test on generated mock data.");
        }
    }

    @AfterEach
    void tearDown() {
        try {
            // Selectively clean up DB records created by the test to protect existing data
            List<String> userIds = testUsers.stream()
                    .map(User::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());

            if (!userIds.isEmpty()) {
                // Find all reservations for test users
                List<Reservation> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getUser() != null && userIds.contains(r.getUser().getId()))
                        .collect(Collectors.toList());

                List<String> reservationIds = reservations.stream()
                        .map(Reservation::getId)
                        .collect(Collectors.toList());

                if (!reservationIds.isEmpty()) {
                    // 1. Delete payments associated with test reservations
                    List<Payment> payments = paymentRepository.findAll().stream()
                            .filter(p -> p.getReservation() != null && reservationIds.contains(p.getReservation().getId()))
                            .collect(Collectors.toList());
                    if (!payments.isEmpty()) {
                        paymentRepository.deleteAll(payments);
                    }

                    // 2. Delete tickets associated with test reservations
                    List<Ticket> tickets = ticketRepository.findAll().stream()
                            .filter(t -> t.getReservation() != null && reservationIds.contains(t.getReservation().getId()))
                            .collect(Collectors.toList());
                    if (!tickets.isEmpty()) {
                        ticketRepository.deleteAll(tickets);
                    }

                    // 3. Delete reservation items associated with test reservations
                    List<ReservationItem> items = reservationItemRepository.findAll().stream()
                            .filter(item -> item.getReservation() != null && reservationIds.contains(item.getReservation().getId()))
                            .collect(Collectors.toList());
                    if (!items.isEmpty()) {
                        reservationItemRepository.deleteAll(items);
                    }

                    // 4. Delete test reservations
                    reservationRepository.deleteAll(reservations);
                }
            }

            // Clear Redis lock at the end of the test
            if (testShow != null && testSeat != null) {
                String redisKey = String.format("ticket_seat:{show:%s}:%s:lock", testShow.getId(), testSeat.getId());
                redisTemplate.delete(redisKey);
            }

            if (!isExternalSeat) {
                if (testSeat != null) seatRepository.delete(testSeat);
                if (testTicketTier != null) ticketTierRepository.delete(testTicketTier);
                if (testTicketType != null) ticketTypeRepository.delete(testTicketType);
                if (testShow != null) showRepository.delete(testShow);
                if (testEvent != null) eventRepository.delete(testEvent);
            } else {
                // If it was an external seat, reset it back to AVAILABLE
                if (testSeat != null) {
                    testSeat.setStatus(SeatStatus.AVAILABLE);
                    testSeat.setReservedBy(null);
                    testSeat.setHoldExpiresAt(null);
                    seatRepository.save(testSeat);
                }
            }

            // Finally, delete the test users
            if (!testUsers.isEmpty()) {
                userRepository.deleteAll(testUsers);
            }
        } catch (Exception e) {
            System.err.println("Clean up failed: " + e.getMessage());
        }
    }

    @Test
    void testReservationRaceCondition_OnlyOneShouldSucceed() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger duplicateSeatErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            // Create and persist user for this thread
            User user = new User();
            user.setEmail("threaduser" + index + "@example.com");
            user.setName("Thread User " + index);
            user.setPhone(String.format("0900000%03d", index));
            user.setStatus(UserStatus.ACTIVE);
            user.setCreatedAt(LocalDateTime.now());
            user = userRepository.save(user);
            testUsers.add(user);

            // Prepare reservation request
            ReservationReq req = new ReservationReq();
            req.setShowId(testShow.getId());
            req.setCustomerEmail(user.getEmail());
            req.setCustomerName(user.getName());
            req.setCustomerPhone(user.getPhone());

            ReservationItemReq itemReq = new ReservationItemReq();
            itemReq.setTicketTypeId(testTicketType.getId());
            itemReq.setTicketTierId(testTicketTier.getId());
            itemReq.setQuantity(1);
            itemReq.setSeatIds(List.of(testSeat.getId()));
            req.setItems(List.of(itemReq));

            final User finalUser = user;
            executorService.submit(() -> {
                try {
                    // Set ThreadLocal SecurityContext for Spring Security / SecurityUtils
                    CustomUserDetails userDetails = new CustomUserDetails(finalUser);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(auth);
                    SecurityContextHolder.setContext(context);

                    // Wait until all threads are ready
                    startLatch.await();

                    // Act
                    reservationService.createReservation(req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    failureCount.incrementAndGet();
                    if (e.getErrorCode() == ErrorCode.SEAT_ALREADY_RESERVED) {
                        duplicateSeatErrorCount.incrementAndGet();
                    } else {
                        System.err.println("Thread failed with AppException: " + e.getErrorCode() + " - " + e.getMessage());
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("Thread failed with unexpected exception: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to finish
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        System.out.println("=== TEST RESULTS ===");
        System.out.println("Threads completed in time: " + completed);
        System.out.println("Successful reservations: " + successCount.get());
        System.out.println("Failed reservations: " + failureCount.get());
        System.out.println("SEAT_ALREADY_RESERVED errors: " + duplicateSeatErrorCount.get());

        // Assertions
        assertEquals(threadCount - 1, failureCount.get(), "Remaining users must fail");
    }
}
