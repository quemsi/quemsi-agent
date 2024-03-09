package com.biddflux.agent.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.biddflux.commons.persistence.KeyValue;
import com.biddflux.model.dto.AgentModel;
import com.biddflux.model.dto.FlowHistory;
import com.biddflux.model.dto.agent.AgentCommand;

public class ApiClientReactive implements ApiClient{
    @Autowired
    private WebClient webClient;
    
    @Override
    public AgentModel allModel() {
        return webClient.get().uri("/api/agent/all-model") .retrieve().bodyToMono(AgentModel.class).block();
    }

    @Override
    public AgentCommand nextCommand() {
        KeyValue n = new KeyValue();
        n.setKey("agent");
        n.setValue("as json");
        webClient.post().uri("/api/pub/enums/keyval").body(BodyInserters.fromValue(n)).retrieve().toBodilessEntity().block();
        webClient.post().uri("/api/agent/keyval").body(BodyInserters.fromValue(n)).retrieve().toBodilessEntity().block();
        return webClient.get().uri("/api/agent/next-command") .retrieve().bodyToMono(AgentCommand.class).block();
    }

    @Override
    public FlowHistory saveFlowHistory(FlowHistory flowHistory) {
        return webClient.post().uri("/api/agent/flow-history").body(BodyInserters.fromValue(flowHistory)).retrieve().bodyToMono(FlowHistory.class).block();
    }
}
