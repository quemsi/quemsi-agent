package com.quemsi.agent.config;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.quemsi.agent.AgentCoordinator;
import com.quemsi.agent.service.AgentCommandExecutor;
import com.quemsi.agent.service.cmd.ExecuteExecuteFlow;
import com.quemsi.agent.service.cmd.ExecuteRetentionExecute;
import com.quemsi.agent.service.cmd.ExecuteTestAWSS3Drive;
import com.quemsi.agent.service.cmd.ExecuteTestAzureBlobDrive;
import com.quemsi.agent.service.cmd.ExecuteTestDatasource;
import com.quemsi.agent.service.cmd.ExecuteTestFolderAccess;
import com.quemsi.agent.service.cmd.ExecuteVersionDeleteRequest;
import com.quemsi.commons.util.ApacheDurationDeserializer;
import com.quemsi.commons.util.ApacheDurationSerializer;
import com.quemsi.commons.util.DateUtils;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.JsonUtils;
import com.quemsi.model.flow.db.sql.SqlParser;

@Configuration(proxyBeanMethods = true)
public class GeneralConfig {
	private static final String dateFormat = "yyyy-MM-dd";
    private static final String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

	@Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            builder.simpleDateFormat(dateTimeFormat);
            builder.serializers(new LocalDateSerializer(DateTimeFormatter.ofPattern(dateFormat)));
            builder.deserializers(new LocalDateDeserializer(DateTimeFormatter.ofPattern(dateFormat)));
            builder.serializers(new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(dateTimeFormat)));
            builder.deserializers(new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(dateTimeFormat)));
			builder.serializers(new ApacheDurationSerializer());
            builder.deserializers(new ApacheDurationDeserializer());
            builder.serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .featuresToDisable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .featuresToDisable(MapperFeature.DEFAULT_VIEW_INCLUSION);       
        };
    }

	@Bean
	public ScheduledExecutorService scheduledExecutorService(){
		return  Executors.newSingleThreadScheduledExecutor();
	}

	@Bean
	public AgentCoordinator agentController(){
		return new AgentCoordinator();
	}
	
	@Bean
	public AgentCommandExecutor commandExecutor(){
		return new AgentCommandExecutor();
	}

	@Bean
	public ExecuteExecuteFlow executeExecuteFlow(){
		return new ExecuteExecuteFlow();
	}

	@Bean
	public ExecuteRetentionExecute executeRetentionExecute(){
		return new ExecuteRetentionExecute();
	}

	@Bean
	public ExecuteTestDatasource executeTestDatasource(){
		return new ExecuteTestDatasource();
	}

	@Bean
	public ExecuteTestFolderAccess executeTestFolderAccess(){
		return new ExecuteTestFolderAccess();
	}

	@Bean
	public ExecuteVersionDeleteRequest executeVersionDeleteRequest(){
		return new ExecuteVersionDeleteRequest();
	}
	
	@Bean
	public ExecuteTestAzureBlobDrive executeTestAzureBlobDrive(){
		return new ExecuteTestAzureBlobDrive();
	}

	@Bean
	public ExecuteTestAWSS3Drive executeTestAWSS3Drive(){
		return new ExecuteTestAWSS3Drive();
	}

	@Bean
	public EnvironmentVars environmentVars() {
		return new EnvironmentVars();
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
