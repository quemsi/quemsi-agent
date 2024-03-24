package com.biddflux.agent.aspect;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.stereotype.Component;

import com.biddflux.agent.api.ApiClient;
import com.biddflux.commons.util.BaseRuntimeException;
import com.biddflux.commons.util.Exceptions;
import com.biddflux.model.dto.agent.onapi.NotifyError;

@Aspect
// @Component
public class GlobalErrorHandling {
    @Autowired
	private ApiClient apiClient;
    
    @AfterThrowing(pointcut="execution(* com.biddflux.agent..*.*(..))", throwing="ex")
    public void handleError(Exception ex) {
        if(ex instanceof ClientAuthorizationException){
            return;
        }
        NotifyError.NotifyErrorBuilder builder = NotifyError.builder().entityName("agent").entityType("agent");
        if(ex instanceof BaseRuntimeException bre){
            builder.exception(bre);
        }else{
            builder.exception(Exceptions.server("unexpected-agent-error").withCause(ex).get());
        }
        apiClient.send(builder.build());
     }
}
