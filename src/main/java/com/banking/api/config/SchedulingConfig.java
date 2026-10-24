package com.banking.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the fixed-delay jobs - currently the time-lock release sweep. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
