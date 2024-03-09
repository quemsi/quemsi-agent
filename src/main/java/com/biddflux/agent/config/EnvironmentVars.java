package com.biddflux.agent.config;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;

import lombok.Data;

@Data
public class EnvironmentVars {
	@Value("${BAKERUP_HOME:~/biddflux-agent}")
    private String homeDir;
	@Value("${google-drives-files:googleDrives}")
	private String googleDriveFilesRoot;
	
	public String googleDriveFilesLocation() {
		return homeDir + File.separator + googleDriveFilesRoot;
	}
}
