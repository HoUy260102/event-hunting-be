package com.example.event.job;

import com.example.event.component.AuthForgotPasswordConsumer;
import com.example.event.component.AuthVerifyEmailConsumer;
import com.example.event.component.TicketEmailConsumer;
import com.example.event.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamRecoveryJob {

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketEmailConsumer ticketEmailConsumer;
    private final AuthVerifyEmailConsumer authVerifyEmailConsumer;
    private final AuthForgotPasswordConsumer authForgotPasswordConsumer;

    private static final int MAX_DELIVERY_COUNT = 3;
    private static final long MIN_IDLE_MINUTES = 2;

    /**
     * Định kỳ quét PEL cho Ticket Stream mỗi 1 phút.
     * ShedLock đảm bảo chỉ có 1 instance xử lý tại một thời điểm.
     */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "recoverTicketStreamJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void recoverTicketPendingMessages() {
        recoverStream(
                RedisStreamConfig.STREAM_KEY,
                RedisStreamConfig.GROUP_NAME,
                RedisStreamConfig.CONSUMER_NAME,
                "[TICKET-STREAM-RECOVERY]",
                ticketEmailConsumer::onMessage);
    }

    /**
     * Định kỳ quét PEL cho Auth Verify Email Stream mỗi 1 phút.
     * Tách riêng độc lập với Ticket Stream để không bị chặn lẫn nhau.
     */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "recoverAuthVerifyStreamJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void recoverAuthVerifyPendingMessages() {
        recoverStream(
                RedisStreamConfig.AUTH_STREAM_KEY,
                RedisStreamConfig.AUTH_GROUP_NAME,
                RedisStreamConfig.AUTH_CONSUMER_NAME,
                "[AUTH-STREAM-RECOVERY]",
                authVerifyEmailConsumer::onMessage);
    }

    /**
     * Định kỳ quét PEL cho Auth Forgot Password Stream mỗi 1 phút.
     * Tách riêng độc lập để không bị chặn lẫn nhau.
     */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "recoverForgotPasswordStreamJob", lockAtMostFor = "PT50S", lockAtLeastFor = "PT5S")
    public void recoverForgotPasswordPendingMessages() {
        recoverStream(
                RedisStreamConfig.FORGOT_PASSWORD_STREAM_KEY,
                RedisStreamConfig.FORGOT_PASSWORD_GROUP_NAME,
                RedisStreamConfig.FORGOT_PASSWORD_CONSUMER_NAME,
                "[FORGOT-PASSWORD-STREAM-RECOVERY]",
                authForgotPasswordConsumer::onMessage);
    }

    private void recoverStream(
            String streamKey,
            String groupName,
            String consumerName,
            String logPrefix,
            java.util.function.Consumer<MapRecord<String, String, String>> consumerCallback) {
        try {
            PendingMessages pendingMessages = stringRedisTemplate.opsForStream().pending(
                    streamKey,
                    Consumer.from(groupName, consumerName),
                    Range.unbounded(),
                    50);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            for (PendingMessage msg : pendingMessages) {
                // 1. Kiểm tra số lần thử lại để tránh Poison Pill
                if (msg.getTotalDeliveryCount() >= MAX_DELIVERY_COUNT) {
                    log.error("{} Tin nhắn ID={} đã thử lại {} lần thất bại -> ACK và loại bỏ để tránh nghẽn PEL",
                            logPrefix, msg.getIdAsString(), msg.getTotalDeliveryCount());
                    stringRedisTemplate.opsForStream().acknowledge(streamKey, groupName, msg.getId());
                    continue;
                }

                // 2. Kiểm tra Idle time: Chỉ xử lý tin nhắn đã bất động >= MIN_IDLE_MINUTES
                // Điều này ĐẢM BẢO KHÔNG BAO GIỜ đụng vào tin nhắn đang được consumer xử lý
                // bình thường
                Duration idleTime = msg.getElapsedTimeSinceLastDelivery();
                if (idleTime.toMinutes() >= MIN_IDLE_MINUTES) {
                    log.info("{} Phát hiện tin Pending cần xử lý lại: ID={}, idle={}s, deliveryCount={}",
                            logPrefix, msg.getIdAsString(), idleTime.toSeconds(), msg.getTotalDeliveryCount());

                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().range(
                            streamKey,
                            Range.closed(msg.getIdAsString(), msg.getIdAsString()));

                    if (records != null && !records.isEmpty()) {
                        MapRecord<String, Object, Object> rawRec = records.get(0);
                        Map<String, String> converted = new HashMap<>();
                        rawRec.getValue().forEach((k, v) -> converted.put(String.valueOf(k), String.valueOf(v)));
                        MapRecord<String, String, String> typedRecord = MapRecord.create(rawRec.getStream(), converted)
                                .withId(rawRec.getId());

                        consumerCallback.accept(typedRecord);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("{} Lỗi khi quét PEL: {}", logPrefix, e.getMessage());
        }
    }
}
