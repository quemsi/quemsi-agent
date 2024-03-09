package com.biddflux.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.biddflux.agent.service.FlowManager;
import com.biddflux.commons.util.Exceptions;
import com.biddflux.model.flow.Flow;
import com.biddflux.model.flow.factories.SourceFactory;
import com.biddflux.model.flow.factories.StepFactory;
import com.biddflux.model.flow.factories.StorageFactory;
import com.biddflux.model.flow.retention.Default;
import com.biddflux.model.flow.retention.Noretention;
import com.biddflux.model.flow.retention.RetentionPolicy;

@Configuration
public class FlowConfig {
    @Bean
    public FlowManager flowManager(){
        return new FlowManager();
    }
    @Bean
	@Scope("prototype")
	public RetentionPolicy retentionPolicy(String name, long count, long size) {
		if("default".equals(name)){
			Default def = new Default();
			def.setCountLimit(count);
			def.setSizeLimit(size);
			return def;
		}
		if("noretention".equals(name)){
			return new Noretention();
		}
		throw Exceptions.server("not-supported-retention-policy").withExtra("name", name).get();
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
