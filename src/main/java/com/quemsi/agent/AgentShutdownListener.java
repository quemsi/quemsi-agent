package com.quemsi.agent;

import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import reactor.netty.resources.ConnectionProvider;

/**
 * Cancels in-flight HTTP (command loop) and stops the executor early so SIGINT / systemd stop
 * does not wait for multi-minute WebClient read timeouts.
 */
@Component
public class AgentShutdownListener {

    private final AgentCoordinator agentCoordinator;
    private final ConnectionProvider connectionProvider;
    private final ExecutorService vThreadExecutor;

    public AgentShutdownListener(
            AgentCoordinator agentCoordinator,
            ConnectionProvider connectionProvider,
            @Qualifier("vThreadExecutor") ExecutorService vThreadExecutor) {
        this.agentCoordinator = agentCoordinator;
        this.connectionProvider = connectionProvider;
        this.vThreadExecutor = vThreadExecutor;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        agentCoordinator.stopCommandLoop();
        connectionProvider.dispose();
        vThreadExecutor.shutdownNow();
    }
}
