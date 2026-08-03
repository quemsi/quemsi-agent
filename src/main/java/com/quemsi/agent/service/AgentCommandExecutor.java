package com.quemsi.agent.service;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.service.cmd.ExecuteExecuteFlow;
import com.quemsi.agent.service.cmd.ExecuteRetentionExecute;
import com.quemsi.agent.service.cmd.ExecuteTestAWSS3Drive;
import com.quemsi.agent.service.cmd.ExecuteTestAzureBlobDrive;
import com.quemsi.agent.service.cmd.ExecuteTestDatasource;
import com.quemsi.agent.service.cmd.ExecuteTestFolderAccess;
import com.quemsi.agent.service.cmd.ExecuteTestRedis;
import com.quemsi.agent.service.cmd.ExecutePreviewSubset;
import com.quemsi.agent.service.cmd.ExecuteVersionDeleteRequest;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.PreviewSubset;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.TestAWSS3Drive;
import com.quemsi.model.dto.agent.TestAzureBlobDrive;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.TestFolderAccess;
import com.quemsi.model.dto.agent.TestRedis;
import com.quemsi.model.dto.agent.VersionDeleteRequest;

public class AgentCommandExecutor {
    @Autowired
    private ExecuteExecuteFlow executeExecuteFlow;
    @Autowired
    private ExecuteRetentionExecute executeRetentionExecute;
    @Autowired
    private ExecuteTestDatasource executeTestDataExecutor;
    @Autowired
    private ExecuteTestFolderAccess executeTestFolderAccess;
    @Autowired
    private ExecuteVersionDeleteRequest executeVersionDeleteRequest;
    @Autowired
    private ExecuteTestAzureBlobDrive executeTestAzureBlobDrive;
    @Autowired
    private ExecuteTestAWSS3Drive executeTestAWSS3Drive;
    @Autowired
    private ExecuteTestRedis executeTestRedis;
    @Autowired
    private ExecutePreviewSubset executePreviewSubset;

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
        executeTestDataExecutor.execute(cmd);
    }
    public void execute(PreviewSubset cmd){
        executePreviewSubset.execute(cmd);
    }
    public void execute(TestFolderAccess cmd){
        executeTestFolderAccess.execute(cmd);
    }
    public void execute(TestAzureBlobDrive cmd){
        executeTestAzureBlobDrive.execute(cmd);
    }
    public void execute(TestAWSS3Drive cmd){
        executeTestAWSS3Drive.execute(cmd);
    }
    public void execute(TestRedis cmd){
        executeTestRedis.execute(cmd);
    }
}
