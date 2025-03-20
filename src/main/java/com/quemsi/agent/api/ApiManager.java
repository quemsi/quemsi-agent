package com.quemsi.agent.api;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.api.ApiClient;
import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.DataVersion;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.FlowExecution.FlowExecutionStep;
import com.quemsi.model.dto.agent.AgentCommand;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiManager implements ApiClient{
    private static final String GRANT_TYPE = "client_credentials";
    private static final String SCOPE = "openid";
    private static final int EXPIRY_BUFFER = 60;
    @Value("${quemsi-api.realm}")
    private String realm;
    @Getter
    @Value("${quemsi-api.client-id}")
    private String clientId;
    @Value("${quemsi-api.client-secret}")
    private String clientSecret;
    
    private TokenApi tokenApi;
    private QuemsiApi quemsiApi;
    
    private CompletableFuture<IdmToken> tokenFuture;

    public ApiManager(TokenApi tokenApi, QuemsiApi quemsiApi){
        this.tokenApi = tokenApi;
        this.quemsiApi = quemsiApi;
    }

    @PostConstruct
    public void afterPropertiesSet(){
        refreshIdmTokenFuture();
    }

    public IdmToken idmToken(){
        IdmToken token = null;
        try {
            while(token == null || token.getValidUntil().isBefore(LocalDateTime.now())){
                token = tokenFuture.get();
                if(!token.getRefreshAfter().isAfter(LocalDateTime.now())){
                    refreshIdmTokenFuture();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw Exceptions.server("idm-failure").withCause(e).get();
        }
        return token;
    }
    public void refreshIdmTokenFuture(){
        tokenFuture = CompletableFuture.supplyAsync(()->{
            IdmToken token = tokenApi.getToken(realm, clientId, clientSecret, GRANT_TYPE, SCOPE);
            token.setValidUntil(LocalDateTime.now().plusSeconds(token.getExpiresIn()));
            token.setRefreshAfter(token.getValidUntil().minusSeconds(EXPIRY_BUFFER));
            return token;
        });
    }

    public String authHeader(){
        IdmToken token = idmToken();
        return new StringBuilder(token.getTokenType()).append(" ").append(token.getAccessToken()).toString();
    }

    

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class IdmToken{
        private String accessToken;
        private int expiresIn;
        private String tokenType;
        private LocalDateTime validUntil;
        private LocalDateTime refreshAfter;
    }



    @Override
    public AgentModel allModel(String agentVersion) {
        return quemsiApi.allModel(this.authHeader(), agentVersion);
    }

    @Override
    public AgentCommand nextCommand() {
        return quemsiApi.nextCommand(this.authHeader());
    }

    @Override
    public FlowExecution initiate(String flowName, Map<String, String> tags) {
        return quemsiApi.initiate(this.authHeader(), flowName, tags);
    }

    @Override
    public FlowExecution saveFlowExecution(FlowExecution execution) {
        return quemsiApi.saveFlowExecution(this.authHeader(), execution);
    }

    @Override
    public FlowExecutionStep saveFlowExecutionStep(FlowExecutionStep executionStep) {
        return quemsiApi.saveFlowExecutionStep(this.authHeader(), executionStep);
    }

    @Override
    public String googleCredential() {
        return quemsiApi.googleCredential(this.authHeader());
    }

    @Override
    public void send(AgentCommand command) {
        quemsiApi.send(this.authHeader(), command);
    }

    @Override
    public DataVersion findVersion(String flowName, Map<String, String> tags) {
        return quemsiApi.findVersion(this.authHeader(), flowName, tags);
    }
}
