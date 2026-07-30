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
			String messageId = ex.getMessageId() != null ? ex.getMessageId() : "redis-connection-test-failed";
			TestRedisResult result = builder
					.success(false)
					.errorCode(ex.getStatus() != null ? ex.getStatus().value() : 500)
					.message(messageId)
					.errorMessage(describe(messageId, ex))
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

	private String describe(String messageId, BaseRuntimeException ex) {
		switch (messageId) {
			case "redis-target-is-replica":
				return "This Redis instance is a replica. Clearing a replica cannot synchronize the cache, "
						+ "point the connection at the master or use Sentinel mode.";
			case "redis-sentinels-unreachable":
				return "None of the configured sentinels could be reached.";
			case "redis-discovered-master-unreachable":
				return "Sentinels answered, but the master address they reported is not reachable from this agent.";
			case "redis-auth-failed":
				return "Redis rejected the supplied credentials.";
			default:
				return ex.getMessage();
		}
	}
}
