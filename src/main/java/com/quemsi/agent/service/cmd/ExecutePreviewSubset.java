package com.quemsi.agent.service.cmd;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.agent.PreviewSubset;
import com.quemsi.model.dto.agent.onapi.PreviewSubsetResult;
import com.quemsi.model.flow.db.DMLService;
import com.quemsi.model.flow.db.DataSourceFactory;
import com.quemsi.model.flow.db.sql.DbModel;
import com.quemsi.model.flow.subset.SubsetPlan;
import com.quemsi.model.flow.subset.SubsetPlanner;

public class ExecutePreviewSubset {
    @Autowired
    private ApiManager apiManager;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;

    public void execute(PreviewSubset cmd) {
        PreviewSubsetResult result = PreviewSubsetResult.builder()
            .agentId(cmd.getAgentId())
            .correlationId(cmd.getCorrelationId())
            .timeoutMilis(cmd.getTimeoutMilis())
            .success(false)
            .build();
        try {
            if (cmd.getDatasource() == null) {
                result.setMessage("datasource-required");
                result.setErrorMessage("Datasource is required for subset preview");
                apiManager.send(result);
                return;
            }
            if (cmd.getSubset() == null || !cmd.getSubset().isActive()) {
                result.setMessage("subset-required");
                result.setErrorMessage("Enabled subset with at least one driver is required");
                apiManager.send(result);
                return;
            }
            String username = cmd.getDatasource().getUsername();
            String password = cmd.getDatasource().getPassword();
            if (cmd.getDatasource().isUseEnvVar()) {
                String userValue = System.getenv(username);
                String passValue = System.getenv(password);
                if (StringUtils.isEmptyOrNull(userValue) || StringUtils.isEmptyOrNull(passValue)) {
                    result.setMessage("missing-env-variable");
                    result.setErrorMessage("Environment variable credentials are missing");
                    apiManager.send(result);
                    return;
                }
                username = userValue;
                password = passValue;
            }
            DataSourceFactory factory = DataSourceFactory.create(cmd.getDatasource().getType());
            factory.setUrl(cmd.getDatasource().getUrl());
            factory.setDbName(cmd.getDatasource().getDbName());
            factory.setSchemas(cmd.getDatasource().getSchemas());
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setName(cmd.getDatasource().getName());
            factory.setReadOnly(true);

            DbModel dbModel = factory.getDbModel();
            try (DMLService dml = factory.dmlService()) {
                SubsetPlan plan = new SubsetPlanner().plan(dbModel, dml, cmd.getSubset());
                result.setTables(plan.summaries());
                result.setSuccess(true);
                result.setMessage("subset-preview-ok");
            }
        } catch (BaseRuntimeException ex) {
            agentBatchedLogger.logError(null, null, LogMessage.error("subset-preview-failed", ex));
            result.setSuccess(false);
            result.setMessage(ex.getMessageId() != null ? ex.getMessageId() : "subset-preview-failed");
            result.setErrorMessage(ex.getMessage());
        } catch (Exception ex) {
            agentBatchedLogger.logError(null, null, LogMessage.error("subset-preview-failed", ex));
            result.setSuccess(false);
            result.setMessage("subset-preview-failed");
            result.setErrorMessage(ex.getMessage());
        }
        apiManager.send(result);
    }
}
