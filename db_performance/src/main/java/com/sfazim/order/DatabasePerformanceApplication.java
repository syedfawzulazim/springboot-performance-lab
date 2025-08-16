package com.sfazim.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class DatabasePerformanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatabasePerformanceApplication.class, args);
	}

}
