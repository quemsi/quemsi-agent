package com.quemsi.agent.control;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quemsi.agent.api.ApiManager;
import com.quemsi.agent.control.BuilderSessionRegistry.ActiveSession;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.builder.BuilderMode;
import com.quemsi.model.dto.builder.BuilderSessionOpenPayload;
import com.quemsi.model.dto.builder.BuilderSessionSubmitRequest;
import com.quemsi.model.flow.db.DataSourceFactory;
import com.quemsi.model.flow.db.sql.DbColumn;
import com.quemsi.model.flow.db.sql.DbModel;
import com.quemsi.model.flow.db.sql.DbSequence;
import com.quemsi.model.flow.db.sql.DbTable;

@RestController
@RequestMapping("/control/builder")
public class ControlBuilderController {

    @Autowired
    private ApiManager apiManager;
    @Autowired
    private BuilderSessionRegistry sessionRegistry;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @GetMapping
    public ResponseEntity<String> open(@RequestParam("ticket") String ticket) throws IOException {
        if (StringUtils.isEmptyOrNull(ticket)) {
            throw Exceptions.badRequest("builder-ticket-required").get();
        }
        BuilderSessionOpenPayload payload;
        try {
            payload = apiManager.openBuilderSession(ticket);
        } catch (WebClientResponseException e) {
            throw Exceptions.create(org.springframework.http.HttpStatus.valueOf(e.getStatusCode().value()),
                    "builder-open-failed").withCause(e).get();
        } catch (WebClientRequestException e) {
            throw Exceptions.server("unable-to-reach-api").withCause(e).get();
        }

        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        ActiveSession session = sessionRegistry.putFromOpen(payload, expiresAt);

        String html = loadTemplate("control-builder/index.html");
        html = html.replace("{{SESSION_ID}}", escapeHtml(session.sessionId()))
                .replace("{{BROWSER_TOKEN}}", escapeHtml(session.browserToken()))
                .replace("{{MODE}}", escapeHtml(session.mode() != null ? session.mode().name() : ""))
                .replace("{{DATASOURCE}}", escapeHtml(session.datasourceName() != null ? session.datasourceName() : ""))
                .replace("{{DRAFT_JSON}}", escapeJsString(objectMapper.writeValueAsString(
                        session.draftConfig() != null ? session.draftConfig() : Map.of())));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/api/tables")
    public Map<String, Object> tables(@RequestParam("sessionId") String sessionId,
            @RequestParam("token") String token) {
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.CLEAR_TABLES
                && session.mode() != BuilderMode.DROP_TABLES
                && session.mode() != BuilderMode.MASK_COLUMNS
                && session.mode() != BuilderMode.UPDATE_SEQUENCES) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        ensureModel(session);
        ActiveSession refreshed = sessionRegistry.require(sessionId, token);
        List<String> list = refreshed.cachedTables() != null ? refreshed.cachedTables() : List.of();
        return Map.of("tables", list);
    }

