package com.quemsi.agent.service.cmd;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.flow.Flow;

public class ExecuteExecuteFlow {
    @Autowired
    private FlowManager flowManager;
    @Autowired
    private ApiManager apiManager;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;
    
    public void execute(ExecuteFlow cmd){
        agentBatchedLogger.logInfo(null, null, LogMessage.info("executing flow {}", cmd));
        FlowExecution execution = null;
        try{
            Flow flow = flowManager.findByName(cmd.getFlowName()).orElseThrow(Exceptions.notFound("invalid-flow-name").withExtra("flowName", cmd.getFlowName()).supplier());
            execution = flow.execute(cmd.getVersionId(), cmd.getTags(), cmd.getFiles(), cmd.getFlowExecutionId());
            if(execution != null){
                execution = apiManager.saveFlowExecution(execution);
            }
        }
        finally{
        }
        if(execution == null){
            throw Exceptions.server("flow-execution-null").withExtra("flowExecutionId", cmd.getFlowExecutionId()).withExtra("flowName", cmd.getFlowName()).get();
        }
    }
}
