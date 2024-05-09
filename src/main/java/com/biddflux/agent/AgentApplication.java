package com.biddflux.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

import com.biddflux.agent.config.AotHints;

@SpringBootApplication
@ImportRuntimeHints(AotHints.class)
public class AgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentApplication.class, args);
	}

}
