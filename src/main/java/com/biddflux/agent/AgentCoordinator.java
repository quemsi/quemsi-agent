package com.biddflux.agent;

import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.biddflux.agent.api.ApiClient;
import com.biddflux.agent.service.FlowManager;
import com.biddflux.agent.service.SpringBeanManager;
import com.biddflux.commons.util.Exceptions;
import com.biddflux.model.dto.AgentModel;
import com.biddflux.model.dto.FlowHistory;
import com.biddflux.model.dto.agent.AgentCommand;
import com.biddflux.model.dto.agent.DelayAgentCommand;
import com.biddflux.model.dto.agent.ExecuteFlow;
import com.biddflux.model.flow.Flow;
import com.biddflux.model.flow.retention.RetentionPolicy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentCoordinator {
    // @Autowired
    // private ConfigurableApplicationContext context;
	@Autowired
	private ApiClient apiClient;
    @Autowired
	private SpringBeanManager beanManager;
	@Autowired
	private ObjectProvider<RetentionPolicy> retentionPolicyProvider;
	@Autowired
	private FlowManager flowManager;
    @Autowired
    private ExecutorService vThreadExecutor;
    private ApiCommandListener apiCommandListener;

	private boolean initialized;
	
    public void initialize(AgentModel model){
        model.getTimers().forEach(t -> beanManager.registerTimer(t.getName(), t.getSchedule()));
        model.getDatasources().forEach(ds -> beanManager.registerDatasource(ds.getName(), ds.getDbName(), ds.getUrl(), ds.getUsername(), ds.getPassword()));
		model.getGoogleDrives().forEach(t -> beanManager.registerGoogleDrive(t.getName(), t.getCallbackBaseUrl(), t.getCallbackPort()));
		model.getLocalDrives().forEach(t -> beanManager.registerLocalDrive(t.getName(), t.getStorageRoot(), t.getCapacity()));
		model.getStorages().forEach(s -> beanManager.registerStroge(s.getName(), s.getType(), s.getLoc(), s.getRootPath(), retentionPolicyProvider.getObject(s.getRetentionPolicy(), s.getCountLimit(), s.getSizeLimit())));
		model.getFlows().forEach(f -> flowManager.createNewFlow(f));
    }

    public void start() {
        while(!this.initialized){
            try{
                AgentModel model = apiClient.allModel();
                log.info("model : {}", model);
				initialize(model);
                initialized = true;
                apiCommandListener = new ApiCommandListener();
                vThreadExecutor.submit(apiCommandListener);
            }catch(WebClientRequestException ex){
                log.error("apiClientError", ex);
                // SpringApplication.exit(context, () -> -1);
            }
        }
    }

    public void execute(AgentCommand command){
        if(command instanceof ExecuteFlow executeFlow){
            Flow flow = flowManager.findByName(executeFlow.getFlowName()).orElseThrow(Exceptions.notFound("invalid-flow-name").withExtra("flowName", executeFlow.getFlowName()).supplier());
            FlowHistory history = flow.execute(executeFlow.getVersionId(), executeFlow.getTags());
            if(history != null){
                history = apiClient.saveFlowHistory(history);
            }
        } else if(command instanceof DelayAgentCommand delayAgent){
            try {
                Thread.sleep(delayAgent.getDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
}
