package com.quemsi.agent.service;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.quemsi.agent.flow.TimerImpl;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.api.ApiClient;
import com.quemsi.model.dto.AgentModel;
import com.quemsi.model.dto.DatasourceType;
import com.quemsi.model.dto.StorageType;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.flow.Timer;
import com.quemsi.model.flow.db.DataSourceFactory;
import com.quemsi.model.flow.db.mysql.DataSourceFactoryMySql;
import com.quemsi.model.flow.db.postgres.DatasourceFactoryPostgres;
import com.quemsi.model.flow.db.sqlserver.DatasourceFactorySqlserver;
import com.quemsi.model.flow.out.ABStorage;
import com.quemsi.model.flow.out.AWS3Storage;
import com.quemsi.model.flow.out.AWSS3Drive;
import com.quemsi.model.flow.out.AzureBlobDrive;
import com.quemsi.model.flow.out.LStorage;
import com.quemsi.model.flow.out.LocalDrive;
import com.quemsi.model.flow.out.Storage;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SpringBeanManager {
	@Autowired
	private DefaultListableBeanFactory beanFactory;
	@Autowired
	protected ApplicationContext context;
	@Autowired
	protected ApiClient apiClient;

	public TimerImpl findTimer(String name){
		return beanFactory.getBean(name, TimerImpl.class);
	}
	
	public List<Timer> findTimers(){
		return List.copyOf(beanFactory.getBeansOfType(Timer.class).values());
	}

	public TimerImpl registerTimer(AgentModel.Timer timer) {
		BeanReqisterer<TimerImpl> registerer = new BeanReqisterer<>(timer.getName(), TimerImpl.class, () -> new TimerImpl());
		if(!timer.isActive()){
			registerer.destroy();
			return null;
		}else{
			TimerImpl t = registerer.getBean();
			t.setName(timer.getName());  
			t.setSchedule(timer.getSchedule());
			registerer.register(true);
			if(!registerer.isNew()){
				t.reset();
			}else{
				t.init();
			}
			return t;
		}
	}
	
	public void registerDatasource(AgentModel.Datasource datasource) {
		BeanReqisterer<? extends DataSourceFactory> registerer = null;
		if(DatasourceType.MYSQL.equals(datasource.getType())){
			registerer = new BeanReqisterer<DataSourceFactoryMySql>(datasource.getName(), DataSourceFactoryMySql.class, () -> new DataSourceFactoryMySql());
		} else if(DatasourceType.POSTGRES.equals(datasource.getType())){
			registerer = new BeanReqisterer<>(datasource.getName(), DatasourceFactoryPostgres.class, ()-> new DatasourceFactoryPostgres());
		} else if(DatasourceType.SQLSERVER.equals(datasource.getType())){
			registerer = new BeanReqisterer<>(datasource.getName(), DatasourceFactorySqlserver.class, ()-> new DatasourceFactorySqlserver());
		} else {
			throw Exceptions.server("not-implemented-datasource-type").withExtra("type", datasource.getType()).withExtra("name", datasource.getName()).get();
		}
		if(!datasource.isActive()){
			registerer.destroy();
			return;
		}
		DataSourceFactory dsFactory = registerer.getBean();
		dsFactory.setName(datasource.getName());
		dsFactory.setDbName(datasource.getDbName());
		dsFactory.setSchema(datasource.getSchema());
		dsFactory.setUrl(datasource.getUrl());
		if(datasource.isUseEnvVar()){
			Environment environment = context.getEnvironment();
			log.debug("{} var value : {}", "MYSQLUSER", environment.getProperty("MYSQLUSER"));
			log.debug("{} var value : {}", "MYSQLPASS", environment.getProperty("MYSQLPASS"));
			dsFactory.setUsername(environment.getProperty(datasource.getUsername()));
			dsFactory.setPassword(environment.getProperty(datasource.getPassword()));
			
			if(StringUtils.isEmptyOrNull(dsFactory.getUsername()) || StringUtils.isEmptyOrNull(dsFactory.getPassword())){
				BaseRuntimeException ex = Exceptions.badRequest("environment-vars-not-set").withExtra("vars", datasource.getUsername() + "," + datasource.getPassword()).get();
				apiClient.send(NotifyError.builder().entityType("datasource").entityName(datasource.getName()).exception(ex).build());
				ex.printStackTrace();
			}
		}else{
			dsFactory.setUsername(datasource.getUsername());
			dsFactory.setPassword(datasource.getPassword());
		}
		registerer.register();
	}
	
	public void registerLocalDrive(AgentModel.LocalDrive localDrive) {
		BeanReqisterer<LocalDrive> registerer = new BeanReqisterer<>(localDrive.getName(), LocalDrive.class, () -> new LocalDrive());
		if(!localDrive.isActive()){
			registerer.destroy();
			return;
		}
		LocalDrive drive = registerer.getBean();
		drive.setName(localDrive.getName());
		drive.setStorageRoot(localDrive.getStorageRoot());
		drive.setCapacity(localDrive.getCapacity());
		drive.setUsedSize(localDrive.getUsedSize());
		registerer.register();
	}

	public void registerAzureBlobDrive(AgentModel.AzureBlobDrive azureBlobDrive) {
		BeanReqisterer<AzureBlobDrive> registerer = new BeanReqisterer<>(azureBlobDrive.getName(), AzureBlobDrive.class, () -> new AzureBlobDrive());
		if(!azureBlobDrive.isActive()){
			registerer.destroy();
			return;
		}
		AzureBlobDrive drive = registerer.getBean();
		drive.setName(azureBlobDrive.getName());
		drive.setAccountName(azureBlobDrive.getAccountName());
		if(azureBlobDrive.isUseEnvVar()){
			Environment environment = context.getEnvironment();
			log.debug("{} var value : {}", azureBlobDrive.getAccountKey(), environment.getProperty(azureBlobDrive.getAccountKey()));
			drive.setAccountKey(environment.getProperty(azureBlobDrive.getAccountKey()));
			if(StringUtils.isEmptyOrNull(drive.getAccountKey())){
				BaseRuntimeException ex = Exceptions.badRequest("environment-vars-not-set").withExtra("vars", azureBlobDrive.getAccountKey()).get();
				apiClient.send(NotifyError.builder().entityType("azure-blob-storage").entityName(azureBlobDrive.getName()).exception(ex).build());
				ex.printStackTrace();
			}
		}else{
			drive.setAccountKey(azureBlobDrive.getAccountKey());
		}
		drive.setStorageRoot(azureBlobDrive.getStorageRoot());
		drive.setCapacity(azureBlobDrive.getCapacity());
		drive.setUsedSize(azureBlobDrive.getUsedSize());
		registerer.register();
	}

	public void registerAWSS3Drive(AgentModel.AWSS3Drive awsS3Drive) {
		BeanReqisterer<AWSS3Drive> registerer = new BeanReqisterer<>(awsS3Drive.getName(), AWSS3Drive.class, () -> new AWSS3Drive());
		if(!awsS3Drive.isActive()){
			registerer.destroy();
			return;
		}
		AWSS3Drive drive = registerer.getBean();
		drive.setName(awsS3Drive.getName());
		drive.setAccessKey(awsS3Drive.getAccessKey());
		drive.setSecretKey(awsS3Drive.getSecretKey());
		drive.setRegion(awsS3Drive.getRegion());
		drive.setBucketName(awsS3Drive.getBucketName());
		if(awsS3Drive.isUseEnvVar()){
			Environment environment = context.getEnvironment();
			log.debug("{} var value : {}", awsS3Drive.getAccessKey(), environment.getProperty(awsS3Drive.getAccessKey()));
			log.debug("{} var value : {}", awsS3Drive.getSecretKey(), environment.getProperty(awsS3Drive.getSecretKey()));
			drive.setAccessKey(environment.getProperty(awsS3Drive.getAccessKey()));
			drive.setSecretKey(environment.getProperty(awsS3Drive.getSecretKey()));
			if(StringUtils.isEmptyOrNull(drive.getAccessKey()) || StringUtils.isEmptyOrNull(drive.getSecretKey())){
				BaseRuntimeException ex = Exceptions.badRequest("environment-vars-not-set").withExtra("vars", awsS3Drive.getAccessKey() + "," + awsS3Drive.getSecretKey()).get();
				apiClient.send(NotifyError.builder().entityType("aws-s3-storage").entityName(awsS3Drive.getName()).exception(ex).build());
				ex.printStackTrace();
			}
		}else{
			drive.setAccessKey(awsS3Drive.getAccessKey());
			drive.setSecretKey(awsS3Drive.getSecretKey());
		}
		drive.setStorageRoot(awsS3Drive.getStorageRoot());
		drive.setCapacity(awsS3Drive.getCapacity());
		drive.setUsedSize(awsS3Drive.getUsedSize());
		registerer.register();
	}

	public List<Storage> findStorages(){
		return List.copyOf(beanFactory.getBeansOfType(Storage.class).values());
	}

	public Storage findStorage(String name){
		return beanFactory.getBean(name, Storage.class);
	}

	public void registerStroge(AgentModel.Storage storage) {
		try{
			if (StorageType.LOCAL.equals(storage.getType())){
				BeanReqisterer<LStorage> registerer = new BeanReqisterer<>(storage.getName(), LStorage.class, () -> new LStorage());
				if(!storage.isActive()){
					registerer.destroy();
					return;
				}
				LStorage ls = registerer.getBean();
				ls.setName(storage.getName());
				try{
					ls.setLocalDrive(beanFactory.getBean(storage.getLoc(), LocalDrive.class));
				}catch(NoSuchBeanDefinitionException e){
					throw Exceptions.server("not-existing-drive").withExtra("storageName", storage.getName()).withExtra("driveName", storage.getLoc()).withExtra("type", storage.getType()).get();
				}
				ls.setRootPath(storage.getRootPath());
				ls.setRetentionPolicy(storage.getRetentionPolicy());
				ls.setUsedSize(storage.getUsedSize());
				ls.setCapacity(storage.getCapacity());
				ls.setUtil(context.getBean(FileNameUtil.class));
				registerer.register();
			} else if (StorageType.AZUREBLOB.equals(storage.getType())){
				BeanReqisterer<ABStorage> registerer = new BeanReqisterer<>(storage.getName(), ABStorage.class, () -> new ABStorage());
				if(!storage.isActive()){
					registerer.destroy();
					return;
				}
				ABStorage ls = registerer.getBean();
				ls.setName(storage.getName());
				try{
					AzureBlobDrive azureDrive = beanFactory.getBean(storage.getLoc(), AzureBlobDrive.class);
					ls.setAzureBlobDrive(azureDrive);
					ls.setUnderlyingStorage(new AzureBlobStorage(azureDrive));
				}catch(NoSuchBeanDefinitionException e){
					throw Exceptions.server("not-existing-drive").withExtra("storageName", storage.getName()).withExtra("driveName", storage.getLoc()).withExtra("type", storage.getType()).get();
				}
				ls.setRootPath(storage.getRootPath());
				ls.setRetentionPolicy(storage.getRetentionPolicy());
				ls.setUsedSize(storage.getUsedSize());
				ls.setCapacity(storage.getCapacity());
				ls.setUtil(context.getBean(FileNameUtil.class));
				registerer.register();
			} else if (StorageType.AWSS3.equals(storage.getType())){
				BeanReqisterer<AWS3Storage> registerer = new BeanReqisterer<>(storage.getName(), AWS3Storage.class, () -> new AWS3Storage());
				if(!storage.isActive()){
					registerer.destroy();
					return;
				}
				AWS3Storage ls = registerer.getBean();
				ls.setName(storage.getName());
				try{
					AWSS3Drive awsS3Drive = beanFactory.getBean(storage.getLoc(), AWSS3Drive.class);
					ls.setAwsS3Drive(awsS3Drive);
					ls.setUnderlyingStorage(new AWSS3Storage(awsS3Drive));
				}catch(NoSuchBeanDefinitionException e){
					throw Exceptions.server("not-existing-drive").withExtra("storageName", storage.getName()).withExtra("driveName", storage.getLoc()).withExtra("type", storage.getType()).get();
				}
				ls.setRootPath(storage.getRootPath());
				ls.setRetentionPolicy(storage.getRetentionPolicy());
				ls.setUsedSize(storage.getUsedSize());
				ls.setCapacity(storage.getCapacity());
				ls.setUtil(context.getBean(FileNameUtil.class));
				registerer.register();
			} else {
				throw Exceptions.server("not-implemented-yet").withExtra("type", storage.getType()).withExtra("name", storage.getName()).get();
			}
		}catch(BaseRuntimeException e){
			log.error("error-in-registering-storage", e);
			apiClient.send(NotifyError.builder().entityType("storage").entityName(storage.getName()).exception(e).build());
		}
	}

	private class BeanReqisterer<T>{
		private String name;
		private Class<T> clazz;
		private T bean = null;
		private Supplier<T> instanceSupplier;
		private boolean newBean = false;
		public BeanReqisterer(String name, Class<T> clazz, Supplier<T> insSupplier){
			this.name = name;
			this.clazz = clazz;
			this.instanceSupplier = insSupplier;
		}
		public T getBean(){
			if(beanFactory.containsBean(name)){
				bean = beanFactory.getBean(name, clazz);
			}else{
				bean = instanceSupplier.get();
				newBean = true;
			}
			return bean;
		}
		public void register(boolean autowire){
			if(newBean){
				if(autowire){
					beanFactory.autowireBean(bean);
				}
				beanFactory.registerSingleton(name, bean);
			}
		}
		public void destroy(){
			beanFactory.destroySingleton(name);
		}
		public void register(){
			register(false);
		}
		public boolean isNew(){
			return newBean;
		}
	}
}
