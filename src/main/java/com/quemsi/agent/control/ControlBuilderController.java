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
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.commons.util.BaseRuntimeException;
import com.quemsi.commons.util.Exceptions;
import com.quemsi.commons.util.StringUtils;
import com.quemsi.model.dto.DataFile;
import com.quemsi.model.dto.builder.BuilderMode;
import com.quemsi.model.dto.builder.BuilderSchemaSource;
import com.quemsi.model.dto.builder.BuilderSessionOpenPayload;
import com.quemsi.model.dto.builder.BuilderSessionSubmitRequest;
import com.quemsi.model.flow.DataPackage;
import com.quemsi.model.flow.DataPackageFile;
import com.quemsi.model.flow.Flow;
import com.quemsi.model.flow.FlowContext;
import com.quemsi.model.flow.db.DataSourceFactory;
import com.quemsi.model.flow.db.sql.DbColumn;
import com.quemsi.model.flow.db.sql.DbModel;
import com.quemsi.model.flow.db.sql.DbSequence;
import com.quemsi.model.flow.db.sql.DbTable;
import com.quemsi.model.flow.file.ZipBackupArchive;
import com.quemsi.model.flow.out.Storage;
import com.quemsi.model.util.CommonConstants;
import com.quemsi.model.util.QuemsiTemp;

import java.io.File;
import java.nio.file.Path;

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
    @Autowired
    private SpringBeanManager beanManager;

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

        String schemaLabel = session.datasourceName() != null ? session.datasourceName() : "";
        if (session.schemaSource() == BuilderSchemaSource.DATA_VERSION) {
            String file = session.fileName() != null ? session.fileName() : "archive";
            String storage = session.storageName() != null ? session.storageName() : "";
            schemaLabel = storage.isBlank() ? file : storage + " / " + file;
        }

        String html = loadTemplate("control-builder/index.html");
        html = html.replace("{{SESSION_ID}}", escapeHtml(session.sessionId()))
                .replace("{{BROWSER_TOKEN}}", escapeHtml(session.browserToken()))
                .replace("{{MODE}}", escapeHtml(session.mode() != null ? session.mode().name() : ""))
                .replace("{{DATASOURCE}}", escapeHtml(schemaLabel))
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
        if (session.schemaSource() == BuilderSchemaSource.DATA_VERSION) {
            return ensureModelFromArchive(session);
        }
        DataSourceFactory ds = resolveDatasource(session.datasourceName());
        DbModel model = ds.getDbModel();
        LinkedList<String> tables = model.orderedTableNames();
        List<String> list = tables != null ? List.copyOf(tables) : List.of();
        sessionRegistry.updateCache(session.sessionId(), list, model);
        ActiveSession refreshed = sessionRegistry.require(session.sessionId(), session.browserToken());
        return refreshed.cachedModel() != null ? refreshed.cachedModel() : model;
    }

    private DbModel ensureModelFromArchive(ActiveSession session) {
        if (StringUtils.isEmptyOrNull(session.storageName()) || StringUtils.isEmptyOrNull(session.fileName())) {
            throw Exceptions.badRequest("builder-archive-refs-required").get();
        }
        Storage storage;
        try {
            storage = beanManager.findStorage(session.storageName());
        } catch (NoSuchBeanDefinitionException e) {
            throw Exceptions.notFound("storage-not-registered")
                    .withExtra("storageName", session.storageName())
                    .withCause(e)
                    .get();
        }
        Flow flow = new Flow();
        flow.setId(-1L);
        flow.setName("control-builder");
        if (!storage.isReady()) {
            storage.init(flow);
        }
        DataFile dataFile = new DataFile();
        dataFile.setDir(session.dir());
        dataFile.setName(session.fileName());
        dataFile.setVersion(session.versionId());
        dataFile.setContentType(session.contentType());
        dataFile.setSize(session.size());

        FlowContext context = new FlowContext(flow, null);
        List<DataPackage> packages;
        try {
            packages = storage.getFiles(context, List.of(dataFile));
        } catch (IOException e) {
            throw Exceptions.server("builder-unable-to-load-archive").withCause(e).get();
        }
        if (packages == null || packages.isEmpty()) {
            throw Exceptions.notFound("file-not-found")
                    .withExtra("fileName", session.fileName())
                    .withExtra("dir", session.dir())
                    .get();
        }
        DataPackage zipPackage = packages.get(0);
        File zipFile = resolveZipFile(zipPackage);
        boolean deleteOnClose = zipPackage instanceof DataPackageFile dpf && dpf.isDeleteOnClear();
        if (zipPackage instanceof DataPackageFile dpf && dpf.getFile() != null && dpf.getFile().equals(zipFile)) {
            dpf.setDeleteOnClear(false);
        }
        try {
            ZipBackupArchive archive = new ZipBackupArchive(zipFile, deleteOnClose);
            if (!archive.exists(CommonConstants.DB_MODEL_FILE_NAME)) {
                archive.close();
                zipPackage.clear();
                throw Exceptions.notFound("unable-to-find-db-model")
                        .withExtra("entry", CommonConstants.DB_MODEL_FILE_NAME)
                        .get();
            }
            DbModel model;
            try (InputStream in = archive.open(CommonConstants.DB_MODEL_FILE_NAME)) {
                model = objectMapper.readValue(in, DbModel.class);
            }
            if (model != null) {
                model.build();
            }
            LinkedList<String> tables = model != null ? model.orderedTableNames() : null;
            List<String> list = tables != null ? List.copyOf(tables) : List.of();
            sessionRegistry.updateArchiveCache(session.sessionId(), archive, zipPackage, list, model);
            ActiveSession refreshed = sessionRegistry.require(session.sessionId(), session.browserToken());
            return refreshed.cachedModel() != null ? refreshed.cachedModel() : model;
        } catch (BaseRuntimeException e) {
            zipPackage.clear();
            throw e;
        } catch (Exception e) {
            zipPackage.clear();
            throw Exceptions.server("builder-unable-to-parse-db-model").withCause(e).get();
        }
    }

    private static File resolveZipFile(DataPackage zipPackage) {
        File asFile = zipPackage.asFile();
        if (asFile != null && asFile.isFile()) {
            return asFile;
        }
        try (InputStream in = zipPackage.getInputStream()) {
            Path temp = QuemsiTemp.spoolToTempFile(in, "quemsi-builder-", ".zip");
            return temp.toFile();
        } catch (Exception e) {
            throw Exceptions.server("unable-to-materialize-zip").withCause(e).get();
        }
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
        if (StringUtils.isEmptyOrNull(name)) {
            throw Exceptions.badRequest("builder-datasource-required").get();
        }
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
