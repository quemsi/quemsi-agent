package com.quemsi.agent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentApplicationDashDTest {

	@AfterEach
	void clearProps() {
		System.clearProperty("quemsi.temp-dir");
		System.clearProperty("CLIENT_ID");
	}

	@Test
	void applyDashDSystemPropertiesSetsAndStrips() {
		String[] remaining = AgentApplication.applyDashDSystemProperties(new String[] {
			"-Dquemsi.temp-dir=/var/quemsi/tmp",
			"-DCLIENT_ID=agent-1",
			"--other"
		});
		assertThat(System.getProperty("quemsi.temp-dir"), equalTo("/var/quemsi/tmp"));
		assertThat(System.getProperty("CLIENT_ID"), equalTo("agent-1"));
		assertThat(remaining, equalTo(new String[] { "--other" }));
	}

	@Test
	void promoteTempDirFromEnvDoesNotOverrideExistingProperty() {
		System.setProperty("quemsi.temp-dir", "/already");
		AgentApplication.promoteTempDirFromEnv();
		assertThat(System.getProperty("quemsi.temp-dir"), equalTo("/already"));
	}

	@Test
	void applyDashDIgnoresMalformed() {
		String[] remaining = AgentApplication.applyDashDSystemProperties(new String[] {
			"-D",
			"-Dnorequals",
			"plain"
		});
		assertThat(System.getProperty("quemsi.temp-dir"), nullValue());
		assertThat(remaining, equalTo(new String[] { "-D", "-Dnorequals", "plain" }));
	}
}
