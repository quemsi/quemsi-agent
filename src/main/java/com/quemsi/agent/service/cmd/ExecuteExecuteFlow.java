package com.quemsi.agent.service.cmd;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.FlowExecutionStatus;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.flow.Flow;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteExecuteFlow {
    @Autowired
    private FlowManager flowManager;
    @Autowired
    private ApiManager apiManager;
    
    public void execute(ExecuteFlow cmd){
        log.info("executing flow {}", cmd);
        FlowExecution execution = null;
        String erroMessage = null;
        try{
            Flow flow = flowManager.findByName(cmd.getFlowName()).orElseThrow(Exceptions.notFound("invalid-flow-name").withExtra("flowName", cmd.getFlowName()).supplier());
            execution = flow.execute(cmd.getVersionId(), cmd.getTags(), cmd.getFiles(), cmd.getFlowExecutionId());
            if(execution != null){
                log.info("saving history {}", execution);
                execution = apiManager.saveFlowExecution(execution);
            }
        }catch(BaseRuntimeException e){
            erroMessage = "Flow is not ready to run! Check Errors to see previous failures";
        }catch(Exception e){
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            erroMessage = sw.toString();
        }
        
        if(execution == null){
            execution = new FlowExecution();
            execution.setId(cmd.getFlowExecutionId());
            execution.setActive(true);
            execution.setFlowName(cmd.getFlowName());
            execution.setStatus(FlowExecutionStatus.FAILED);
            execution.setLogs(erroMessage);
        }
        apiManager.saveFlowExecution(execution);
    }
}
