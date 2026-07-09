package com.quemsi.agent;

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

}
