package com.quemsi.agent.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.config.AgentProperties;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.dto.AgentLogRecord;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentBatchedLogger {
    
    private final ApiManager apiManager;
    private final AgentProperties agentProperties;
    private final int batchSize;
    private final long flushIntervalSeconds;
    private BlockingQueue<AgentLogRecord> logQueue;
    private AtomicBoolean running;
    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> scheduledFuture;

    public AgentBatchedLogger(ApiManager apiManager, AgentProperties agentProperties, ScheduledExecutorService scheduledExecutorService,
                              @Value("${quemsi.logging.batch-size:10}") int batchSize,
                              @Value("${quemsi.logging.flush-interval-seconds:5}") long flushIntervalSeconds) {
        this.apiManager = apiManager;
        this.agentProperties = agentProperties;
        this.batchSize = batchSize;
        this.flushIntervalSeconds = flushIntervalSeconds;
        this.scheduledExecutorService = scheduledExecutorService;
    }
    
    @PostConstruct
    public void init() {
        // Initialize in @PostConstruct to ensure it runs on the actual bean instance, not proxy
        logQueue = new LinkedBlockingQueue<>();
        running = new AtomicBoolean(true);
        scheduledFuture = scheduledExecutorService.schedule(() -> flushAndReset(), flushIntervalSeconds, TimeUnit.SECONDS);
    }
    
    @PreDestroy
    public void shutdown() {
        running.set(false);
        flushLogs(); // Flush remaining logs on shutdown
    }
    
    public void log(Long flowExecutionId, Long flowExecutionStepId, String level, String message) {
        Long agentId = agentProperties != null ? agentProperties.getAgentId() : null;
        AgentLogRecord logRecord = AgentLogRecord.builder()
            .agentId(agentId)
            .flowExecutionId(flowExecutionId)
            .flowExecutionStepId(flowExecutionStepId)
            .level(level)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Log to SLF4J for local debugging
        String logMessage = message;
        switch (level) {
            case "INFO":
                log.info(logMessage);
                break;
            case "WARN":
                log.warn(logMessage);
                break;
            case "ERROR":
                log.error(logMessage);
                break;
            case "DEBUG":
                log.debug(logMessage);
                break;
            default:
                log.info(logMessage);
        }
        if(logQueue != null){
            logQueue.offer(logRecord);
            
            // Check if we should flush immediately
            if (logQueue.size() >= batchSize) {
                resetScheduled();
                flushLogs();
            }
        }
    }

    public void resetScheduled(){
        if(scheduledFuture != null){
            scheduledFuture.cancel(false);
        }
        scheduledFuture = scheduledExecutorService.schedule(() -> flushAndReset(), flushIntervalSeconds, TimeUnit.SECONDS);
    }

    public void flushAndReset(){
        if(logQueue != null && !logQueue.isEmpty()){
            flushLogs();
        }
        if(scheduledFuture != null){
            scheduledFuture.cancel(false);
        }
        scheduledFuture = scheduledExecutorService.schedule(() -> flushAndReset(), flushIntervalSeconds, TimeUnit.SECONDS);
    }
    
    public void logInfo(Long flowExecutionId, Long flowExecutionStepId, LogMessage message) {
        log(flowExecutionId, flowExecutionStepId, message.getLevel(), message.toString());
    }
    
    public void logWarn(Long flowExecutionId, Long flowExecutionStepId, LogMessage message) {
        log(flowExecutionId, flowExecutionStepId, message.getLevel(), message.toString());
    }
    
    public void logError(Long flowExecutionId, Long flowExecutionStepId, LogMessage message) {
        log(flowExecutionId, flowExecutionStepId, message.getLevel(), message.toString());
    }
    
    public void logDebug(Long flowExecutionId, Long flowExecutionStepId, LogMessage message) {
        log(flowExecutionId, flowExecutionStepId, message.getLevel(), message.toString());
    }
    
    // Method to match FlowContext.LogWriter interface (agentId is ignored, obtained from AgentProperties)
    public void logWithAgentId(Long agentId, Long flowExecutionId, Long flowExecutionStepId, String level, String message) {
        log(flowExecutionId, flowExecutionStepId, level, message);
    }
    
    private synchronized void flushLogs() {
        if (!running.get() && logQueue.isEmpty()) {
            return;
        }
        
        List<AgentLogRecord> logsToSend = new ArrayList<>();
        logQueue.drainTo(logsToSend, batchSize);
        
        if (logsToSend.isEmpty()) {
            return;
        }
        
        try {
            apiManager.getQuemsiApi().sendLogs(apiManager.authHeader(), logsToSend);
        } catch (Exception e) {
            // Re-queue logs on failure (up to a limit to prevent memory issues)
            if (logQueue.size() < batchSize * 10) {
                logQueue.addAll(logsToSend);
            }
        }
    }
}

