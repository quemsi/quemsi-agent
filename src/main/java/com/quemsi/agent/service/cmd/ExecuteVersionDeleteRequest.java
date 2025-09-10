package com.quemsi.agent.service.cmd;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.model.dto.agent.VersionDeleteRequest;
import com.quemsi.model.dto.agent.onapi.VersionDeleted;
import com.quemsi.model.flow.out.Storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteVersionDeleteRequest {
    @Autowired
    private SpringBeanManager beanManager;
    @Autowired
    private ApiManager apiManager;
    
    public void execute(VersionDeleteRequest versionDeleteRequest){
        Storage storage = beanManager.findStorage(versionDeleteRequest.getVersion().getStorage().getName());
        versionDeleteRequest.getVersion().getFiles().forEach(f -> {
            try{
                storage.deleteFile(f.getDir(), f.getName());
            }catch(IOException ex){
                log.debug("ignored", ex);
            }
        });
        VersionDeleted versionDeleted = VersionDeleted.builder().versionId(versionDeleteRequest.getVersion().getId()).build();
        log.info("sending version deleted {}", versionDeleted);
        apiManager.send(versionDeleted);
    }
}
