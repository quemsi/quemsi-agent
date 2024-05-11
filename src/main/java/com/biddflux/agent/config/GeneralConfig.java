package com.biddflux.agent.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.biddflux.agent.AgentCoordinator;
import com.biddflux.commons.util.DateUtils;
import com.biddflux.commons.util.FileNameUtil;
import com.biddflux.commons.util.JsonUtils;
import com.biddflux.model.flow.db.sql.SqlParser;
import com.biddflux.model.flow.in.MySqlBackupProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration(proxyBeanMethods = false)
public class GeneralConfig {
	@Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.featuresToEnable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
            .featuresToEnable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            ;
    }

	@Bean
	public AgentCoordinator agentController(){
		return new AgentCoordinator();
	}
	
	@Bean
	public EnvironmentVars environmentVars() {
		return new EnvironmentVars();
	}
    
	@Bean
	@ConfigurationProperties(prefix = "mysqlbackup")
	public MySqlBackupProperties mySqlBackupProperties(){
		return new MySqlBackupProperties();
	}

    @Bean(destroyMethod = "shutdown")
	public ExecutorService vThreadExecutor(){
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("command-executor-service").factory());
	}
	
	@Bean
	public JsonUtils jsonUtils(){
		return new JsonUtils();
	}
	
	@Bean
	public FileNameUtil fileNameUtil(){
		return new FileNameUtil();
	}

	@Bean
	public DateUtils dateUtils(){
		return new DateUtils();
	}

	@Bean
	public SqlParser sqlParser(){
		return new SqlParser();
	}
}
