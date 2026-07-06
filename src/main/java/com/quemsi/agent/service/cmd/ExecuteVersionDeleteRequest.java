package com.quemsi.agent.service.cmd;

import java.io.IOException;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.dto.agent.VersionDeleteRequest;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.dto.agent.onapi.VersionDeleted;
import com.quemsi.model.flow.out.Storage;

public class ExecuteVersionDeleteRequest {
    @Autowired
    private SpringBeanManager beanManager;
    @Autowired
    private ApiManager apiManager;
    @Autowired
    private FileNameUtil util;
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;
    
    public void execute(VersionDeleteRequest versionDeleteRequest){
        try{
            Storage storage = beanManager.findStorage(versionDeleteRequest.getVersion().getStorage().getName());
            versionDeleteRequest.getVersion().getFiles().forEach(f -> {
                try{
                    storage.deleteFile(f.getDir(), util.versionedFileName(f.getName(), f.getVersion()));
                }catch(IOException ex){
                    agentBatchedLogger.logDebug(null, null, LogMessage.debug("ignored", ex));
                }
            });
            VersionDeleted versionDeleted = VersionDeleted.builder().correlationId(versionDeleteRequest.getCorrelationId()).versionId(versionDeleteRequest.getVersion().getId()).succeeded(true).build();
            agentBatchedLogger.logInfo(null, null, LogMessage.info("sending version deleted {}", versionDeleted));
            apiManager.send(versionDeleted);
        }catch(NoSuchBeanDefinitionException e){
            agentBatchedLogger.logError(null, null, LogMessage.error("error-in-executing-version-delete-request", e));
            apiManager.send(NotifyError.builder().entityType("storage").entityName(versionDeleteRequest.getVersion().getStorage().getName()).exception(Exceptions.server("not-existing-storage").withExtra("storageName", versionDeleteRequest.getVersion().getStorage().getName()).get()).build());
        }
    }
}
