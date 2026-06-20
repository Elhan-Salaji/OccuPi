package com.occupi.feature.receiver;

import com.occupi.feature.receiver.dto.SensorData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.occupi.feature.receiver.Receiver;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReceiverTest {

    @Mock
    private SensorDataService sensorDataService;

    @InjectMocks
    private Receiver receiver;

    @Test
    void shouldForwardParsedDataToService() {
        SensorData data = new SensorData(
                "room-42",
                "sensor-7",
                3,
                0.95,
                Instant.parse("2026-04-13T10:30:00Z")
        );

        receiver.receive(data);

        verify(sensorDataService).process(data);
    }

    @Test
    void shouldHandleZeroCount() {
        SensorData data = new SensorData(
                "room-1",
                "sensor-1",
                0,
                1.0,
                Instant.now()
        );

        receiver.receive(data);

        verify(sensorDataService).process(data);
    }

    @Test
    void shouldNotProcessWhenDataIsNull() {
        receiver.receive(null);

        verifyNoInteractions(sensorDataService);
    }
}
