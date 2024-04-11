package com.biddflux.agent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.biddflux.agent.api.ApiClient;
import com.biddflux.agent.service.FlowManager;
import com.biddflux.agent.service.GoogleDriveManager;
import com.biddflux.agent.service.SpringBeanManager;
import com.biddflux.commons.util.Exceptions;
import com.biddflux.model.dto.AgentModel;
import com.biddflux.model.dto.FlowHistory;
import com.biddflux.model.dto.agent.AgentCommand;
import com.biddflux.model.dto.agent.DelayAgentCommand;
import com.biddflux.model.dto.agent.ExecuteFlow;
import com.biddflux.model.dto.agent.GoogleDriveConnect;
import com.biddflux.model.dto.agent.RetentionExecute;
import com.biddflux.model.dto.agent.UpdateAgentModel;
import com.biddflux.model.dto.agent.onapi.NotifyError;
import com.biddflux.model.dto.agent.onapi.RetentionCompleted;
import com.biddflux.model.dto.agent.onapi.UpdateGoogleDrive;
import com.biddflux.model.flow.Flow;
import com.biddflux.model.flow.out.GoogleDrive;
import com.biddflux.model.flow.out.Storage;
import com.google.common.io.Files;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentCoordinator {
	@Autowired
	private ApiClient apiClient;
    @Autowired
	private SpringBeanManager beanManager;
	@Autowired
	private FlowManager flowManager;
    @Autowired
    private ExecutorService vThreadExecutor;
    @Autowired
    private GoogleDriveManager manager;
    private ApiCommandListener apiCommandListener;

	private boolean initialized;
	
    public void initialize(AgentModel model){
        if(model.getTimers() != null){
            model.getTimers().forEach(t -> beanManager.registerTimer(t.getName(), t.getSchedule()));
        }
        if(model.getDatasources() != null){
            model.getDatasources().forEach(ds -> beanManager.registerDatasource(ds.getName(), ds.getDbName(), ds.getUrl(), ds.getUsername(), ds.getPassword()));
        }
        if(model.getGoogleDrives() != null){
            model.getGoogleDrives().forEach(t -> beanManager.registerGoogleDrive(t.getName(), t.getCallbackBaseUrl(), t.getCallbackPort()));
        }
        if(model.getLocalDrives() != null){
            model.getLocalDrives().forEach(t -> beanManager.registerLocalDrive(t.getName(), t.getStorageRoot(), t.getCapacity(), t.getUsedSize()));
        }
        if(model.getStorages() != null){
            model.getStorages().forEach(s -> beanManager.registerStroge(s.getName(), s.getType(), s.getLoc(), s.getRootPath(), s.getRetentionPolicy(), s.getCapacity(), s.getUsedSize()));
        }
        if(model.getFlows() != null){
            model.getFlows().forEach(f -> flowManager.createNewFlow(f));
        }
    }

    public void start() {
        while(!this.initialized){
            try{
                AgentModel model = apiClient.allModel();
                log.info("model : {}", model);
				initialize(model);
                String googleCredentialJson = apiClient.googleCredential();
                BufferedWriter writer = new BufferedWriter(Files.newWriter(new File("credentials.json"), StandardCharsets.UTF_8));
                writer.write(googleCredentialJson);
                writer.close();
                log.info("will initialize googledrives");
                vThreadExecutor.execute(new InitGoogleDrives());
                initialized = true;
                log.info("initialization completed");
                apiCommandListener = new ApiCommandListener();
                vThreadExecutor.submit(apiCommandListener);
            }catch(WebClientRequestException | IOException ex){
                throw Exceptions.server("init-error").withCause(ex).get();
            }
        }
    }

    public void execute(AgentCommand command){
        if(command instanceof DelayAgentCommand delayAgent){
            try {
                Thread.sleep(delayAgent.getDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if(command instanceof ExecuteFlow executeFlow){
            log.info("executing flow {}", executeFlow);
            Flow flow = flowManager.findByName(executeFlow.getFlowName()).orElseThrow(Exceptions.notFound("invalid-flow-name").withExtra("flowName", executeFlow.getFlowName()).supplier());
            FlowHistory history = flow.execute(executeFlow.getVersionId(), executeFlow.getTags());
            if(history != null){
                log.info("saving history {}", history);
                history = apiClient.saveFlowHistory(history);
            }
        } else if(command instanceof GoogleDriveConnect gDriveConnect) {
            log.info("connecting google drive {}", gDriveConnect);
            GoogleDrive drive = beanManager.findGoogleDrive(gDriveConnect.getDriveName());
            if(gDriveConnect.isConnect() != drive.isConnected()){
                if(drive.isConnected()){
                    drive.clearConnection();
                } else {
                    try {
                        drive.connectToDrive();
                    } catch (GeneralSecurityException | IOException e) {
                        throw Exceptions.server("google-drive-error").withCause(e).get();
                    }
                }
            }
            apiClient.send(UpdateGoogleDrive.builder().driveName(drive.getName()).connected(drive.isConnected()).build());
        } else if(command instanceof UpdateAgentModel updatedModel){
            log.info("uupdating model {}", updatedModel);
            initialize(updatedModel.getUpdatedModel());
        } else if(command instanceof RetentionExecute retentionExecute){
            log.info("executing retention {}", retentionExecute);
            Storage storage = beanManager.findStorage(retentionExecute.getStorageName());
            List<Long> fileIds = new LinkedList<>();
            retentionExecute.getFiles().forEach(f -> {
                try{
                    storage.deleteFile(f.getDir(), f.getName());
                    fileIds.add(f.getId());
                }catch(IOException ex){
                    log.debug("ignored", ex);
                }
            });
            RetentionCompleted retentionCompleted = RetentionCompleted.builder().storageId(retentionExecute.getStorageId()).storageName(retentionExecute.getStorageName()).files(fileIds).build();
            log.info("sending retention complete {}", retentionCompleted);
            apiClient.send(retentionCompleted);
        } else{
            throw Exceptions.server("not-implemented").withExtra("commandName", command.getName()).get();
        }
    }
				
    public class ApiCommandListener implements Runnable{
        @Override
        public void run() {
            try{
                AgentCommand command = apiClient.nextCommand();
                execute(command);
            }catch(Exception e){
                log.error("command-execution-error", e);
            }finally{
                vThreadExecutor.submit(this);
            }
        }
    }

    public class InitGoogleDrives implements Runnable{
        @Override
        public void run() {
            manager.connectToDrives();
        }
    }

    public class ConnectToGoogleDrive implements Runnable{
        private GoogleDrive googleDrive;

        @Override
        public void run() {
            try{
                googleDrive.connectToDrive();
            } catch (Exception ex){
                apiClient.send(NotifyError.builder().entityName(googleDrive.getName()).entityType(GoogleDrive.class.getSimpleName()).exception(Exceptions.server("unable-to-connect-drive").withCause(ex).get()).build());
            }
        }
    }
}
