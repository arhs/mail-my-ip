package com.spikeseed.arhs.corporate.tools.ipmailer;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * This class activate:
 * <ul>
 * <li>The spring boot application feature</li>
 * <li>The scheduling feature to use the annotation @Scheduled(cron = "0 * * * * ?")</li>
 * <li>The configuration properties features to use the annotation @ConfigurationProperties</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class AppConfig {
}
