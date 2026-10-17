package io.github.leobej.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Here rather than on the application class (§28), so infrastructure owns it like every other switch.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
