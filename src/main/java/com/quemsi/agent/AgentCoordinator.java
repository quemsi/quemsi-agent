package com.quemsi.agent;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.agent.service.AgentCommandExecutor;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.DelayedFormatter;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.agent.AgentCommand;
import com.quemsi.model.dto.agent.DelayAgentCommand;
import com.quemsi.model.dto.agent.ExecuteFlow;
import com.quemsi.model.dto.agent.RetentionExecute;
import com.quemsi.model.dto.agent.TestAWSS3Drive;
import com.quemsi.model.dto.agent.TestAzureBlobDrive;
import com.quemsi.model.dto.agent.TestDatasource;
import com.quemsi.model.dto.agent.TestFolderAccess;
import com.quemsi.model.dto.agent.UpdateAgentModel;
import com.quemsi.model.dto.agent.VersionDeleteRequest;

public class AgentCoordinator {

    private static final long SLEEP_SLICE_MS = 100;

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
    @Autowired
    private AgentBatchedLogger agentBatchedLogger;
    private static final int MAX_BACKOFF_SECONDS = 60;
    private static final int MIN_BACKOFF_SECONDS = 5;

    private AtomicInteger backoff = new AtomicInteger(0);

    private final AtomicBoolean commandLoopActive = new AtomicBoolean(true);

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
                agentBatchedLogger.logInfo(null, null, LogMessage.info("quemsi-agent:{}", agentVersion));
                AgentModel model = apiManager.allModel(agentVersion);
                agentBatchedLogger.logDebug(null, null, LogMessage.debug("model : {}", DelayedFormatter.toDelayedString(Exceptions.wrapSupplier(() -> objectMapper.writeValueAsString(model)))));
				initialize(model);
                initialized = true;
                agentBatchedLogger.logInfo(null, null, LogMessage.info("initialization completed"));
                apiCommandListener = new ApiCommandListener();
                vThreadExecutor.submit(apiCommandListener);
            }catch(WebClientRequestException ex){
                throw Exceptions.server("initialization-error").withCause(ex).get();
            }
        }
    }

    /**
     * Stops the {@link ApiCommandListener} loop from resubmitting; used during application shutdown.
     */
    public void stopCommandLoop() {
        commandLoopActive.set(false);
    }

    /**
     * Sleeps up to {@code totalMillis} in small slices so shutdown can cut the wait short.
     */
    private void sleepMillisInterruptible(long totalMillis) {
        long remaining = totalMillis;
        while (remaining > 0 && commandLoopActive.get()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            long chunk = Math.min(SLEEP_SLICE_MS, remaining);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            remaining -= chunk;
        }
    }

    public void execute(AgentCommand command){
        if(command instanceof DelayAgentCommand delayAgent){
            sleepMillisInterruptible(delayAgent.getDelay());
        } else {
            agentBatchedLogger.logInfo(null, null, LogMessage.info("recived command  : {}", command));
            if(command instanceof ExecuteFlow executeFlow){
                commandExecutor.execute(executeFlow);
            } else if(command instanceof UpdateAgentModel updatedModel){
                agentBatchedLogger.logInfo(null, null, LogMessage.info("uupdating model {}", updatedModel));
                initialize(updatedModel.getUpdatedModel());
            } else if(command instanceof RetentionExecute retentionExecute){
                commandExecutor.execute(retentionExecute);
            } else if(command instanceof VersionDeleteRequest versionDeleteRequest){
                commandExecutor.execute(versionDeleteRequest);
            } else if(command instanceof TestDatasource testDatasource){
                commandExecutor.execute(testDatasource);
            } else if(command instanceof TestFolderAccess testFolderAccess){
                commandExecutor.execute(testFolderAccess);
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
            if (!commandLoopActive.get()) {
                return;
            }
            boolean listenNext = true;
            try{
                if(backoff.get() > 0){
                    agentBatchedLogger.logDebug(null, null, LogMessage.debug("Waiting for {} seconds before next command", backoff.get()));
                    sleepMillisInterruptible(Duration.ofSeconds(backoff.get()).toMillis());
                }
                if (!commandLoopActive.get()) {
                    return;
                }
                AgentCommand command = apiManager.nextCommand();
                execute(command);
                resetBackoff();
            } catch (WebClientRequestException ignore){
                incrementBackoff();
                agentBatchedLogger.logDebug(null, null, LogMessage.debug("Unable to reach api, will try again in {} seconds", apiRetry));
                agentBatchedLogger.logDebug(null, null, LogMessage.debug("api error", ignore));
                sleepMillisInterruptible(Duration.ofSeconds(apiRetry).toMillis());
            } catch(BaseRuntimeException bre){
                incrementBackoff();
                listenNext = !bre.getExtra().containsKey("exit");
            } catch(Exception e) {
                incrementBackoff();
                agentBatchedLogger.logError(null, null, LogMessage.error("command-execution-error", e));
            } finally {
                if(listenNext && commandLoopActive.get()){
                    vThreadExecutor.submit(this);
                }
            }
        }
        public void resetBackoff() {
            backoff.set(0);
        }
        public void incrementBackoff() {
            int current = backoff.get();
            if (current == 0) {
                backoff.set(MIN_BACKOFF_SECONDS);
            } else {
                backoff.set(Math.min(current * 2, MAX_BACKOFF_SECONDS));
            }
        }
    }
}
