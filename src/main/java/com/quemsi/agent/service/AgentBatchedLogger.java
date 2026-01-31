package com.quemsi.agent.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.model.dto.AgentLogRecord;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentBatchedLogger {
    
    @Autowired
    private ApiManager apiManager;
    
    @Value("${quemsi.logging.batch-size:50}")
    private int batchSize;
    
    @Value("${quemsi.logging.flush-interval-seconds:5}")
    private long flushIntervalSeconds;
    
    private final BlockingQueue<AgentLogRecord> logQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @PostConstruct
    public void init() {
        running.set(true);
        log.info("AgentBatchedLogger initialized with batchSize={}, flushIntervalSeconds={}", batchSize, flushIntervalSeconds);
    }
    
    @Scheduled(fixedDelayString = "${quemsi.logging.flush-interval-seconds:5}", timeUnit = TimeUnit.SECONDS)
    public void scheduledFlush() {
        flushLogs();
    }
    
    @PreDestroy
    public void shutdown() {
        running.set(false);
        flushLogs(); // Flush remaining logs on shutdown
        log.info("AgentBatchedLogger shutdown completed");
    }
    
    public void log(Long agentId, Long flowExecutionId, Long flowExecutionStepId, String level, String message) {
        log.info("Logging message: {} {} {} {} {}", agentId, flowExecutionId, flowExecutionStepId, level, message);
        AgentLogRecord logRecord = AgentLogRecord.builder()
            .agentId(agentId)
            .flowExecutionId(flowExecutionId)
            .flowExecutionStepId(flowExecutionStepId)
            .level(level)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
        
        logQueue.offer(logRecord);
        
        // Check if we should flush immediately
        if (logQueue.size() >= batchSize) {
            flushLogs();
        }
    }
    
    public void logInfo(Long agentId, Long flowExecutionId, Long flowExecutionStepId, String message) {
        log(agentId, flowExecutionId, flowExecutionStepId, "INFO", message);
    }
    
    public void logWarn(Long agentId, Long flowExecutionId, Long flowExecutionStepId, String message) {
        log(agentId, flowExecutionId, flowExecutionStepId, "WARN", message);
    }
    
    public void logError(Long agentId, Long flowExecutionId, Long flowExecutionStepId, String message) {
        log(agentId, flowExecutionId, flowExecutionStepId, "ERROR", message);
    }
    
    public void logDebug(Long agentId, Long flowExecutionId, Long flowExecutionStepId, String message) {
        log(agentId, flowExecutionId, flowExecutionStepId, "DEBUG", message);
    }
    
    private void flushLogs() {
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
            log.debug("Successfully sent {} logs to API", logsToSend.size());
        } catch (Exception e) {
            log.error("Failed to send logs to API, re-queuing {} logs", logsToSend.size(), e);
            // Re-queue logs on failure (up to a limit to prevent memory issues)
            if (logQueue.size() < batchSize * 10) {
                logQueue.addAll(logsToSend);
            } else {
                log.warn("Log queue is too large, dropping {} logs", logsToSend.size());
            }
        }
    }
}

