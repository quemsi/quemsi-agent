package com.biddflux.agent.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.biddflux.model.dto.AgentModel;
import com.biddflux.model.dto.DataVersion;
import com.biddflux.model.dto.FlowHistory;
import com.biddflux.model.dto.agent.AgentCommand;

public class ApiClientReactive implements ApiClient{
    @Autowired
    private WebClient webClient;
    
    @Override
    public AgentModel allModel(String agentVersion) {
        return webClient.get().uri(uriBuilder -> uriBuilder.path("/api/agent/all-model").queryParam("agentVersion", agentVersion).build()).retrieve().bodyToMono(AgentModel.class).block();
    }

    @Override
    public AgentCommand nextCommand() {
        return webClient.get().uri("/api/agent/next-command").retrieve().bodyToMono(AgentCommand.class).block();
    }

    @Override
    public FlowHistory saveFlowHistory(FlowHistory flowHistory) {
        return webClient.post().uri("/api/agent/flow-history").body(BodyInserters.fromValue(flowHistory)).retrieve().bodyToMono(FlowHistory.class).block();
    }

    @Override
    public String googleCredential(){
        return webClient.get().uri("/api/agent/gdrive-credentials").retrieve().bodyToMono(String.class).block();
    }

    @Override
    public void send(AgentCommand command) {
        webClient.post().uri("/api/agent/agent-command").body(BodyInserters.fromValue(command)).retrieve().toBodilessEntity().block();
    }

    @Override
    public DataVersion findVersion(String flowName, Map<String, String> tags) {
        return webClient.get().uri( uriBuilder -> uriBuilder.path("/api/agent/find-version/" + flowName).build(tags)).retrieve().bodyToMono(DataVersion.class).block();
    }
}
