package com.biddflux.agent.config;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.biddflux.agent.AgentCoordinator;
import com.biddflux.commons.util.ApacheDurationDeserializer;
import com.biddflux.commons.util.ApacheDurationSerializer;
import com.biddflux.commons.util.DateUtils;
import com.biddflux.commons.util.FileNameUtil;
import com.biddflux.commons.util.JsonUtils;
import com.biddflux.model.flow.db.sql.SqlParser;
import com.biddflux.model.flow.in.MySqlBackupProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

@Configuration(proxyBeanMethods = false)
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
