package com.occupi;

import com.influxdb.v3.client.InfluxDBClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AppApplicationTests {

	// Prevents the real InfluxDBClient from being instantiated during context load.
	// The influxdb3-java client uses Apache Arrow + Netty internally, which causes
	// an UnsupportedOperationException on initialization in the test environment.
	@MockitoBean
	InfluxDBClient influxDBClient;

	@Test
	void contextLoads() {
	}

}
