package com.quemsi.agent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration.class,
    org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
    org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration.class,
    org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.reactive.ReactiveMultipartAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration.class
})
@ImportRuntimeHints(AgentRuntimeHintsRegistrar.class)
@EnableScheduling
public class AgentApplication {

	public static void main(String[] args) {
		/* Native image may leave -Dkey=value in argv instead of applying them like HotSpot.
		 * QuemsiTemp and other code use System.getProperty, so apply them explicitly. */
		args = applyDashDSystemProperties(args);
		promoteTempDirFromEnv();
		for (String arg : args) {
			if (arg.equalsIgnoreCase("--version") || arg.equalsIgnoreCase("version") || arg.equalsIgnoreCase("-v")) {
				/* Read version directly from application.yml */
				String version = null;
				
				try (java.io.InputStream is = AgentApplication.class.getClassLoader()
						.getResourceAsStream("application.yml")) {
					if (is != null) {
						java.util.Scanner scanner = new java.util.Scanner(is);
						while (scanner.hasNextLine()) {
							String line = scanner.nextLine();
							if (line.trim().startsWith("version:")) {
								/* Extract version value, handling both quoted and unquoted values */
								String versionValue = line.substring(line.indexOf(':') + 1).trim();
								if (versionValue.startsWith("'") && versionValue.endsWith("'")) {
									version = versionValue.substring(1, versionValue.length() - 1);
								} else if (versionValue.startsWith("\"") && versionValue.endsWith("\"")) {
									version = versionValue.substring(1, versionValue.length() - 1);
								} else {
									version = versionValue;
								}
								break;
							}
						}
						scanner.close();
					}
				} catch (Exception ignored) {
					/* Ignore exceptions */
				}
				
				/* Fallback to other methods if reading from application.yml fails */
				if (version == null) {
					version = System.getProperty("spring.application.version");
					if (version == null) {
						Package pkg = AgentApplication.class.getPackage();
						version = (pkg != null) ? pkg.getImplementationVersion() : null;
					}
					if (version == null) {
						version = "unknown";
					}
				}
				
				// Note: System.out.println used here because this runs before Spring context initialization
				// AgentBatchedLogger is not available at this point
				System.out.println("quemsi-agent version: " + version);
				System.exit(0);
			}
		}
		SpringApplication.run(AgentApplication.class, args);
	}

	/**
	 * Apply {@code -Dkey=value} argv entries as system properties and remove them from args.
	 * Idempotent if the runtime already applied them.
	 */
	static String[] applyDashDSystemProperties(String[] args) {
		if (args == null || args.length == 0) {
			return args;
		}
		List<String> remaining = new ArrayList<>(args.length);
		for (String arg : args) {
			if (arg != null && arg.startsWith("-D") && arg.length() > 2) {
				int eq = arg.indexOf('=');
				if (eq > 2) {
					String key = arg.substring(2, eq);
					String value = arg.substring(eq + 1);
					if (!key.isBlank()) {
						System.setProperty(key, value);
						continue;
					}
				}
			}
			remaining.add(arg);
		}
		return remaining.toArray(String[]::new);
	}

	/** Ensure {@code QUEMSI_TEMP_DIR} is visible via system property for QuemsiTemp. */
	static void promoteTempDirFromEnv() {
		String existing = System.getProperty("quemsi.temp-dir");
		if (existing != null && !existing.isBlank()) {
			return;
		}
		String fromEnv = System.getenv("QUEMSI_TEMP_DIR");
		if (fromEnv != null && !fromEnv.isBlank()) {
			System.setProperty("quemsi.temp-dir", fromEnv);
		}
	}

}
