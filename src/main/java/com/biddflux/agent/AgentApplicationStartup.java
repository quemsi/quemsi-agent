package com.biddflux.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class AgentApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {
    @Autowired
    private AgentCoordinator agentController;
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        agentController.start();
    }
}
