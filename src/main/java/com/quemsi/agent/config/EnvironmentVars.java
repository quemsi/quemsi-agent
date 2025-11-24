package com.quemsi.agent.config;

import org.springframework.beans.factory.annotation.Value;

import lombok.Data;

@Data
public class EnvironmentVars {
	@Value("${BAKERUP_HOME:~/quemsi-agent}")
    private String homeDir;
}
