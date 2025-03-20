package com.quemsi.agent.api;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.DataVersion;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.FlowExecution.FlowExecutionStep;
import com.quemsi.model.dto.agent.AgentCommand;

public interface QuemsiApi {
    @GetExchange("/api/agent/all-model")
    AgentModel allModel(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
        @RequestParam(required = false) String agentVersion);
    
    @GetExchange("/api/agent/next-command")
    AgentCommand nextCommand(@RequestHeader(HttpHeaders.AUTHORIZATION) String token);
    
    @PostExchange("/api/agent/initate/{flowName}")
    FlowExecution initiate(@RequestHeader(HttpHeaders.AUTHORIZATION) String token, @PathVariable String flowName, @RequestParam Map<String, String> tags);
    
    @PostExchange("/api/agent/flow-execution")
    FlowExecution saveFlowExecution(@RequestHeader(HttpHeaders.AUTHORIZATION) String token, @RequestBody FlowExecution execution);
    
    @PostExchange("/api/agent/flow-execution-step")
    FlowExecutionStep saveFlowExecutionStep(@RequestHeader(HttpHeaders.AUTHORIZATION) String token, @RequestBody FlowExecutionStep executionStep);
    
    @GetExchange("/api/agent/gdrive-credentials")
    String googleCredential(@RequestHeader(HttpHeaders.AUTHORIZATION) String token);
    
    @PostExchange("/api/agent/agent-command")
    void send(@RequestHeader(HttpHeaders.AUTHORIZATION) String token, @RequestBody AgentCommand command);
    
    @GetExchange("/api/agent/find-version/{flowName}")
    DataVersion findVersion(@RequestHeader(HttpHeaders.AUTHORIZATION) String token, @PathVariable String flowName, @RequestParam Map<String, String> tags);
}
