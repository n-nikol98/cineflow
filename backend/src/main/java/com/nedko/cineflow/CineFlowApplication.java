package com.nedko.cineflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CineFlowApplication {

    public static void main(final String[] args) {
        SpringApplication.run(CineFlowApplication.class, args);
    }
}
