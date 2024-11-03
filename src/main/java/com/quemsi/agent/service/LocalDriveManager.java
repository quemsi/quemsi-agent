package com.quemsi.agent.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Component;

import com.quemsi.model.flow.out.LocalDrive;

@Component
public class LocalDriveManager {
    @Autowired
	private DefaultListableBeanFactory beanFactory;
	
    public List<LocalDrive> getBeans() {
		return List.copyOf(beanFactory.getBeansOfType(LocalDrive.class).values());
	}
}
