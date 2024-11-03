package com.quemsi.agent.config;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import com.quemsi.agent.service.FlowManager;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.factories.SourceFactory;
import com.quemsi.model.flow.factories.StepFactory;
import com.quemsi.model.flow.factories.StorageFactory;

@Configuration(proxyBeanMethods = false)
public class FlowConfig {
    @Bean
    public FlowManager flowManager(){
        return new FlowManager();
    }
   
	@Bean
	public StepFactory stepFactory() {
		return new StepFactory();
	}
	
	@Bean
	public SourceFactory sourceFactory() {
		return new SourceFactory();
	}
	
	@Bean
	public StorageFactory storageFactory() {
		return new StorageFactory();
	}
	
	@Bean
	@Scope("prototype")
	public Flow flow() {
		return new Flow();
	}

	@Bean
	public SchedulerFactoryBean schedulerFactoryBean() {        
		SchedulerFactoryBean scheduler = new SchedulerFactoryBean();
		Properties quartzProperties = new Properties();     
		quartzProperties.put("org.quartz.threadPool.threadCount", "1");
		scheduler.setQuartzProperties(quartzProperties);
		return scheduler;
	}
}
