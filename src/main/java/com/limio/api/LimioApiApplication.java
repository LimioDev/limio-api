package com.limio.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LimioApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LimioApiApplication.class, args);
	}

}
