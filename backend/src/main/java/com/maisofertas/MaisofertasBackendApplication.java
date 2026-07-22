package com.maisofertas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MaisofertasBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaisofertasBackendApplication.class, args);
	}

}
