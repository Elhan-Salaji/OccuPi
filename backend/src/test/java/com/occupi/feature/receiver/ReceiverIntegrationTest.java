package com.occupi.feature.receiver;

import com.influxdb.v3.client.InfluxDBClient;
import com.occupi.AppApplication;
import com.occupi.feature.receiver.dto.SensorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Integration test for {@link Receiver}.
 *
 * Simulates a STOMP client (e.g. Raspberry Pi) connecting over WebSocket
 * and sending sensor data to /app/data. Verifies that the data reaches
 * {@link SensorDataService#process(SensorData)}.
 *
 * Uses a real Spring Boot context on a random port to avoid port conflicts.
 *
 * Note: Payloads are sent as raw JSON strings (StringMessageConverter) to avoid
 * dependency on deprecated Jackson 2 message converters. Spring Boot 4 uses
 * Jackson 3 (tools.jackson) on the server side for deserialization.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = AppApplication.class
)
@Import(ReceiverIntegrationTest.TestSecurityConfig.class)
@ActiveProfiles("test")
class ReceiverIntegrationTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
    }


    @LocalServerPort
    int port;

    // Prevents the real InfluxDBClient from being instantiated during context load.
    // The influxdb3-java client uses Apache Arrow + Netty internally, which causes
    // an UnsupportedOperationException on initialization in the test environment.
    @MockitoBean
    InfluxDBClient influxDBClient;

    // Mock the service so we can verify it was called without needing a real DB
    @MockitoBean
    SensorDataService sensorDataService;

    WebSocketStompClient stompClient;
    JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        // Raw WebSocket client — matches how stomp.py connects (no SockJS)
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        // Send payloads as plain JSON strings — server deserializes via Jackson 3
        stompClient.setMessageConverter(new StringMessageConverter());

        // Jackson 3 JsonMapper for serializing SensorData in tests
        // Dates are ISO-8601 by default in Jackson 3, no extra module needed
        jsonMapper = JsonMapper.builder().build();
    }

    /**
     * Happy path: a valid SensorData payload sent over STOMP reaches the service.
     */
    @Test
    void shouldForwardSensorDataToService() throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();

        SensorData data = new SensorData("room-01", "sensor-01", 3, 0.9, Instant.now());
        String payload = jsonMapper.writeValueAsString(data);

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        session.send("/app/data", payload);
                        connected.complete(null);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable ex) {
                        connected.completeExceptionally(ex);
                    }
                }
        );

        connected.get(5, TimeUnit.SECONDS);

        // timeout(2000): Mockito polls up to 2s — avoids flaky Thread.sleep()
        verify(sensorDataService, timeout(2000)).process(any(SensorData.class));
    }

    /**
     * Zero-count: a valid payload with count=0 (room empty) still reaches the service.
     * The service must not silently drop zero-count readings.
     */
    @Test
    void shouldForwardZeroCountToService() throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();

        SensorData data = new SensorData("room-01", "sensor-01", 0, 1.0, Instant.now());
        String payload = jsonMapper.writeValueAsString(data);

        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        session.send("/app/data", payload);
                        connected.complete(null);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable ex) {
                        connected.completeExceptionally(ex);
                    }
                }
        );

        connected.get(5, TimeUnit.SECONDS);

        verify(sensorDataService, timeout(2000)).process(any(SensorData.class));
    }
}
