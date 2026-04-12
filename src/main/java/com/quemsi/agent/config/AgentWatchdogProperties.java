package com.quemsi.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Idle watchdog: time since last successful {@code nextCommand()} response from the API.
 * When {@link #enabled}, {@code timeout} must exceed worst-case single-command execution,
 * because no new API response arrives while a command runs.
 */
@Data
@ConfigurationProperties(prefix = "agent.watchdog")
public class AgentWatchdogProperties {

    private boolean enabled = false;

    private Duration timeout = Duration.ofMinutes(30);
}
