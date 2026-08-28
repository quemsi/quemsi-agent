package com.quemsi.agent.service.cmd;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.FlowExecutionStatus;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.flow.Flow;

public class ExecuteExecuteFlow {
    @Autowired
    private FlowManager flowManager;
    @Autowired
    private ApiManager apiManager;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;
    
    public void execute(ExecuteFlow cmd){
        Long executionId = cmd.getFlowExecutionId();
        agentBatchedLogger.logInfo(executionId, null, LogMessage.info("executing flow {}", cmd));
        try {
            Flow flow = flowManager.findByName(cmd.getFlowName()).orElseThrow(
                Exceptions.notFound("invalid-flow-name")
                    .onEntity("flow", cmd.getFlowName())
                    .withExtra("flowName", cmd.getFlowName())
                    .supplier());
            FlowExecution execution = flow.execute(cmd.getVersionId(), cmd.getTags(), cmd.getFiles(), executionId);
            if (execution != null) {
                apiManager.saveFlowExecution(execution);
            } else {
                agentBatchedLogger.logWarn(executionId, null, LogMessage.warn("flow is already running"));
                saveTerminalExecution(cmd, FlowExecutionStatus.SKIPPED);
            }
        } catch (Exception e) {
            agentBatchedLogger.logError(executionId, null, LogMessage.errorWithCause("failed-to-execute-flow", e));
            saveTerminalExecution(cmd, FlowExecutionStatus.FAILED);
            notifyFailure(cmd, e);
        }
    }

    private void saveTerminalExecution(ExecuteFlow cmd, FlowExecutionStatus status) {
        try {
            LocalDateTime now = LocalDateTime.now();
            FlowExecution execution = new FlowExecution();
            execution.setId(cmd.getFlowExecutionId());
            execution.setActive(true);
            execution.setFlowName(cmd.getFlowName());
            execution.setStatus(status);
            execution.setStartedAt(now);
            execution.setFinishedAt(now);
            apiManager.saveFlowExecution(execution);
        } catch (Exception saveEx) {
            agentBatchedLogger.logError(cmd.getFlowExecutionId(), null,
                LogMessage.errorWithCause("failed-to-save-flow-execution", saveEx));
        }
    }

    private void notifyFailure(ExecuteFlow cmd, Exception e) {
        BaseRuntimeException bre = e instanceof BaseRuntimeException b
            ? b
            : Exceptions.server("failed-to-execute-flow")
                .onEntity("flow", cmd.getFlowName())
                .withCause(e)
                .withExtra("flowName", cmd.getFlowName())
                .get();
        apiManager.send(NotifyError.builder()
            .entityType("flow")
            .entityName(cmd.getFlowName())
            .exception(bre)
            .build());
    }
}
