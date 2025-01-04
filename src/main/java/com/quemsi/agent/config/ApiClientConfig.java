package com.quemsi.agent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.api.QuemsiApi;
import com.quemsi.agent.api.TokenApi;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class ApiClientConfig {
    @Value("${quemsi-api.server-url}")
    private String serverUrl;
    @Value("${quemsi-api.log-request-detail:false}")
    private boolean logRequestDetails;
    
    @Bean
    public ApiManager apiManager(TokenApi tokenApi, QuemsiApi quemsiApi){
        return new ApiManager(tokenApi, quemsiApi);
    }

    @Bean
    public TokenApi keycloakTokenApi(HttpServiceProxyFactory apiServiceProxyFactory){
        return apiServiceProxyFactory.createClient(TokenApi.class);
    }
    @Bean
    public QuemsiApi quemsiApi(HttpServiceProxyFactory apiServiceProxyFactory){
        return apiServiceProxyFactory.createClient(QuemsiApi.class);
    }

    @Bean
    public HttpServiceProxyFactory apiServiceProxyFactory(WebClientAdapter apiExchangeAdapter) {
        log.info("HttpServiceProxyFactory is being initialized");
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(apiExchangeAdapter).build();
        log.info("apiServiceProxyFactory is initialized: {}", factory);
        return factory;
    }

    @Bean
    public WebClientAdapter apiExchangeAdapter(WebClient apiWebClient) {
        WebClientAdapter webClientAdapter = WebClientAdapter.create(apiWebClient);
        webClientAdapter.setBlockTimeout(Duration.ofSeconds(30));
        return webClientAdapter;
    }

    @Bean
    public WebClient apiWebClient(ReactorClientHttpConnector clientConnector) {
        log.info("Api webClient is being initialized");
        WebClient webClient = WebClient
                .builder()
                .clientConnector(clientConnector)
                .codecs(configurer -> configurer.defaultCodecs().enableLoggingRequestDetails(logRequestDetails))
                .baseUrl(serverUrl)
                .build();
        
        log.info("apiWebClient is initialized: {}", webClient);
        return webClient;
    }

    @Bean
    public ReactorClientHttpConnector clientConnector(){
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(30)); 
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        return connector;
    }
}
