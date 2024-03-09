package com.biddflux.agent.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Service;

import com.biddflux.model.flow.out.GoogleDrive;

@Service

public class GoogleDriveManager {
	//TODO: how this should work in agent
	// @Autowired
	// private GoogleDriveServiceImpl googleDriveServiceImpl;
	@Autowired
	private DefaultListableBeanFactory beanFactory;
	// @Autowired
	// private ExecutorService vExecutorService;

	public void connectToDrives() {
		// googleDriveServiceImpl.findAll().forEach(gde -> {
		// 	googleDriveExecutor.execute(()->{
		// 		GoogleDrive gd = beanFactory.getBean(gde.getName(), GoogleDrive.class);
		// 		try {
		// 			gd.connectToDrive();
		// 		} catch (Exception e) {
		// 			log.error("error connection to drive ", e);
		// 			gd.setError(StringUtils.stackTraceOf(e));
		// 		}
		// 	});
		// });
	}
	
	public List<GoogleDrive> googleDriveBeans() {
		return List.copyOf(beanFactory.getBeansOfType(GoogleDrive.class).values());
	}
}
