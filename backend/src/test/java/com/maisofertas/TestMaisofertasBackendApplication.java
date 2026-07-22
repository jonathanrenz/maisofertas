package com.maisofertas;

import org.springframework.boot.SpringApplication;

public class TestMaisofertasBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(MaisofertasBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
