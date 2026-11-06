package com.orderflow.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class NotificationConfiguration {
    @Bean
    Clock notificationClock() {
        return Clock.systemUTC();
    }
}
