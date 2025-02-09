package com.quemsi.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(AgentRuntimeHintsRegistrar.class)
public class AgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentApplication.class, args);
	}

}
