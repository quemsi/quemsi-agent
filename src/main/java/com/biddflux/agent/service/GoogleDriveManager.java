package com.biddflux.agent.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Service;

import com.biddflux.agent.api.ApiClient;
import com.biddflux.agent.flow.gdrive.GoogleDrive;
import com.biddflux.commons.util.Exceptions;
import com.biddflux.model.dto.agent.onapi.NotifyError;
import com.biddflux.model.dto.agent.onapi.UpdateGoogleDrive;

@Service

public class GoogleDriveManager {
	@Autowired
	private DefaultListableBeanFactory beanFactory;
	@Autowired
	private ApiClient apiClient;
	@Autowired
	private SpringBeanManager springBeanManager;

	public void connectToDrives() {
		springBeanManager.findGoogleDrives().forEach(Exceptions.<GoogleDrive, Exception>wrapConsumer(gd -> {
			try{
				gd.setBrowserConsumer((driveName, authUrl) -> apiClient.send(UpdateGoogleDrive.builder().driveName(driveName).authUrl(authUrl).connected(false).build()));
				gd.connectToDrive();
				if(gd.isConnected()){
					apiClient.send(UpdateGoogleDrive.builder().driveName(gd.getName()).connected(true).build());
				}
			}catch(Exception e){
				apiClient.send(NotifyError.builder().entityType("google-drive").entityName(gd.getName()).exception(Exceptions.server("agent").withCause(e).get()).build());
			}
		}));
	}
	
	public List<GoogleDrive> googleDriveBeans() {
		return List.copyOf(beanFactory.getBeansOfType(GoogleDrive.class).values());
	}
}
