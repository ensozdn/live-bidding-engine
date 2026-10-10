package com.bidding.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BiddingCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(BiddingCoreApplication.class, args);
	}

}