    @GetMapping("/api/columns")
    public Map<String, Object> columns(@RequestParam("sessionId") String sessionId,
            @RequestParam("token") String token,
            @RequestParam("table") String table) {
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.MASK_COLUMNS
                && session.mode() != BuilderMode.UPDATE_SEQUENCES) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        if (StringUtils.isEmptyOrNull(table)) {
            throw Exceptions.badRequest("builder-table-required").get();
        }
        DbModel model = ensureModel(session);
        DbTable dbTable = model.findTable(table)
                .orElseThrow(Exceptions.notFound("builder-table-not-found").withExtra("table", table).supplier());
        List<String> columns = new ArrayList<>();
        for (DbColumn col : dbTable.orderedColumns()) {
            if (col != null && col.getName() != null) {
                columns.add(col.getName());
            }
        }
        return Map.of(
                "table", dbTable.qualifiedName(),
                "schema", dbTable.getSchema() != null ? dbTable.getSchema() : "",
                "name", dbTable.getName() != null ? dbTable.getName() : "",
                "columns", columns);
    }

    @GetMapping("/api/sequences")
    public Map<String, Object> sequences(@RequestParam("sessionId") String sessionId,
            @RequestParam("token") String token) {
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.UPDATE_SEQUENCES) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        DbModel model = ensureModel(session);
        List<Map<String, String>> sequences = new ArrayList<>();
        if (model.getSequences() != null) {
            for (DbSequence seq : model.getSequences()) {
                if (seq == null || seq.getName() == null) {
                    continue;
                }
                sequences.add(Map.of(
                        "qualified", seq.qualifiedName(),
                        "schema", seq.getSchema() != null ? seq.getSchema() : "",
                        "name", seq.getName()));
            }
        }
        return Map.of("sequences", sequences);
    }

    private DbModel ensureModel(ActiveSession session) {
        if (session.cachedModel() != null) {
            return session.cachedModel();
        }
        DataSourceFactory ds = resolveDatasource(session.datasourceName());
        DbModel model = ds.getDbModel();
        LinkedList<String> tables = model.orderedTableNames();
        List<String> list = tables != null ? List.copyOf(tables) : List.of();
        sessionRegistry.updateCache(session.sessionId(), list, model);
        ActiveSession refreshed = sessionRegistry.require(session.sessionId(), session.browserToken());
        return refreshed.cachedModel() != null ? refreshed.cachedModel() : model;
    }

    @PostMapping("/api/apply")
    public Map<String, String> apply(@RequestBody Map<String, Object> body) {
        String sessionId = asString(body.get("sessionId"));
        String token = asString(body.get("token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> config = body.get("config") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : null;
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (config == null) {
            throw Exceptions.badRequest("builder-result-required").get();
        }
        try {
            apiManager.submitBuilderSessionResult(sessionId,
                    BuilderSessionSubmitRequest.builder().resultConfig(config).build());
        } catch (WebClientResponseException e) {
            throw Exceptions.create(org.springframework.http.HttpStatus.valueOf(e.getStatusCode().value()),
                    "builder-submit-failed").withCause(e).get();
        } catch (WebClientRequestException e) {
            throw Exceptions.server("unable-to-reach-api").withCause(e).get();
        } catch (BaseRuntimeException e) {
            throw e;
        }

        sessionRegistry.remove(sessionId);
        String redirect = appendQuery(session.returnUrl(), "builderSession", sessionId);
        return Map.of("redirectUrl", redirect != null ? redirect : "/");
    }

    @PostMapping("/api/cancel")
    public Map<String, String> cancel(@RequestBody Map<String, Object> body) {
        String sessionId = asString(body.get("sessionId"));
        String token = asString(body.get("token"));
        ActiveSession session = sessionRegistry.require(sessionId, token);
        sessionRegistry.remove(sessionId);
        String redirect = session.returnUrl();
        return Map.of("redirectUrl", redirect != null && !redirect.isBlank() ? redirect : "/");
    }

    @GetMapping(value = "/static/{file}", produces = { "text/css", "application/javascript", "text/javascript" })
    public ResponseEntity<byte[]> staticAsset(@org.springframework.web.bind.annotation.PathVariable("file") String file)
            throws IOException {
        if (!file.equals("builder.css") && !file.equals("builder.js")) {
            throw Exceptions.notFound("builder-static-not-found").get();
        }
        ClassPathResource resource = new ClassPathResource("control-builder/" + file);
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        }
        MediaType type = file.endsWith(".css") ? MediaType.valueOf("text/css") : MediaType.valueOf("application/javascript");
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    private DataSourceFactory resolveDatasource(String name) {
        try {
            return applicationContext.getBean(name, DataSourceFactory.class);
        } catch (NoSuchBeanDefinitionException e) {
            throw Exceptions.notFound("datasource-not-registered")
                    .withExtra("datasourceName", name)
                    .withCause(e)
                    .get();
        }
    }

    private static String loadTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String appendQuery(String returnUrl, String key, String value) {
        if (StringUtils.isEmptyOrNull(returnUrl)) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(returnUrl)
                .replaceQueryParam(key, value)
                .build(true)
                .toUriString();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJsString(String json) {
        if (json == null) {
            return "{}";
        }
        /* Embed JSON as JS string literal content inside <script type="application/json"> is safer —
           we inject into a script JSON.parse('...') so escape quotes and backslashes. */
        return json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }
}
