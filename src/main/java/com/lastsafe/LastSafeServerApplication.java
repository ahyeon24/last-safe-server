package com.lastsafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LastSafeServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LastSafeServerApplication.class, args);
	}

}
