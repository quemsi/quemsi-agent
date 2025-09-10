package com.quemsi.agent.service;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.service.cmd.ExecuteTestDatasource;
import com.quemsi.agent.service.cmd.ExecuteExecuteFlow;
import com.quemsi.agent.service.cmd.ExecuteRetentionExecute;
import com.quemsi.agent.service.cmd.ExecuteVersionDeleteRequest;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.VersionDeleteRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentCommandExecutor {
    @Autowired
    private ExecuteExecuteFlow executeExecuteFlow;
    @Autowired
    private ExecuteRetentionExecute executeRetentionExecute;
    @Autowired
    private ExecuteTestDatasource cmdTestDataExecutor;
    @Autowired
    private ExecuteVersionDeleteRequest executeVersionDeleteRequest;

    public void execute(ExecuteFlow cmd){
        executeExecuteFlow.execute(cmd);
    }
    public void execute(RetentionExecute cmd){
        executeRetentionExecute.execute(cmd);
    }
    public void execute(VersionDeleteRequest cmd){
        executeVersionDeleteRequest.execute(cmd);
    }
    public void execute(TestDatasource cmd){
        cmdTestDataExecutor.execute(cmd);
    }
}
