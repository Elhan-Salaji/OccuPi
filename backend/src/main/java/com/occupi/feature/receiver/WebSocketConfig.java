package com.occupi.feature.receiver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * WebSocket configuration for STOMP messaging.
 *
 * Enables real-time, bidirectional communication between Raspberry Pi
 * and the Spring Boot backend via WebSocket with STOMP protocol.
 *
 * Configuration:
 * - Endpoint: /ws (with SockJS fallback)
 * - Message broker: in-memory (suitable for single-instance deployments)
 * - Application prefix: /app (for @MessageMapping routes)
 * - User destination prefix: /user (for per-user messaging)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Jackson 3 ObjectMapper auto-configured by Spring Boot 4.
     * Used in the custom STOMP message converter for proper Instant support.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Registers a custom STOMP message converter backed by the Spring Boot
     * auto-configured Jackson 3 ObjectMapper.
     *
     * By resolving the content-type to application/json regardless of what the
     * client sends, this converter handles stomp.py clients (Raspberry Pi) that
     * omit the content-type header and send plain-text JSON payloads.
     *
     * Jackson 3 has built-in java.time support, so Instant deserialization works
     * without any additional module registration.
     */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(new Jackson3StompConverter(objectMapper));
        return true;
    }

    /**
     * Custom STOMP message converter that uses the Spring Boot auto-configured
     * Jackson 3 ObjectMapper. Accepts any content-type and treats the body as JSON.
     */
    private static class Jackson3StompConverter extends AbstractMessageConverter {

        private final ObjectMapper mapper;

        Jackson3StompConverter(ObjectMapper mapper) {
            super(MimeTypeUtils.APPLICATION_JSON, new MimeType("text", "plain"));
            this.mapper = mapper;
        }

        @Override
        protected boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
            try {
                Object payload = message.getPayload();
                if (payload instanceof byte[] bytes) {
                    return mapper.readValue(bytes, targetClass);
                }
                if (payload instanceof String str) {
                    return mapper.readValue(str, targetClass);
                }
                return null;
            } catch (JacksonException e) {
                return null;
            }
        }

        @Override
        protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
            try {
                return mapper.writeValueAsBytes(payload);
            } catch (JacksonException e) {
                return null;
            }
        }
    }

    /**
     * Configures the WebSocket STOMP endpoints.
     *
     * Two endpoints are registered:
     * - /ws          — raw WebSocket (no SockJS) for native clients such as Raspberry Pi
     *                  and stomp.py. Also used by integration tests via StandardWebSocketClient.
     * - /ws/occupancy — SockJS-enabled endpoint for browser-based clients that may lack
     *                  native WebSocket support.
     *
     * @param registry the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket endpoint for native clients (Raspberry Pi / stomp.py)
        registry
                .addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "https://occupi.mi.hdm-stuttgart.de"
                );

        // SockJS endpoint for browser clients
        registry
                .addEndpoint("/ws/occupancy")
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "https://occupi.mi.hdm-stuttgart.de"
                )
                .withSockJS();
    }

    /**
     * Configures the message broker for routing messages between clients.
     *
     * - Application destination prefix: /app
     *   Routes @MessageMapping handlers in @Controller classes
     *   Example: /app/data → receives via @MessageMapping("/data")
     *
     * - Broker destination prefix: /topic and /queue
     *   /topic: broadcast to multiple subscribers
     *   /queue: point-to-point messaging
     *
     * - User destination prefix: /user
     *   Enables per-user messaging via convertAndSendToUser()
     *
     * @param config the message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();

        config
                .enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{25000, 25000})
                .setTaskScheduler(scheduler);

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
}
