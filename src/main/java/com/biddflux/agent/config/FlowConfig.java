package com.biddflux.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.biddflux.agent.service.FlowManager;
import com.biddflux.model.flow.Flow;
import com.biddflux.model.flow.factories.SourceFactory;
import com.biddflux.model.flow.factories.StepFactory;
import com.biddflux.model.flow.factories.StorageFactory;

@Configuration
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
}
