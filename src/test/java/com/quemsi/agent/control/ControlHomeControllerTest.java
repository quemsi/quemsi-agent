package com.quemsi.agent.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import com.quemsi.agent.AgentCoordinator;
import com.quemsi.agent.flow.TimerImpl;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.model.flow.db.DataSourceFactory;

class ControlHomeControllerTest {

    @Test
    void homeRendersAgentIdentityAndUiGuidance() throws Exception {
        ControlHomeController controller = new ControlHomeController();
        AgentCoordinator coordinator = mock(AgentCoordinator.class);
        when(coordinator.isInitialized()).thenReturn(true);
        SpringBeanManager beans = mock(SpringBeanManager.class);
        when(beans.findStorages()).thenReturn(List.of());
        FlowManager flows = mock(FlowManager.class);
        when(flows.flowNames()).thenReturn(List.of("nightly-backup"));
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansOfType(DataSourceFactory.class)).thenReturn(Map.of());
        when(ctx.getBeansOfType(TimerImpl.class)).thenReturn(Map.of());

        ReflectionTestUtils.setField(controller, "agentCoordinator", coordinator);
        ReflectionTestUtils.setField(controller, "beanManager", beans);
        ReflectionTestUtils.setField(controller, "flowManager", flows);
        ReflectionTestUtils.setField(controller, "applicationContext", ctx);
        ReflectionTestUtils.setField(controller, "agentVersion", "2.6.2-SNAPSHOT");
        ReflectionTestUtils.setField(controller, "clientId", "agent-1-1");
        ReflectionTestUtils.setField(controller, "apiUrl", "http://127.0.0.1:9081");
        ReflectionTestUtils.setField(controller, "uiUrl", "");

        ResponseEntity<String> response = controller.home();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("This is not the Quemsi UI")
                .contains("Configure on agent")
                .contains("agent-1-1")
                .contains("2.6.2-SNAPSHOT")
                .contains("http://127.0.0.1:9081")
                .contains("href=\"http://127.0.0.1/app/\"")
                .contains("Open Quemsi UI")
                .contains("nightly-backup")
                .contains("Ready")
                .doesNotContain("{{");
    }

    @Test
    void resolveUiUrlPrefersConfiguredValue() {
        assertThat(ControlHomeController.resolveUiUrl("https://console.example.com/app/", "https://quemsi.com"))
                .isEqualTo("https://console.example.com/app/");
    }

    @Test
    void resolveUiUrlDerivesFromPublicApiOrigin() {
        assertThat(ControlHomeController.resolveUiUrl("", "https://quemsi.com"))
                .isEqualTo("https://quemsi.com/app/");
    }

    @Test
    void resolveUiUrlDropsLocalApiPort() {
        assertThat(ControlHomeController.resolveUiUrl(null, "http://127.0.0.1:9081"))
                .isEqualTo("http://127.0.0.1/app/");
    }

    @Test
    void resolveUiUrlFallsBackWhenApiHostIsNotBrowsable() {
        assertThat(ControlHomeController.resolveUiUrl("", "http://api:8081"))
                .isEqualTo("https://quemsi.com/app/");
    }
}
