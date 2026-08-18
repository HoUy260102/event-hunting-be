package com.example.event.config;

import com.example.event.component.AuthVerifyEmailConsumer;
import com.example.event.component.TicketEmailConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    public static final String STREAM_KEY  = "stream:ticket-email";
    public static final String GROUP_NAME  = "ticket-email-group";
    public static final String CONSUMER_NAME = "consumer-1";

    public static final String AUTH_STREAM_KEY  = "stream:auth-verify-email";
    public static final String AUTH_GROUP_NAME  = "auth-verify-email-group";
    public static final String AUTH_CONSUMER_NAME = "auth-verify-consumer-1";
    
    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
    ticketStreamContainer(RedisConnectionFactory factory) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> opts =
                StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .serializer(new StringRedisSerializer())
                        .build();

        return StreamMessageListenerContainer.create(factory, opts);
    }

    @Bean
    public ApplicationRunner ticketStreamInit(
            StringRedisTemplate stringRedisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> ticketStreamContainer,
            TicketEmailConsumer ticketEmailConsumer) {
        return args -> {

            // 1. Đảm bảo khóa stream tồn tại (Redis cần stream trước khi tạo group)
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(STREAM_KEY))) {
                Map<String, String> body = new HashMap<>();
                body.put("_init", "1");
                stringRedisTemplate.opsForStream()
                        .add(STREAM_KEY, body);
                log.info("[TICKET-STREAM] Đã tạo stream '{}'", STREAM_KEY);
            }

            // 2. Tạo consumer group (bỏ qua lỗi BUSYGROUP nếu group đã tồn tại)
            try {
                stringRedisTemplate.opsForStream()
                        .createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP_NAME);
                log.info("[TICKET-STREAM] Đã tạo consumer group '{}'", GROUP_NAME);
            } catch (Exception e) {
                log.info("[TICKET-STREAM] Consumer group '{}' đã tồn tại — OK", GROUP_NAME);
            }

            // 3. Đăng ký consumer — ACK thủ công để lỗi vẫn nằm trong Pending
            ticketStreamContainer.receive(
                    Consumer.from(GROUP_NAME, CONSUMER_NAME),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    ticketEmailConsumer
            );

            // 4. Khởi động vòng lặp polling
            ticketStreamContainer.start();
            log.info("[TICKET-STREAM] StreamMessageListenerContainer đã được khởi động");
        };
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
    authStreamContainer(RedisConnectionFactory factory) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> opts =
                StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .serializer(new StringRedisSerializer())
                        .build();

        return StreamMessageListenerContainer.create(factory, opts);
    }

    @Bean
    public ApplicationRunner authStreamInit(
            StringRedisTemplate stringRedisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> authStreamContainer,
            AuthVerifyEmailConsumer authVerifyEmailConsumer) {

        return args -> {

            // 1. Đảm bảo khóa stream tồn tại
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(AUTH_STREAM_KEY))) {
                Map<String, String> body = new HashMap<>();
                body.put("_init", "1");
                stringRedisTemplate.opsForStream()
                        .add(AUTH_STREAM_KEY, body);
                log.info("[AUTH-STREAM] Đã tạo stream '{}'", AUTH_STREAM_KEY);
            }

            // 2. Tạo consumer group
            try {
                stringRedisTemplate.opsForStream()
                        .createGroup(AUTH_STREAM_KEY, ReadOffset.from("0-0"), AUTH_GROUP_NAME);
                log.info("[AUTH-STREAM] Đã tạo consumer group '{}'", AUTH_GROUP_NAME);
            } catch (Exception e) {
                log.info("[AUTH-STREAM] Consumer group '{}' đã tồn tại — OK", AUTH_GROUP_NAME);
            }

            // 3. Đăng ký consumer
            authStreamContainer.receive(
                    Consumer.from(AUTH_GROUP_NAME, AUTH_CONSUMER_NAME),
                    StreamOffset.create(AUTH_STREAM_KEY, ReadOffset.lastConsumed()),
                    authVerifyEmailConsumer
            );

            // 4. Khởi động
            authStreamContainer.start();
            log.info("[AUTH-STREAM] StreamMessageListenerContainer đã được khởi động");
        };
    }
}
