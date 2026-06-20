package com.occupi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * Lives in the root package {@code com.occupi} so that component scanning,
 * JPA entity/repository scanning and auto-configuration package detection all
 * cover the {@code com.occupi.feature.*} packages out of the box — no explicit
 * base-package configuration needed.
 *
 * {@code @EnableScheduling} powers the batched occupancy flush (see OccupancyService).
 */
@SpringBootApplication
@EnableScheduling
public class AppApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppApplication.class, args);
	}

}
