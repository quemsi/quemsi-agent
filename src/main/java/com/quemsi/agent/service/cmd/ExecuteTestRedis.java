package com.quemsi.agent.service.cmd;

import org.springframework.beans.factory.annotation.Autowired;

import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.service.AgentBatchedLogger;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.LogMessage;
import com.quemsi.model.dto.ClearRedisConfig;
import com.quemsi.model.dto.agent.TestRedis;
import com.quemsi.model.dto.agent.onapi.TestRedisResult;
import com.quemsi.model.flow.redis.RedisConnectionSupport;
import com.quemsi.model.flow.redis.RedisConnectionSupport.RedisSession;
import com.quemsi.model.flow.redis.RedisConnectionSupport.ResolvedCredentials;

public class ExecuteTestRedis {
	@Autowired
	private ApiManager apiManager;
	@Autowired
	private AgentBatchedLogger agentBatchedLogger;

	public void execute(TestRedis cmd) {
		TestRedisResult.TestRedisResultBuilder builder = TestRedisResult.builder()
				.agentId(cmd.getAgentId())
				.correlationId(cmd.getCorrelationId())
				.timeoutMilis(cmd.getTimeoutMilis());

		try {
			ClearRedisConfig config = cmd.toConfig();
			RedisConnectionSupport.validateConnectionConfig(config);
			ResolvedCredentials credentials = RedisConnectionSupport.resolveCredentials(config);

			agentBatchedLogger.logInfo(null, null,
					LogMessage.info("Testing Redis connection mode={} host={} masterName={} database={}",
							config.getMode(), config.getHost(), config.getMasterName(), config.getDatabase()));

			try (RedisSession session = RedisConnectionSupport.open(config, credentials)) {
				String pong = session.jedis().ping();
				TestRedisResult result = builder
						.success(true)
						.message("connection-succeded")
						.discoveredMaster(session.discoveredMaster())
						.errorMessage(null)
						.build();
				agentBatchedLogger.logInfo(null, null,
						LogMessage.info("Redis connection test succeeded master={} ping={}",
								session.discoveredMaster(), pong));
				apiManager.send(result);
			}
		} catch (BaseRuntimeException ex) {
			agentBatchedLogger.logError(null, null, LogMessage.error("redis-connection-test-failed", ex));
			TestRedisResult result = builder
					.success(false)
					.errorCode(ex.getStatus() != null ? ex.getStatus().value() : 500)
					.message(ex.getMessageId() != null ? ex.getMessageId() : "redis-connection-test-failed")
					.errorMessage(ex.getMessage())
					.build();
			apiManager.send(result);
		} catch (Exception ex) {
			agentBatchedLogger.logError(null, null, LogMessage.error("redis-connection-test-failed", ex));
			TestRedisResult result = builder
					.success(false)
					.errorCode(500)
					.message("redis-connection-test-failed")
					.errorMessage(ex.getMessage() != null ? ex.getMessage() : "redis-connection-test-failed")
					.build();
			apiManager.send(result);
		}
	}
}
