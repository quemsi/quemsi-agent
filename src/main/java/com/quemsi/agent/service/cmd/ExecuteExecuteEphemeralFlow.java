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
import com.quemsi.model.dto.agent.ExecuteEphemeralFlow;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.flow.Flow;

public class ExecuteExecuteEphemeralFlow {
    @Autowired
    private FlowManager flowManager;
    @Autowired
    private ApiManager apiManager;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;

    public void execute(ExecuteEphemeralFlow cmd) {
        Long executionId = cmd.getFlowExecutionId();
        String flowName = cmd.getFlowName();
        agentBatchedLogger.logInfo(executionId, null, LogMessage.info("executing ephemeral flow {}", cmd));
        try {
            if (cmd.getFlow() == null) {
                throw Exceptions.badRequest("ephemeral-flow-required").withExtra("flowName", flowName).get();
            }
            Flow flow = flowManager.createNewFlow(cmd.getFlow());
            if (flow == null) {
                throw Exceptions.server("error-creating-flow")
                    .onEntity("flow", flowName)
                    .withExtra("flowName", flowName)
                    .get();
            }
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
        } finally {
            flowManager.uninstall(flowName);
        }
    }

    private void saveTerminalExecution(ExecuteEphemeralFlow cmd, FlowExecutionStatus status) {
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

    private void notifyFailure(ExecuteEphemeralFlow cmd, Exception e) {
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
