package com.quemsi.agent;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentCommandExecutor;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.DelayedFormatter;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.agent.AgentCommand;
import com.quemsi.model.dto.agent.DelayAgentCommand;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.TestAzureBlobDrive;
import com.quemsi.model.dto.agent.TestAWSS3Drive;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.UpdateAgentModel;
import com.quemsi.model.dto.agent.VersionDeleteRequest;

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
    private AgentCommandExecutor commandExecutor;

    private ApiCommandListener apiCommandListener;
    @Value("${spring.application.version}")
    private String agentVersion;

	private boolean initialized;
	
    public void initialize(AgentModel model){
        if(model.getTimers() != null){
            model.getTimers().forEach(t -> beanManager.registerTimer(t));
        }
        if(model.getDatasources() != null){
            model.getDatasources().forEach(ds -> beanManager.registerDatasource(ds));
        }
        if(model.getLocalDrives() != null){
            model.getLocalDrives().forEach(t -> beanManager.registerLocalDrive(t));
        }
        if(model.getAzureBlobDrives() != null){
            model.getAzureBlobDrives().forEach(a -> beanManager.registerAzureBlobDrive(a));
        }
        if(model.getAwsS3Drives() != null){
            model.getAwsS3Drives().forEach(a -> beanManager.registerAWSS3Drive(a));
        }
        if(model.getStorages() != null){
            model.getStorages().forEach(s -> beanManager.registerStroge(s));
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
                commandExecutor.execute(executeFlow);
            } else if(command instanceof UpdateAgentModel updatedModel){
                log.info("uupdating model {}", updatedModel);
                initialize(updatedModel.getUpdatedModel());
            } else if(command instanceof RetentionExecute retentionExecute){
                commandExecutor.execute(retentionExecute);
            } else if(command instanceof VersionDeleteRequest versionDeleteRequest){
                commandExecutor.execute(versionDeleteRequest);
            } else if(command instanceof TestDatasource testDatasource){
                    commandExecutor.execute(testDatasource);
            } else if(command instanceof TestAzureBlobDrive testAzureBlobDrive){
                commandExecutor.execute(testAzureBlobDrive);
            } else if(command instanceof TestAWSS3Drive testAWSS3Drive){
                commandExecutor.execute(testAWSS3Drive);
            } else {
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
