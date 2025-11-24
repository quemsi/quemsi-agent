package com.quemsi.agent.service.cmd;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.onapi.TestDatasourceResult;
import com.quemsi.model.flow.db.DataSourceFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteTestDatasource {
    @Autowired
	private ApiManager apiManager;
    
    public void execute(TestDatasource cmd){
        TestDatasourceResult result = TestDatasourceResult.builder().agentId(cmd.getAgentId()).correlationId(cmd.getCorrelationId())
            .timeoutMilis(cmd.getTimeoutMilis())
            .build();
        try{
            String username = cmd.getDatasource().getUsername();
            String password = cmd.getDatasource().getPassword();
            boolean credentialsExists = true;
            if(cmd.getDatasource().isUseEnvVar()){
                String userValue = System.getenv(username);
                String passValue = System.getenv(password);
                if(StringUtils.isEmptyOrNull(userValue)){
                    result.setSuccess(false);
                    result.setMessage("missing-env-variable");
                    result.setErrorMessage("Environment variable " + username + " is missing, make sure you pass it to agent properly");
                    credentialsExists = false;
                } else if(StringUtils.isEmptyOrNull(passValue)){
                    result.setSuccess(false);
                    result.setMessage("missing-env-variable");
                    result.setErrorMessage("Environment variable " + username + " is missing, make sure you pass it to agent properly");
                    credentialsExists = false;
                } else {
                    username = userValue;
                    password = passValue;
                }
            }
            if(credentialsExists){
                DataSourceFactory factory = DataSourceFactory.create(cmd.getDatasource().getType());
                
                factory.setUrl(cmd.getDatasource().getUrl());
                factory.setDbName(cmd.getDatasource().getDbName());
                factory.setSchema(cmd.getDatasource().getSchema());
                factory.setUsername(username);
                factory.setPassword(password);
                
                boolean reachable = factory.healthCheck();
                result.setSuccess(reachable);
                if(reachable){
                    result.setMessage("connection-succeded");
                } else {
                    result.setMessage("connection-unreachable");
                }
            }
        } catch(SQLException sex){
            log.error("datasource-test-failed", sex);
            result.setSuccess(false);
            result.setErrorCode(sex.getErrorCode());        
            result.setMessage("connection-test-failed");
            result.setErrorMessage(sex.getMessage());
        } catch(Exception ex){
            result.setSuccess(false);   
            result.setMessage("connection-test-failed-no-code");
            result.setErrorMessage(ex.getMessage());
        }
        apiManager.send(result);
    }
}
