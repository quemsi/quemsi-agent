package com.biddflux.agent.config;

import java.util.List;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.biddflux.agent.api.ApiClientReactive;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class ApiClientConfig {
    @Bean
    public ApiClientReactive apiClient(){
        return new ApiClientReactive();
    }
    
    
    @Bean
    public WebClient webClient(final @Value("${oauth2.registration.id}") String oauth2RegistrationId,
                                   final @Value("${resource.base}") String resourceBase,
                                   final ClientRegistrationRepository clientRegistrationRepository) {
        var defaultClientCredentialsTokenResponseClient = new DefaultClientCredentialsTokenResponseClient();
        defaultClientCredentialsTokenResponseClient
                .setRestOperations(getRestTemplateForTokenEndPoint(oauth2RegistrationId));

        var provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(c -> c.accessTokenResponseClient(defaultClientCredentialsTokenResponseClient))
                .build();

        var oauth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

        var authorizedClientServiceOAuth2AuthorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oauth2AuthorizedClientService);
        authorizedClientServiceOAuth2AuthorizedClientManager.setAuthorizedClientProvider(provider);
        
        var oauth = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientServiceOAuth2AuthorizedClientManager);
        oauth.setDefaultClientRegistrationId(oauth2RegistrationId);

        return WebClient.builder()
                .baseUrl(resourceBase)
                .filter(oauth)
                .filter(logResourceRequest(log, oauth2RegistrationId))
                .filter(logResourceResponse(log, oauth2RegistrationId))
                .build();
    }

    private RestTemplate getRestTemplateForTokenEndPoint(String oauth2RegistrationId) {
        var restTemplateForTokenEndPoint = new RestTemplate();
        restTemplateForTokenEndPoint
                .setMessageConverters(
                        List.of(new FormHttpMessageConverter(),
                                new OAuth2AccessTokenResponseHttpMessageConverter()
                        ));
        restTemplateForTokenEndPoint
                .setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        restTemplateForTokenEndPoint
                .setInterceptors(List.of(restTemplateRequestInterceptor(oauth2RegistrationId)));
        return restTemplateForTokenEndPoint;
    }

    private static ExchangeFilterFunction logResourceRequest(final Logger logger, final String clientName) {
        return ExchangeFilterFunction.ofRequestProcessor(c -> {
            logger.trace(
                "For Client {}, Sending OAUTH2 protected Resource Request to {}: {}",
                clientName, c.method(), c.url()
            );
            return Mono.just(c);
        });
    }

    private static ExchangeFilterFunction logResourceResponse(final Logger logger, final String clientName) {
        return ExchangeFilterFunction.ofResponseProcessor(c -> {
            log.trace("For Client {}, OAUTH2 protected Resource Response status: {}", clientName, c.statusCode());
            return Mono.just(c);
        });
    }

    private static ClientHttpRequestInterceptor restTemplateRequestInterceptor(final String clientName) {
        return (request, body, execution) -> {
            log.info("For Client {}, Sending OAUTH2 Token Request to {}", clientName, request.getURI());
            var clientResponse = execution.execute(request, body);
            log.info("For Client {}, OAUTH2 Token Response: {}", clientName, clientResponse.getStatusCode());
            return clientResponse;
        };
    }
}
