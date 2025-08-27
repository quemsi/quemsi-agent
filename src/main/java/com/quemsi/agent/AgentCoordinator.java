package com.quemsi.agent;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.CommandExecutor;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.DelayedFormatter;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.agent.AgentCommand;
import com.quemsi.model.dto.agent.DelayAgentCommand;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.UpdateAgentModel;
import com.quemsi.model.dto.agent.VersionDeleteRequest;
import com.quemsi.model.dto.agent.onapi.RetentionCompleted;
import com.quemsi.model.dto.agent.onapi.VersionDeleted;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.out.Storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentCoordinator {
    @Value("${api.retry:5}")
    private long apiRetry;
	@Autowired
	private ApiManager apiManager;
    @Autowired
	private SpringBeanManager beanManager;
	@Autowired
	private FlowManager flowManager;
    @Autowired
    private ExecutorService vThreadExecutor;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CommandExecutor commandExecutor;

    private ApiCommandListener apiCommandListener;
    @Value("${spring.application.version}")
    private String agentVersion;

	private boolean initialized;
	
    public void initialize(AgentModel model){
        if(model.getTimers() != null){
            model.getTimers().forEach(t -> beanManager.registerTimer(t.getName(), t.getSchedule()));
        }
        if(model.getDatasources() != null){
            model.getDatasources().forEach(ds -> beanManager.registerDatasource(ds.getType(), ds.getName(), ds.getDbName(), ds.getUrl(), ds.getUsername(), ds.getPassword(), ds.isUseEnvVar()));
        }
        if(model.getLocalDrives() != null){
            model.getLocalDrives().forEach(t -> beanManager.registerLocalDrive(t.getName(), t.getStorageRoot(), t.getCapacity(), t.getUsedSize()));
        }
        if(model.getAzureBlobDrives() != null){
            model.getAzureBlobDrives().forEach(a -> beanManager.registerAzureBlobDrive(a.getName(), a.getAccountName(), a.getAccountKey(), a.isUseEnvVar(), a.getStorageRoot(), a.getCapacity(), a.getUsedSize()));
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
                log.info("quemsi-agent:{}", agentVersion);
                AgentModel model = apiManager.allModel(agentVersion);
                log.debug("model : {}", DelayedFormatter.toDelayedString(Exceptions.wrapSupplier(() -> objectMapper.writeValueAsString(model))));
				initialize(model);
                initialized = true;
                log.info("initialization completed");
                apiCommandListener = new ApiCommandListener();
                vThreadExecutor.submit(apiCommandListener);
            }catch(WebClientRequestException ex){
                throw Exceptions.server("initialization-error").withCause(ex).get();
            }
        }
    }

    public void execute(AgentCommand command){
        log.info("recived command  : {}", command);
        if(command instanceof DelayAgentCommand delayAgent){
            try {
                Thread.sleep(delayAgent.getDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            if(command instanceof ExecuteFlow executeFlow){
                log.info("executing flow {}", executeFlow);
                Flow flow = flowManager.findByName(executeFlow.getFlowName()).orElseThrow(Exceptions.notFound("invalid-flow-name").withExtra("flowName", executeFlow.getFlowName()).supplier());
                FlowExecution execution = flow.execute(executeFlow.getVersionId(), executeFlow.getTags(), executeFlow.getFiles(), executeFlow.getFlowExecutionId());
                if(execution != null){
                    log.info("saving history {}", execution);
                    execution = apiManager.saveFlowExecution(execution);
                }
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
                apiManager.send(retentionCompleted);
            } else if(command instanceof VersionDeleteRequest versionDeleteRequest){
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
            } else if(command instanceof TestDatasource testDatasource){
                commandExecutor.execute(testDatasource);
            }
            else{
                throw Exceptions.server("not-implemented").withExtra("commandName", command.getName()).get();
            }
        }
    }
				
    public class ApiCommandListener implements Runnable{
        @Override
        public void run() {
            boolean listenNext = true;
            try{
                AgentCommand command = apiManager.nextCommand();
                execute(command);
            } catch (WebClientRequestException ignore){
                log.debug("Unable to reach api, will try again in {} seconds", apiRetry);
                log.trace("api error", ignore);
                Exceptions.wrapRunnable(() -> Thread.sleep(Duration.ofSeconds(apiRetry))).run();;
            } catch(BaseRuntimeException bre){
                listenNext = !bre.getExtra().containsKey("exit");
            } catch(Exception e) {
                log.error("command-execution-error", e);
            } finally {
                if(listenNext){
                    vThreadExecutor.submit(this);
                }
            }
        }
    }
}
