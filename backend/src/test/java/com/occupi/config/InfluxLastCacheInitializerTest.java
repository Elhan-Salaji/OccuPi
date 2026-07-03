package com.occupi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InfluxLastCacheInitializer")
class InfluxLastCacheInitializerTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> response;

    private InfluxDBProperties properties;
    private InfluxLastCacheInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new InfluxDBProperties();
        properties.setUrl("http://influx.test:8181");
        properties.setDatabase("occupi");
        properties.setToken("");
        initializer = new InfluxLastCacheInitializer(properties, httpClient);
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int statusCode) throws Exception {
        when(response.statusCode()).thenReturn(statusCode);
        lenient().when(response.body()).thenReturn("");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    @Test
    @DisplayName("creates one cache per measurement against the configure endpoint")
    void createsBothCaches() throws Exception {
        stubResponse(201);

        initializer.createLastCaches();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        List<HttpRequest> requests = captor.getAllValues();
        for (HttpRequest request : requests) {
            assertEquals("http://influx.test:8181/api/v3/configure/last_cache",
                    request.uri().toString());
            assertEquals("POST", request.method());
            assertTrue(request.headers().firstValue("Authorization").isEmpty(),
                    "empty token must not produce an Authorization header");
        }
    }

    @Test
    @DisplayName("sends a bearer header when a token is configured")
    void sendsBearerWhenTokenSet() throws Exception {
        properties.setToken("secret");
        stubResponse(201);

        initializer.createLastCaches();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        assertEquals("Bearer secret",
                captor.getValue().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    @DisplayName("treats 409 (cache already exists) as success and still creates the second cache")
    void treats409AsSuccess() throws Exception {
        stubResponse(409);

        assertDoesNotThrow(() -> initializer.createLastCaches());

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("swallows unexpected HTTP errors so startup continues on the fallback path")
    void swallowsHttpErrors() throws Exception {
        stubResponse(500);

        assertDoesNotThrow(() -> initializer.createLastCaches());

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("swallows connection failures so startup continues on the fallback path")
    void swallowsConnectionFailure() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("connection refused"));

        assertDoesNotThrow(() -> initializer.createLastCaches());

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("does not retry caches that were created successfully")
    void retryStopsAfterSuccess() throws Exception {
        stubResponse(201);

        initializer.createLastCaches();
        initializer.retryMissingLastCaches();

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("retries failed creations until they succeed, then stops")
    void retryRetriesFailures() throws Exception {
        when(response.statusCode()).thenReturn(500, 500, 201, 201);
        lenient().when(response.body()).thenReturn("");
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenAnswer(inv -> response);

        initializer.createLastCaches();       // both fail (500)
        initializer.retryMissingLastCaches(); // both succeed (201)
        initializer.retryMissingLastCaches(); // nothing left to create

        verify(httpClient, times(4)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("cache definition carries key column, count 1 and the TTL in seconds")
    void cacheDefinitionJson() {
        String json = InfluxLastCacheInitializer.cacheDefinition(
                "occupi", "occupancy", "occupancy_latest_by_room", "roomId", Duration.ofDays(7));

        assertEquals("{\"db\": \"occupi\", \"table\": \"occupancy\", "
                + "\"name\": \"occupancy_latest_by_room\", \"key_columns\": [\"roomId\"], "
                + "\"count\": 1, \"ttl\": 604800}", json);
    }
}
