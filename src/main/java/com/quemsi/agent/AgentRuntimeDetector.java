package com.quemsi.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Detects how the agent process is packaged/running for install UI selection.
 * Canonical values: windows, linux, docker, java.
 */
public final class AgentRuntimeDetector {

	public static final String WINDOWS = "windows";
	public static final String LINUX = "linux";
	public static final String DOCKER = "docker";
	public static final String JAVA = "java";

	private static final Set<String> ALLOWED = Set.of(WINDOWS, LINUX, DOCKER, JAVA);
	private static final Path DOCKER_ENV = Path.of("/.dockerenv");
	private static final Path CGROUP = Path.of("/proc/1/cgroup");

	private AgentRuntimeDetector() {
	}

	public static String detect() {
		return detect(
				() -> System.getenv("QUEMSI_RUNTIME"),
				() -> System.getProperty("os.name", ""),
				DOCKER_ENV,
				CGROUP,
				AgentRuntimeDetector::inNativeImage);
	}

	static String detect(
			Supplier<String> envOverride,
			Supplier<String> osName,
			Path dockerEnvPath,
			Path cgroupPath,
			BooleanSupplier nativeImage) {
		String override = normalize(envOverride != null ? envOverride.get() : null);
		if (override != null) {
			return override;
		}
		if (isDocker(dockerEnvPath, cgroupPath)) {
			return DOCKER;
		}
		if (nativeImage != null && nativeImage.getAsBoolean()) {
			String os = osName != null ? osName.get() : "";
			if (os != null && os.toLowerCase(Locale.ROOT).startsWith("windows")) {
				return WINDOWS;
			}
			return LINUX;
		}
		return JAVA;
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return ALLOWED.contains(normalized) ? normalized : null;
	}

	private static boolean isDocker(Path dockerEnvPath, Path cgroupPath) {
		if (dockerEnvPath != null && Files.exists(dockerEnvPath)) {
			return true;
		}
		if (cgroupPath == null || !Files.isRegularFile(cgroupPath)) {
			return false;
		}
		try {
			String content = Files.readString(cgroupPath, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
			return content.contains("docker")
					|| content.contains("containerd")
					|| content.contains("kubepods");
		} catch (IOException e) {
			return false;
		}
	}

	static boolean inNativeImage() {
		try {
			Class<?> imageInfo = Class.forName("org.graalvm.nativeimage.ImageInfo");
			Object result = imageInfo.getMethod("inImageCode").invoke(null);
			return Boolean.TRUE.equals(result);
		} catch (Throwable ignored) {
			return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
		}
	}
}
