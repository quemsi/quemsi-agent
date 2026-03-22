package com.quemsi.agent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.api.QuemsiApi;
import com.quemsi.agent.api.TokenApi;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration(proxyBeanMethods = false)
public class ApiClientConfig {
    @Value("${quemsi-api.server-url}")
    private String serverUrl;
    @Value("${quemsi-api.keycloak-url}")
    private String keycloakUrl;
    @Value("${quemsi-api.log-request-detail:false}")
    private boolean logRequestDetails;
    
    
    @Bean
    public ApiManager apiManager(TokenApi tokenApi, QuemsiApi quemsiApi){
        return new ApiManager(tokenApi, quemsiApi);
    }

    @Bean
    public QuemsiApi quemsiApi(HttpServiceProxyFactory apiServiceProxyFactory){
        return apiServiceProxyFactory.createClient(QuemsiApi.class);
    }

    @Bean
    public HttpServiceProxyFactory apiServiceProxyFactory(WebClientAdapter apiExchangeAdapter) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(apiExchangeAdapter).build();
        return factory;
    }

    @Bean
    public WebClientAdapter apiExchangeAdapter(WebClient apiWebClient) {
        WebClientAdapter webClientAdapter = WebClientAdapter.create(apiWebClient);
        webClientAdapter.setBlockTimeout(Duration.ofSeconds(30));
        return webClientAdapter;
    }

    @Bean
    public WebClient apiWebClient(ReactorClientHttpConnector clientConnector, ObjectMapper objectMapper) {
        WebClient webClient = WebClient
                .builder()
                .clientConnector(clientConnector)
                .codecs(configurer -> configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON)))
                .codecs(configurer -> configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON)))
                .codecs(configurer -> configurer.defaultCodecs().enableLoggingRequestDetails(logRequestDetails))
                .baseUrl(serverUrl)
                .build();
        
        return webClient;
    }

    @Bean
    public TokenApi keycloakTokenApi(HttpServiceProxyFactory keycloakServiceProxyFactory){
        return keycloakServiceProxyFactory.createClient(TokenApi.class);
    }
    
    @Bean
    public HttpServiceProxyFactory keycloakServiceProxyFactory(WebClientAdapter keycloakExchangeAdapter) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(keycloakExchangeAdapter).build();
        return factory;
    }

    @Bean
    public WebClientAdapter keycloakExchangeAdapter(WebClient keycloakWebClient) {
        WebClientAdapter webClientAdapter = WebClientAdapter.create(keycloakWebClient);
        webClientAdapter.setBlockTimeout(Duration.ofSeconds(30));
        return webClientAdapter;
    }

    @Bean
    public WebClient keycloakWebClient(ReactorClientHttpConnector clientConnector) {
        WebClient webClient = WebClient
                .builder()
                .clientConnector(clientConnector)
                .codecs(configurer -> configurer.defaultCodecs().enableLoggingRequestDetails(logRequestDetails))
                .baseUrl(keycloakUrl)
                .build();
        return webClient;
    }

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider connectionProvider() {
        return ConnectionProvider.builder("fixed")
            .maxConnections(50)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build();
    }

    @Bean
    public ReactorClientHttpConnector clientConnector(ConnectionProvider connectionProvider) {
        HttpClient httpClient = HttpClient.create(connectionProvider)
            .responseTimeout(Duration.ofSeconds(30));
        return new ReactorClientHttpConnector(httpClient);
    }
}
