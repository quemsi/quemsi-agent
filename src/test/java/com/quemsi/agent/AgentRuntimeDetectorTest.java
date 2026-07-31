package com.quemsi.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRuntimeDetectorTest {

	@TempDir
	Path tempDir;

	@Test
	void envOverrideTakesPrecedence() throws Exception {
		Path dockerEnv = tempDir.resolve("dockerenv");
		Files.createFile(dockerEnv);

		String runtime = AgentRuntimeDetector.detect(
				() -> "java",
				() -> "Linux",
				dockerEnv,
				tempDir.resolve("missing-cgroup"),
				() -> true);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.JAVA);
	}

	@Test
	void invalidEnvOverrideIsIgnored() throws Exception {
		Path dockerEnv = tempDir.resolve("dockerenv");
		Files.createFile(dockerEnv);

		String runtime = AgentRuntimeDetector.detect(
				() -> "macos",
				() -> "Linux",
				dockerEnv,
				tempDir.resolve("missing-cgroup"),
				() -> true);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.DOCKER);
	}

	@Test
	void dockerEnvFileDetected() throws Exception {
		Path dockerEnv = tempDir.resolve("dockerenv");
		Files.createFile(dockerEnv);

		String runtime = AgentRuntimeDetector.detect(
				() -> null,
				() -> "Linux",
				dockerEnv,
				tempDir.resolve("missing-cgroup"),
				() -> true);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.DOCKER);
	}

	@Test
	void cgroupDockerDetected() throws Exception {
		Path cgroup = tempDir.resolve("cgroup");
		Files.writeString(cgroup, "12:memory:/docker/abc123\n");

		String runtime = AgentRuntimeDetector.detect(
				() -> null,
				() -> "Linux",
				tempDir.resolve("missing-dockerenv"),
				cgroup,
				() -> true);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.DOCKER);
	}

	@Test
	void nativeWindows() {
		String runtime = AgentRuntimeDetector.detect(
				() -> null,
				() -> "Windows 11",
				tempDir.resolve("missing-dockerenv"),
				tempDir.resolve("missing-cgroup"),
				() -> true);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.WINDOWS);
	}

	@Test
	void nativeLinux() {
		String runtime = AgentRuntimeDetector.detect(
				() -> null,
				() -> "Linux",
				tempDir.resolve("missing-dockerenv"),
				tempDir.resolve("missing-cgroup"),
				() -> true);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.LINUX);
	}

	@Test
	void jvmIsJava() {
		String runtime = AgentRuntimeDetector.detect(
				() -> null,
				() -> "Linux",
				tempDir.resolve("missing-dockerenv"),
				tempDir.resolve("missing-cgroup"),
				() -> false);

		assertThat(runtime).isEqualTo(AgentRuntimeDetector.JAVA);
	}
}
