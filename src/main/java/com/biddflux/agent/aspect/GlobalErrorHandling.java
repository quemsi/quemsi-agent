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
@Component
public class GlobalErrorHandling {
    @Autowired
	private ApiClient apiClient;
    
    @AfterThrowing(pointcut="execution(* com.biddflux.agent.service..*.*(..))", throwing="ex")
    public void onService(Exception ex) {
        handleError(ex);
    }
    public void handleError(Exception ex) {
        if(ex instanceof ClientAuthorizationException){
            return;
        }
        NotifyError.NotifyErrorBuilder notifyError = NotifyError.builder();
        if(ex instanceof BaseRuntimeException bre){
            notifyError.exception(bre);
        }else{
            notifyError.exception(Exceptions.server("unexpected-agent-error").onEntity("global", "agent").withCause(ex).get());
        }
        apiClient.send(notifyError.build());
     }
     
}
