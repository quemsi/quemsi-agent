package com.quemsi.agent.service;

import org.springframework.stereotype.Service;

@Service

public class GoogleDriveManager {
	// @Autowired
	// private DefaultListableBeanFactory beanFactory;
	// @Autowired
	// private ApiClient apiClient;
	// @Autowired
	// private SpringBeanManager springBeanManager;

	public void connectToDrives() {
		// springBeanManager.findGoogleDrives().forEach(Exceptions.<GoogleDrive, Exception>wrapConsumer(gd -> {
		// 	try{
		// 		gd.setBrowserConsumer((driveName, authUrl) -> apiClient.send(UpdateGoogleDrive.builder().driveName(driveName).authUrl(authUrl).connected(false).build()));
		// 		gd.connectToDrive();
		// 		if(gd.isConnected()){
		// 			apiClient.send(UpdateGoogleDrive.builder().driveName(gd.getName()).connected(true).build());
		// 		}
		// 	}catch(Exception e){
		// 		apiClient.send(NotifyError.builder().entityType("google-drive").entityName(gd.getName()).exception(Exceptions.server("agent").withCause(e).get()).build());
		// 	}
		// }));
	}
	
	// public List<GoogleDrive> googleDriveBeans() {
	// 	return List.copyOf(beanFactory.getBeansOfType(GoogleDrive.class).values());
	// }
}
