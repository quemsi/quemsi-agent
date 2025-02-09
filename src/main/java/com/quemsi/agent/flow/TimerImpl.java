package com.quemsi.agent.flow;
import java.util.HashMap;
import java.util.Map;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;

import com.quemsi.commons.util.Exceptions;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimerImpl {
    @Setter
	private Scheduler scheduler;
	@Setter
	@Getter
	private String name;
	@Getter
	@Setter
	private String schedule;
	@Getter
	private boolean initialized;
	private TriggerKey triggerKey;
	
	@Getter
	private Map<String, NamedRunnable> runnables = new HashMap<>();
	public void add(NamedRunnable r) {
		runnables.put(r.getName(), r);
	}
	public void remove(String name){
		runnables.remove(name);
	}
	public void tick() {
		log.info("{} timer ticks", this.name);
		if(!runnables.isEmpty()) {
			runnables.values().forEach(Runnable::run);
		}
	}

	public void reset(){
		try {
			scheduler.unscheduleJob(triggerKey);
			init();
		} catch (SchedulerException e) {
			throw Exceptions.server("scheduler-error").withCause(e).get();
		}
	}
	
	public void init() {
		try {
			MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
			factory.setTargetObject(this);
			factory.setTargetMethod("tick");
			factory.afterPropertiesSet();
			String groupName = "timersgroup";
			triggerKey = TriggerKey.triggerKey(name , groupName);
        	JobDetail job = factory.getObject();
			Trigger trigger = TriggerBuilder.newTrigger()
				.withIdentity(name, groupName)
            	.withSchedule(
					CronScheduleBuilder.cronSchedule(this.schedule)
            			.withMisfireHandlingInstructionFireAndProceed()
            	)
            	.build();
            scheduler.scheduleJob(job, trigger);
            this.initialized = true;
            log.debug("{} timer scheduled", this.name);
        } catch (Throwable se) {
            log.error("error creating timer " + this.name, se);
        }
	}
	
	public static interface NamedRunnable extends Runnable {
		String getName();
	}
}
