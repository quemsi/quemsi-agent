package com.biddflux.agent.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.biddflux.model.dto.AgentModel;
import com.biddflux.model.dto.FlowHistory;
import com.biddflux.model.dto.agent.AgentCommand;

public interface ApiClient {
    @GetMapping("/api/agent/all-model")
    AgentModel allModel();
    @GetMapping("/api/agent/next-command")
    AgentCommand nextCommand();
    @PostMapping("/api/agent/next-command")
    FlowHistory saveFlowHistory(FlowHistory history);
    @GetMapping("/api/agent/gdrive-credentials")
    String googleCredential();
    @PostMapping("/api/agent/agent-command")
    void send(AgentCommand command);
}

