package com.quemsi.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.api.ApiClient;
import com.quemsi.model.dto.DataGroup;
import com.quemsi.model.dto.FlowDetail;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.dto.agent.onapi.NotifyFlowReady;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.factories.StepFactory;

class FlowManagerTest {

    private FlowManager flowManager;
    private ApiClient apiClient;
    private StepFactory stepFactory;

    @BeforeEach
    void setUp() {
        flowManager = new FlowManager();
        apiClient = mock(ApiClient.class);
        stepFactory = mock(StepFactory.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Flow> flowObjectProvider = mock(ObjectProvider.class);
        when(flowObjectProvider.getObject()).thenAnswer(inv -> new Flow());

        ReflectionTestUtils.setField(flowManager, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(flowManager, "stepFactory", stepFactory);
        ReflectionTestUtils.setField(flowManager, "flowObjectProvider", flowObjectProvider);
        ReflectionTestUtils.setField(flowManager, "apiClient", apiClient);
        ReflectionTestUtils.setField(flowManager, "beanManager", mock(SpringBeanManager.class));
        ReflectionTestUtils.setField(flowManager, "agentBatchedLogger", mock(AgentBatchedLogger.class));
    }

    @Test
    void registersFlowUnderDtoNameNotNestedJsonName() {
        FlowDetail detail = activeFlow("real-flow",
            "{\"name\":\"json-flow\",\"data\":{\"name\":\"dataset\"},\"agent\":{\"name\":\"agent-1\"},\"steps\":[]}");

        Flow created = flowManager.createNewFlow(detail);

        assertThat(created.getName()).isEqualTo("real-flow");
        assertThat(flowManager.findByName("real-flow")).contains(created);
        assertThat(flowManager.findByName("json-flow")).isEmpty();
        assertThat(flowManager.findByName("dataset")).isEmpty();
        verify(apiClient).send(any(NotifyFlowReady.class));
    }

    @Test
    void inactiveFlowWithoutTimerIsRemoved() {
        FlowDetail active = activeFlow("manual-flow", "{\"name\":\"manual-flow\",\"steps\":[]}");
        flowManager.createNewFlow(active);
        assertThat(flowManager.findByName("manual-flow")).isPresent();

        FlowDetail inactive = activeFlow("manual-flow", "{\"name\":\"manual-flow\",\"steps\":[]}");
        inactive.setActive(false);
        flowManager.createNewFlow(inactive);

        assertThat(flowManager.findByName("manual-flow")).isEmpty();
        verify(apiClient).send(any(NotifyFlowReady.class));
    }

    @Test
    void initFailureUnregistersAndNotifies() {
        FlowDetail detail = activeFlow("broken-flow",
            "{\"name\":\"broken-flow\",\"steps\":[{\"type\":\"From\"}]}");
        when(stepFactory.from(any())).thenThrow(Exceptions.badRequest("not-supported-object-type").get());

        Flow created = flowManager.createNewFlow(detail);

        assertThat(created).isNull();
        assertThat(flowManager.findByName("broken-flow")).isEmpty();
        verify(apiClient).send(any(NotifyError.class));
        verify(apiClient, never()).send(any(NotifyFlowReady.class));
    }

    private static FlowDetail activeFlow(String name, String model) {
        DataGroup data = new DataGroup();
        data.setId(1L);
        data.setName("dataset");
        FlowDetail detail = new FlowDetail();
        detail.setId(10L);
        detail.setActive(true);
        detail.setName(name);
        detail.setTitle(name);
        detail.setData(data);
        detail.setModel(model);
        return detail;
    }
}
