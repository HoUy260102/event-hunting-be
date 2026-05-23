package com.example.event.config;

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
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    public static final String STREAM_KEY  = "stream:ticket-email";
    public static final String GROUP_NAME  = "ticket-email-group";
    public static final String CONSUMER_NAME = "consumer-1";

    // ---------------------------------------------------------------
    // Container bean — poll every 1 s, decode both key & hash fields
    // as plain UTF-8 strings so MapRecord<String,String,String> works.
    // ---------------------------------------------------------------
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

    // ---------------------------------------------------------------
    // On startup: ensure stream + group exist, then start listening.
    // "BUSYGROUP" → group already exists → safe to ignore.
    // ---------------------------------------------------------------
    @Bean
    public ApplicationRunner ticketStreamInit(
            StringRedisTemplate stringRedisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> ticketStreamContainer,
            TicketEmailConsumer ticketEmailConsumer) {

        return args -> {
            // 1. Ensure the stream key exists (Redis requires the stream before creating a group)
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(STREAM_KEY))) {
                stringRedisTemplate.opsForStream()
                        .add(STREAM_KEY, Map.of("_init", "1"));
                log.info("[TICKET-STREAM] Created stream '{}'", STREAM_KEY);
            }

            // 2. Create consumer group (ignore BUSYGROUP if already exists)
            try {
                stringRedisTemplate.opsForStream()
                        .createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP_NAME);
                log.info("[TICKET-STREAM] Created consumer group '{}'", GROUP_NAME);
            } catch (Exception e) {
                log.info("[TICKET-STREAM] Consumer group '{}' already exists — OK", GROUP_NAME);
            }

            // 3. Register consumer — manual ACK so failures stay in Pending
            ticketStreamContainer.receive(
                    Consumer.from(GROUP_NAME, CONSUMER_NAME),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    ticketEmailConsumer
            );

            // 4. Start the polling loop
            ticketStreamContainer.start();
            log.info("[TICKET-STREAM] StreamMessageListenerContainer started");
        };
    }
}
