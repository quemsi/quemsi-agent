package com.quemsi.agent.service.cmd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.commons.util.CommonOps;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.agent.TestFolderAccess;
import com.quemsi.model.dto.agent.onapi.TestFolderAccessResult;

public class ExecuteTestFolderAccess {
    private static final String FOLDER_ACCESS_TEST_FILE = "test.txt";
    @Autowired
	private ApiManager apiManager;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;
    
    public void execute(TestFolderAccess cmd){
        TestFolderAccessResult result = TestFolderAccessResult.builder().agentId(cmd.getAgentId()).correlationId(cmd.getCorrelationId())
            .timeoutMilis(cmd.getTimeoutMilis())
            .build();
        String path = CommonOps.sanitizePath(cmd.getPath());
        if(!StringUtils.isEmptyOrNull(path)){
            String filePath = path + File.separator + FOLDER_ACCESS_TEST_FILE;
            FileOutputStream fos = null;
            try{
                File testFile = new File(filePath);
                File parentDir = testFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                fos = new FileOutputStream(filePath);
                fos.write("test".getBytes());
                fos.close();
                result.setSuccess(true);
                result.setMessage("folder-access-test-success");
            } catch(IOException ioe){
                agentBatchedLogger.logError(null, null, LogMessage.error("folder-access-test-failed", ioe));
                result.setSuccess(false);
                result.setMessage("folder-access-test-failed");
                result.setErrorMessage(ioe.getMessage());
            }catch(Exception ex){
                result.setSuccess(false);   
                result.setMessage("connection-test-failed-no-code");
                result.setErrorMessage(ex.getMessage());
            } finally {
                try{
                    if(fos != null){
                        fos.close();
                    }
                    File file = new File(filePath);
                    if(file.exists()){
                        if(!file.delete()){
                            agentBatchedLogger.logError(null, null, LogMessage.error("folder-access-test-failed", "failed to delete test file"));
                        }
                    }
                }catch(Exception ex){
                    agentBatchedLogger.logError(null, null, LogMessage.error("folder-access-test-failed", ex));
                }
            }
        } else {
            result.setSuccess(false);
            result.setMessage("path-is-empty");
            result.setErrorMessage("path-is-empty");
        }
        apiManager.send(result);
    }
}
