package com.spikeseed.arhs.corporate.tools.ipmailer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the IP Mailer application.
 * @version 1.0
 */
@SpringBootApplication
public class Starter {
    /**
     * The main method.
     * @param args The arguments
     */
    public static void main(final String[] args) {
        SpringApplication springApplication = new SpringApplication(AppConfig.class);
        springApplication.run(args);
    }
}
