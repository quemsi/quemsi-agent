package com.quemsi.agent.control;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.quemsi.commons.util.Exceptions;
import com.quemsi.model.dto.builder.BuilderMode;
import com.quemsi.model.dto.builder.BuilderSchemaSource;
import com.quemsi.model.dto.builder.BuilderSessionOpenPayload;
import com.quemsi.model.flow.DataPackage;
import com.quemsi.model.flow.db.sql.DbModel;
import com.quemsi.model.flow.file.ZipBackupArchive;

@Component
public class BuilderSessionRegistry {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record ActiveSession(
            String sessionId,
            BuilderMode mode,
            BuilderSchemaSource schemaSource,
            String datasourceName,
            String storageName,
            String dir,
            String fileName,
            Long versionId,
            String contentType,
            Long size,
            Map<String, Object> draftConfig,
            String returnUrl,
            String browserToken,
            Instant expiresAt,
            List<String> cachedTables,
            DbModel cachedModel,
            ZipBackupArchive archive,
            DataPackage dataPackage) {
        public ActiveSession withCachedTables(List<String> tables, DbModel model) {
            return new ActiveSession(sessionId, mode, schemaSource, datasourceName, storageName, dir, fileName,
                    versionId, contentType, size, draftConfig, returnUrl, browserToken, expiresAt, tables, model,
                    archive, dataPackage);
        }

        public ActiveSession withArchive(ZipBackupArchive archive, DataPackage dataPackage, List<String> tables,
                DbModel model) {
            return new ActiveSession(sessionId, mode, schemaSource, datasourceName, storageName, dir, fileName,
                    versionId, contentType, size, draftConfig, returnUrl, browserToken, expiresAt, tables, model,
                    archive, dataPackage);
        }
    }

    private final ConcurrentHashMap<String, ActiveSession> sessions = new ConcurrentHashMap<>();

    public ActiveSession putFromOpen(BuilderSessionOpenPayload payload, Instant expiresAt) {
        String browserToken = newToken();
        BuilderSchemaSource schemaSource = payload.getSchemaSource() != null
                ? payload.getSchemaSource()
                : BuilderSchemaSource.DATASOURCE;
        ActiveSession session = new ActiveSession(
                payload.getSessionId(),
                payload.getMode(),
                schemaSource,
                payload.getDatasourceName(),
                payload.getStorageName(),
                payload.getDir(),
                payload.getFileName(),
                payload.getVersionId(),
                payload.getContentType(),
                payload.getSize(),
                payload.getDraftConfig(),
                payload.getReturnUrl(),
                browserToken,
                expiresAt,
                null,
                null,
                null,
                null);
        sessions.put(payload.getSessionId(), session);
        return session;
    }

    public ActiveSession require(String sessionId, String browserToken) {
        ActiveSession session = sessions.get(sessionId);
        if (session == null) {
            throw Exceptions.notFound("builder-session-not-local").withExtra("sessionId", sessionId).get();
        }
        if (session.expiresAt() != null && Instant.now().isAfter(session.expiresAt())) {
            remove(sessionId);
            throw Exceptions.create(org.springframework.http.HttpStatus.GONE, "builder-session-expired").get();
        }
        if (browserToken == null || !browserToken.equals(session.browserToken())) {
            throw Exceptions.forbidden("builder-session-bad-token").get();
        }
        return session;
    }

    public void updateCache(String sessionId, List<String> tables, DbModel model) {
        sessions.computeIfPresent(sessionId, (id, existing) -> existing.withCachedTables(tables, model));
    }

    public void updateArchiveCache(String sessionId, ZipBackupArchive archive, DataPackage dataPackage,
            List<String> tables, DbModel model) {
        sessions.computeIfPresent(sessionId,
                (id, existing) -> existing.withArchive(archive, dataPackage, tables, model));
    }

    public void remove(String sessionId) {
        ActiveSession removed = sessions.remove(sessionId);
        if (removed == null) {
            return;
        }
        if (removed.archive() != null) {
            try {
                removed.archive().close();
            } catch (Exception ignored) {
                /* best-effort */
            }
        }
        if (removed.dataPackage() != null) {
            try {
                removed.dataPackage().clear();
            } catch (Exception ignored) {
                /* best-effort */
            }
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
