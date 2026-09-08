package com.limio.api;

import org.springframework.boot.SpringApplication;

public class TestLimioApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LimioApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
