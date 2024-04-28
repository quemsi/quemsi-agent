package com.biddflux.agent.service;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import com.biddflux.agent.api.ApiClient;
import com.biddflux.commons.util.BaseRuntimeException;
import com.biddflux.commons.util.DateUtils;
import com.biddflux.commons.util.Exceptions;
import com.biddflux.model.dto.DataVersion;
import com.biddflux.model.dto.FlowDetail;
import com.biddflux.model.dto.agent.onapi.NotifyError;
import com.biddflux.model.flow.Flow;
import com.biddflux.model.flow.Step;
import com.biddflux.model.flow.Timer;
import com.biddflux.model.flow.Timer.NamedRunnable;
import com.biddflux.model.flow.factories.StepFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
	
	public Flow createNewFlow(FlowDetail flow) {
		String model = null;
		try {
			model = flow.getModel();
			JsonNode node = objectMapper.readTree(model.getBytes(Charset.forName("UTF-8")));
			Flow f = flowObjectProvider.getObject();
			String name = node.findValue("name").asText(null);
			f.setId(flow.getId());
			f.setName(name);
			f.setActive(flow.isActive());
			f.setTitle(flow.getTitle());
			f.setData(flow.getData());
			f.setBack(flow.isBack());
			JsonNode steps = node.get("steps");
			LinkedList<Step> stepList = new LinkedList<>();
			if(steps != null && steps.isArray()) {
				for(JsonNode step : steps){
					Step s = stepFactory.from(step);
					stepList.add(s);
				}
			}
			f.setSteps(stepList);
			flows.put(name, f);
			if(flow.getTimer() != null){
				Timer timer = beanManager.findTimer(flow.getTimer());
				timer.add(new FlowRunnable(f, flow.getTimer()));
			}
			f.initialize();
			return f;
		} catch(BaseRuntimeException bre){
			log.error("error-in-initializing-flow", bre);
			apiClient.send(NotifyError.builder().exception(bre).build());
		} catch (Exception ex){
			log.error("error-in-creating-flow", ex);
			BaseRuntimeException e = Exceptions.server("error-creating-flow").withCause(ex)
				.onEntity("flow", flow.getName())
				.withExtra("detailMessage", ex.getMessage()).get();
			apiClient.send(NotifyError.builder().exception(e).build());
		}
		return null;
	}

	public Optional<Flow> findByName(String name) {
		return !flows.containsKey(name)?Optional.empty():Optional.of(flows.get(name));
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
			Map<String, String> tags = Map.of("date", dateUtils.getDateString(LocalDateTime.now())
				, "time", dateUtils.getTimeString(LocalDateTime.now()),
				"timer", this.timerName);
			DataVersion version = apiClient.findVersion(flow.getName(), tags);
			flow.execute(Optional.ofNullable(version).map(v -> v.getId()).orElse(null), tags, version.getFiles());
		}
	}
}
