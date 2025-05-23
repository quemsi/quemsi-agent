package com.quemsi.agent.aspect;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.api.ApiClient;
import com.quemsi.model.dto.agent.onapi.NotifyError;

@Aspect
@Component
public class GlobalErrorHandling {
    @Autowired
	private ApiClient apiClient;
    
    @AfterThrowing(pointcut="execution(* com.quemsi.agent.service..*.*(..))", throwing="ex")
    public void onService(Exception ex) {
        handleError(ex);
    }
    public void handleError(Exception ex) {
        NotifyError.NotifyErrorBuilder notifyError = NotifyError.builder();
        if(ex instanceof BaseRuntimeException bre){
            notifyError.exception(bre);
        }else{
            notifyError.exception(Exceptions.server("unexpected-agent-error").onEntity("global", "agent").withCause(ex).get());
        }
        apiClient.send(notifyError.build());
     }
     
}
