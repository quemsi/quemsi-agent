package com.quemsi.agent.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

import com.quemsi.agent.api.ApiManager.IdmToken;

public interface TokenApi {
    @PostExchange(url = "/idm/realms/{realm}/protocol/openid-connect/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    IdmToken getToken(@PathVariable("realm") String realm, @RequestParam("client_id") String clientId, @RequestParam("client_secret") String clientSecret, @RequestParam("grant_type") String grantType, @RequestParam("scope") String scope);
}
