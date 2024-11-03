package com.quemsi.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AgentApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    private AgentCoordinator agentCoordinator;
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("agent started : {}", "2.0.0-SNAPSHOT");
        log.info("agent coordinator : {}", agentCoordinator);
        agentCoordinator.start();
    }
}
