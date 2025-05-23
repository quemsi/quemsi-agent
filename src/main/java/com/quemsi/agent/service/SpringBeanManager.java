package com.quemsi.agent.service;

import java.util.List;
import java.util.function.Supplier;

// import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.quemsi.agent.flow.TimerImpl;
// import com.quemsi.agent.flow.gdrive.GoogleDrive;
// import com.quemsi.agent.flow.gdrive.Gstorage;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.FileNameUtil;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.api.ApiClient;
import com.quemsi.model.dto.StorageType;
import com.quemsi.model.dto.agent.onapi.NotifyError;
import com.quemsi.model.flow.Timer;
import com.quemsi.model.flow.db.mysql.DataSourceFactoryMySql;
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

	public TimerImpl registerTimer(String name, String schedule) {
		BeanReqisterer<TimerImpl> registerer = new BeanReqisterer<>(name, TimerImpl.class, () -> new TimerImpl());
		TimerImpl t = registerer.getBean();
		t.setName(name);  
		t.setSchedule(schedule);
		registerer.register(true);
		if(!registerer.isNew()){
			t.reset();
		}else{
			t.init();
		}
		return t;
	}
	
	public void registerDatasource(String name, String dbName, String url, String username, String password, boolean useEnvVar) {
		BeanReqisterer<DataSourceFactoryMySql> registerer = new BeanReqisterer<>(name, DataSourceFactoryMySql.class, () -> new DataSourceFactoryMySql());
		DataSourceFactoryMySql mysql = registerer.getBean();
		mysql.setName(name);
		mysql.setDbName(dbName);
		mysql.setUrl(url);
		if(useEnvVar){
			Environment environment = context.getEnvironment();
			log.debug("{} var value : {}", "MYSQLUSER", environment.getProperty("MYSQLUSER"));
			log.debug("{} var value : {}", "MYSQLPASS", environment.getProperty("MYSQLPASS"));
			mysql.setUsername(environment.getProperty(username));
			mysql.setPassword(environment.getProperty(password));
			
			if(StringUtils.isEmptyOrNull(mysql.getUsername()) || StringUtils.isEmptyOrNull(mysql.getPassword())){
				BaseRuntimeException ex = Exceptions.badRequest("environment-vars-not-set").withExtra("vars", username + "," + password).get();
				apiClient.send(NotifyError.builder().entityType("datasource").entityName(name).exception(ex).build());
				ex.printStackTrace();
			}
		}else{
			mysql.setUsername(username);
			mysql.setPassword(password);
		}
		registerer.register();
	}
	
	// public GoogleDrive findGoogleDrive(String name){
	// 	return beanFactory.getBean(name, GoogleDrive.class);
	// }

	// public List<GoogleDrive> findGoogleDrives(){
	// 	return List.copyOf(beanFactory.getBeansOfType(GoogleDrive.class).values());
	// }

	// public void registerGoogleDrive(String name, String callbackBaseUrl, Integer callbackPort) {
	// 	BeanReqisterer<GoogleDrive> registerer = new BeanReqisterer<>(name, GoogleDrive.class, () -> new GoogleDrive());
	// 	GoogleDrive googleDrive = registerer.getBean();
	// 	googleDrive.setName(name);
	// 	googleDrive.setCredentialFilePath(envVars.getGoogleDriveFilesRoot() + "/" + name);
	// 	googleDrive.setTokensDirectoryPath(envVars.getGoogleDriveFilesRoot() + "/" + name);
	// 	googleDrive.setCallbackBaseUrl(callbackBaseUrl);
	// 	googleDrive.setCallbackPort(callbackPort);
	// 	registerer.register();
	// }

	public void registerLocalDrive(String name, String storageRoot, long capacity, long usedSize) {
		BeanReqisterer<LocalDrive> registerer = new BeanReqisterer<>(name, LocalDrive.class, () -> new LocalDrive());
		LocalDrive localDrive = registerer.getBean();
		localDrive.setName(name);
		localDrive.setStorageRoot(storageRoot);
		localDrive.setCapacity(capacity);
		localDrive.setUsedSize(usedSize);
		registerer.register();
	}

	public List<Storage> findStorages(){
		return List.copyOf(beanFactory.getBeansOfType(Storage.class).values());
	}

	public Storage findStorage(String name){
		return beanFactory.getBean(name, Storage.class);
	}

	public void registerStroge(String name, StorageType type, String loc, String rootPath, String retentionPolicy, long capacity, long usedSize) {
		if(StorageType.GDRIVE.equals(type)){
			// BeanReqisterer<Gstorage> registerer = new BeanReqisterer<>(name, Gstorage.class, () -> new Gstorage());
			// Gstorage gs = registerer.getBean();
			// gs.setName(name);
			// gs.setRootPath(rootPath);
			// gs.setGoogleDrive(beanFactory.getBean(loc, GoogleDrive.class));
			// gs.setRetentionPolicy(retentionPolicy);
			// gs.setUtil(context.getBean(FileNameUtil.class));
			// registerer.register();
		} else if (StorageType.LOCAL.equals(type)){
			BeanReqisterer<LStorage> registerer = new BeanReqisterer<>(name, LStorage.class, () -> new LStorage());
			LStorage ls = registerer.getBean();
			ls.setName(name);
			ls.setLocalDrive(beanFactory.getBean(loc, LocalDrive.class));
			ls.setRootPath(rootPath);
			ls.setRetentionPolicy(retentionPolicy);
			ls.setUsedSize(usedSize);
			ls.setCapacity(capacity);
			ls.setUtil(context.getBean(FileNameUtil.class));
			registerer.register();
		} else {
			throw Exceptions.server("not-implemented-yet").withExtra("type", type).get();
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
		public void register(){
			register(false);
		}
		public boolean isNew(){
			return newBean;
		}
	}
}
