package com.quemsi.agent.service.cmd;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.dto.agent.onapi.RetentionCompleted;
import com.quemsi.model.flow.out.Storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteRetentionExecute {
    @Autowired
    private SpringBeanManager beanManager;
    @Autowired
    private ApiManager apiManager;
    
    public void execute(RetentionExecute cmd){
        try{
            log.info("executing retention {}", cmd);
            Storage storage = beanManager.findStorage(cmd.getStorageName());
            List<Long> fileIds = new LinkedList<>();
            cmd.getFiles().forEach(f -> {
                try{
                    storage.deleteFile(f.getDir(), f.getName());
                    fileIds.add(f.getId());
                }catch(IOException ex){
                    log.debug("ignored", ex);
                }
            });
            RetentionCompleted retentionCompleted = RetentionCompleted.builder().storageId(cmd.getStorageId()).storageName(cmd.getStorageName()).files(fileIds).versions(cmd.getVersions()).build();
            log.info("sending retention complete {}", retentionCompleted);
            apiManager.send(retentionCompleted);
        }catch(NoSuchBeanDefinitionException e){
            log.error("error-in-executing-retention", e);
            apiManager.send(NotifyError.builder().entityType("storage").entityName(cmd.getStorageName()).exception(Exceptions.server("not-existing-storage").withExtra("storageName", cmd.getStorageName()).get()).build());
        }
    }
}
