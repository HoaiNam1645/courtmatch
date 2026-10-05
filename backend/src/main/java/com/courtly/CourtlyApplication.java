package com.courtly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// Gui email chay ngoai luong xu ly request - xem MailService
@EnableAsync
public class CourtlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourtlyApplication.class, args);
    }
}
