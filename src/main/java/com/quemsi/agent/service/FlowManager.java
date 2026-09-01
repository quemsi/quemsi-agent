package com.quemsi.agent.service;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.agent.flow.TimerImpl;
import com.quemsi.agent.flow.TimerImpl.NamedRunnable;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.DateUtils;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.api.ApiClient;
import com.quemsi.model.dto.FlowDetail;
import com.quemsi.model.dto.FlowExecution;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.dto.agent.onapi.NotifyFlowReady;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.Step;
import com.quemsi.model.flow.factories.StepFactory;

import lombok.Getter;

public class FlowManager {
	private Map<String, Flow> flows = new HashMap<>();
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private StepFactory stepFactory;
	@Autowired
	private ObjectProvider<Flow> flowObjectProvider;
	@Autowired
	private SpringBeanManager beanManager;
	@Autowired
	private ApiClient apiClient;
	@Autowired
	private DateUtils dateUtils;
	@Autowired
	private AgentBatchedLogger agentBatchedLogger;
	
	public Flow createNewFlow(FlowDetail flow) {
		String name = flow.getName();
		try {
			if(!flow.isActive()){
				return uninstall(name);
			}
			JsonNode node = objectMapper.readTree(flow.getModel().getBytes(Charset.forName("UTF-8")));
			Flow f = flowObjectProvider.getObject();
			f.setId(flow.getId());
			f.setName(name);
			f.setActive(flow.isActive());
			f.setTitle(flow.getTitle());
			f.setData(flow.getData());
			f.setBack(flow.isBack());
			JsonNode steps = node.get("steps");
			AtomicInteger ordinal = new AtomicInteger(1);
			LinkedList<Step> stepList = new LinkedList<>();
			if(steps != null && steps.isArray()) {
				for(JsonNode step : steps){
					Step s = stepFactory.from(step);
					s.setOrd(ordinal.getAndIncrement());
					stepList.add(s);
				}
			}
			f.setSteps(stepList);
			JsonNode defaultTagsNode = node.get("defaultExecutionTags");
			if (defaultTagsNode != null && defaultTagsNode.isObject()) {
				f.setDefaultExecutionTags(objectMapper.convertValue(defaultTagsNode, new TypeReference<Map<String, String>>() {}));
			}
			if (agentBatchedLogger != null) {
				f.setLogWriter((agentId, flowExecutionId, flowExecutionStepId, message) -> {
					agentBatchedLogger.logWithAgentId(agentId, flowExecutionId, flowExecutionStepId, message);
				});
			}
			uninstall(name);
			f.initialize();
			if(flow.getTimer() != null){
				TimerImpl timer = beanManager.findTimer(flow.getTimer());
				timer.add(new FlowRunnable(f, flow.getTimer()));
				f.setTimerName(timer.getName());
			}
			flows.put(name, f);
			apiClient.send(NotifyFlowReady.builder().flowName(name).build());
			return f;
		} catch(BaseRuntimeException bre){
			uninstall(name);
			if (agentBatchedLogger != null) {
				agentBatchedLogger.logError(null, null, LogMessage.error("error-in-initializing-flow", bre));
			}
			notifyInitFailure(name, bre);
		} catch (Exception ex){
			uninstall(name);
			if (agentBatchedLogger != null) {
				agentBatchedLogger.logError(null, null, LogMessage.error("error-in-creating-flow", ex));
			}
			BaseRuntimeException e = Exceptions.server("error-creating-flow").withCause(ex)
				.onEntity("flow", name)
				.withExtra("detailMessage", ex.getMessage()).get();
			notifyInitFailure(name, e);
		}
		return null;
	}

	public Optional<Flow> findByName(String name) {
		return !flows.containsKey(name)?Optional.empty():Optional.of(flows.get(name));
	}

	public List<String> flowNames() {
		return List.copyOf(flows.keySet());
	}

	Flow uninstall(String name) {
		if (name == null) {
			return null;
		}
		Flow old = flows.remove(name);
		if (old != null && old.getTimerName() != null) {
			TimerImpl oldTimer = beanManager.findTimer(old.getTimerName());
			oldTimer.remove(name);
		}
		return old;
	}

	private void notifyInitFailure(String flowName, BaseRuntimeException bre) {
		if (bre.getEntityType() == null) {
			bre.setEntityType("flow");
		}
		if (bre.getEntityName() == null) {
			bre.setEntityName(flowName);
		}
		bre.withExtra(NotifyError.EXTRA_PHASE, NotifyError.PHASE_INIT);
		apiClient.send(NotifyError.builder()
			.entityType("flow")
			.entityName(flowName)
			.exception(bre)
			.build());
	}

	public class FlowRunnable implements NamedRunnable
	{
		private Flow flow;
		@Getter
		private String timerName;
		@Override
		public String getName(){
			return flow.getName();
		}
		private FlowRunnable(Flow flow, String timerName){
			this.flow = flow;
			this.timerName = timerName;
		}

		@Override
		public void run() {
			Map<String, String> tags = new HashMap<>();
			if (flow.getDefaultExecutionTags() != null) {
				tags.putAll(flow.getDefaultExecutionTags());
			}
			tags.put("date", dateUtils.getDateString(LocalDateTime.now()));
			tags.put("time", dateUtils.getTimeString(LocalDateTime.now()));
			tags.put("timer", this.timerName);
			FlowExecution execution = apiClient.initiate(flow.getName(), tags);
			FlowExecution updatedExecution = flow.execute(execution.getVersion().getId(), tags, execution.getVersion().getFiles(), execution.getId());
			if(updatedExecution != null){
                updatedExecution = apiClient.saveFlowExecution(updatedExecution);
            }
		}
	}
}
