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
import com.quemsi.model.flow.db.DMLService;
import com.quemsi.model.flow.db.sql.DbColumn;
import com.quemsi.model.flow.db.sql.DbModel;
import com.quemsi.model.flow.db.sql.DbSequence;
import com.quemsi.model.flow.db.sql.DbTable;
import com.quemsi.model.flow.db.sql.DbView;
import com.quemsi.model.flow.file.ZipBackupArchive;
import com.quemsi.model.flow.out.Storage;
import com.quemsi.model.flow.subset.SqlSubsetSupport;
import com.quemsi.model.flow.subset.SubsetBrowseResult;
import com.quemsi.model.flow.subset.SubsetConfig;
import com.quemsi.model.flow.subset.SubsetDriver;
import com.quemsi.model.flow.subset.SubsetPlan;
import com.quemsi.model.flow.subset.SubsetPlanner;
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

        String schemaSourceName = session.schemaSource() != null
                ? session.schemaSource().name()
                : BuilderSchemaSource.DATASOURCE.name();

        String html = loadTemplate("control-builder/index.html");
        html = html.replace("{{SESSION_ID}}", escapeHtml(session.sessionId()))
                .replace("{{BROWSER_TOKEN}}", escapeHtml(session.browserToken()))
                .replace("{{MODE}}", escapeHtml(session.mode() != null ? session.mode().name() : ""))
                .replace("{{SCHEMA_SOURCE}}", escapeHtml(schemaSourceName))
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
                && session.mode() != BuilderMode.UPDATE_SEQUENCES
                && session.mode() != BuilderMode.SUBSET
                && session.mode() != BuilderMode.UPSERT
                && session.mode() != BuilderMode.BROWSE) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        ensureModel(session);
        ActiveSession refreshed = sessionRegistry.require(sessionId, token);
        List<String> list = refreshed.cachedTables() != null ? refreshed.cachedTables() : List.of();
        return Map.of("tables", list);
    }

    @GetMapping("/api/objects")
    public Map<String, Object> objects(@RequestParam("sessionId") String sessionId,
            @RequestParam("token") String token) {
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.BROWSE) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        DbModel model = ensureModel(session);
        List<String> tables = new ArrayList<>();
        if (model.getTables() != null) {
            for (String name : model.getTables().keySet()) {
                if (name != null && !name.isBlank()) {
                    tables.add(name);
                }
            }
        }
        tables.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> views = new ArrayList<>();
        if (model.getViews() != null) {
            for (DbView view : model.getViews()) {
                if (view != null && view.getName() != null) {
                    views.add(view.qualifiedName());
                }
            }
        }
        views.sort(String.CASE_INSENSITIVE_ORDER);

        List<String> sequences = new ArrayList<>();
        if (model.getSequences() != null) {
            for (DbSequence seq : model.getSequences()) {
                if (seq != null && seq.getName() != null) {
                    sequences.add(seq.qualifiedName());
                }
            }
        }
        sequences.sort(String.CASE_INSENSITIVE_ORDER);

        boolean liveRows = session.schemaSource() != BuilderSchemaSource.DATA_VERSION
                && !StringUtils.isEmptyOrNull(session.datasourceName());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tables", tables);
        result.put("views", views);
        result.put("sequences", sequences);
        result.put("liveRows", liveRows);
        result.put("sourceType", model.getSourceType() != null ? model.getSourceType() : "");
        return result;
    }

    @GetMapping("/api/object-detail")
    public Map<String, Object> objectDetail(@RequestParam("sessionId") String sessionId,
            @RequestParam("token") String token,
            @RequestParam("kind") String kind,
            @RequestParam("name") String name) {
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.BROWSE) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        if (StringUtils.isEmptyOrNull(kind) || StringUtils.isEmptyOrNull(name)) {
            throw Exceptions.badRequest("builder-object-kind-name-required").get();
        }
        DbModel model = ensureModel(session);
        String kindNorm = kind.trim().toLowerCase();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("kind", kindNorm);
        result.put("name", name);

        if ("table".equals(kindNorm) || "view".equals(kindNorm)) {
            if ("table".equals(kindNorm)) {
                DbTable dbTable = model.findTable(name)
                        .orElseThrow(Exceptions.notFound("builder-table-not-found").withExtra("table", name).supplier());
                putTableDetail(result, model, dbTable);
            } else {
                DbView dbView = findView(model, name)
                        .orElseThrow(Exceptions.notFound("builder-view-not-found").withExtra("view", name).supplier());
                result.put("schema", dbView.getSchema() != null ? dbView.getSchema() : "");
                result.put("simpleName", dbView.getName() != null ? dbView.getName() : "");
                result.put("qualified", dbView.qualifiedName());
                result.put("definition", dbView.getDefinition() != null ? dbView.getDefinition() : "");
                result.put("dependsOnViews",
                        dbView.getDependsOnViews() != null ? List.copyOf(dbView.getDependsOnViews()) : List.of());
                model.findTable(dbView.qualifiedName()).ifPresent(t -> putTableDetail(result, model, t));
            }
            return result;
        }
        if ("sequence".equals(kindNorm)) {
            DbSequence seq = findSequence(model, name)
                    .orElseThrow(Exceptions.notFound("builder-sequence-not-found").withExtra("sequence", name).supplier());
            result.put("schema", seq.getSchema() != null ? seq.getSchema() : "");
            result.put("simpleName", seq.getName() != null ? seq.getName() : "");
            result.put("qualified", seq.qualifiedName());
            result.put("startValue", seq.getStartValue());
            result.put("minValue", seq.getMinValue());
            result.put("maxValue", seq.getMaxValue());
            result.put("incrementBy", seq.getIncrementBy());
            result.put("cycle", seq.isCycle());
            result.put("cacheSize", seq.getCacheSize());
            result.put("lastValue", seq.getLastValue());
            return result;
        }
        throw Exceptions.badRequest("builder-object-kind-unsupported").withExtra("kind", kind).get();
    }

    private static void putTableDetail(Map<String, Object> result, DbModel model, DbTable dbTable) {
        result.put("schema", dbTable.getSchema() != null ? dbTable.getSchema() : "");
        result.put("simpleName", dbTable.getName() != null ? dbTable.getName() : "");
        result.put("qualified", dbTable.qualifiedName());
        List<Map<String, Object>> columns = new ArrayList<>();
        for (DbColumn col : dbTable.orderedColumns()) {
            if (col == null || col.getName() == null) {
                continue;
            }
            Map<String, Object> c = new java.util.LinkedHashMap<>();
            c.put("name", col.getName());
            c.put("dataType", col.getDataType() != null ? col.getDataType() : "");
            c.put("columnType", col.getColumnType() != null ? col.getColumnType() : "");
            c.put("nullable", col.isNullable());
            c.put("columnKey", col.getColumnKey() != null ? col.getColumnKey() : "");
            c.put("identity", col.isIdentity());
            c.put("maxLength", col.getMaxLength());
            c.put("numPrecision", col.getNumPrecision());
            c.put("numScale", col.getNumScale());
            c.put("columnDefault", col.getColumnDefault());
            columns.add(c);
        }
        result.put("columns", columns);
        result.put("pkColumns",
                dbTable.getPkColumnNames() != null ? List.copyOf(dbTable.getPkColumnNames()) : List.of());

        List<Map<String, Object>> foreignKeys = new ArrayList<>();
        if (model.getReferenceInfos() != null) {
            String q = dbTable.qualifiedName();
            String bare = dbTable.getName();
            for (DbModel.ReferenceInfo ref : model.getReferenceInfos()) {
                if (ref == null) {
                    continue;
                }
                String srcQ = ref.srcQualifiedName();
                String refQ = ref.refQualifiedName();
                boolean involves = q.equals(srcQ) || q.equals(refQ)
                        || (bare != null && (bare.equals(ref.getSrcTableName()) || bare.equals(ref.getRefTableName())));
                if (!involves) {
                    continue;
                }
                Map<String, Object> fk = new java.util.LinkedHashMap<>();
                fk.put("constraintName", ref.getConstraintName() != null ? ref.getConstraintName() : "");
                fk.put("srcTable", srcQ != null ? srcQ : "");
                fk.put("srcColumns", ref.getSrcColumnNames() != null ? List.copyOf(ref.getSrcColumnNames()) : List.of());
                fk.put("refTable", refQ != null ? refQ : "");
                fk.put("refColumns", ref.getRefColumnNames() != null ? List.copyOf(ref.getRefColumnNames()) : List.of());
                foreignKeys.add(fk);
            }
        }
        result.put("foreignKeys", foreignKeys);

        List<Map<String, Object>> indexes = new ArrayList<>();
        Map<String, DbModel.IndexInfo> idxMap = model.indexesForTable(dbTable.qualifiedName());
        if (idxMap != null) {
            for (DbModel.IndexInfo idx : idxMap.values()) {
                if (idx == null || idx.getIndexName() == null) {
                    continue;
                }
                Map<String, Object> i = new java.util.LinkedHashMap<>();
                i.put("name", idx.getIndexName());
                i.put("unique", idx.isUnique());
                i.put("indexType", idx.getIndexType() != null ? idx.getIndexType() : "");
                i.put("columns", idx.getColumns() != null ? List.copyOf(idx.getColumns()) : List.of());
                indexes.add(i);
            }
        }
        indexes.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        result.put("indexes", indexes);
    }

    private static java.util.Optional<DbView> findView(DbModel model, String name) {
        if (model.getViews() == null || name == null) {
            return java.util.Optional.empty();
        }
        for (DbView view : model.getViews()) {
            if (view == null) {
                continue;
            }
            if (name.equals(view.qualifiedName()) || name.equals(view.getName())) {
                return java.util.Optional.of(view);
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<DbSequence> findSequence(DbModel model, String name) {
        if (model.getSequences() == null || name == null) {
            return java.util.Optional.empty();
        }
        for (DbSequence seq : model.getSequences()) {
            if (seq == null) {
                continue;
            }
            if (name.equals(seq.qualifiedName()) || name.equals(seq.getName())) {
                return java.util.Optional.of(seq);
            }
        }
        return java.util.Optional.empty();
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
        if (session.mode() != BuilderMode.UPDATE_SEQUENCES && session.mode() != BuilderMode.BROWSE) {
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

    @PostMapping("/api/browse-rows")
    public Map<String, Object> browseRows(@RequestBody Map<String, Object> body) {
        String sessionId = asString(body.get("sessionId"));
        String token = asString(body.get("token"));
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.SUBSET && session.mode() != BuilderMode.BROWSE) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        if (session.mode() == BuilderMode.BROWSE
                && (session.schemaSource() == BuilderSchemaSource.DATA_VERSION
                        || StringUtils.isEmptyOrNull(session.datasourceName()))) {
            throw Exceptions.badRequest("builder-browse-rows-require-live-datasource").get();
        }
        String table = asString(body.get("table"));
        if (StringUtils.isEmptyOrNull(table)) {
            throw Exceptions.badRequest("builder-table-required").get();
        }
        boolean entireTable = Boolean.TRUE.equals(body.get("entireTable"));
        String where = entireTable ? null : asString(body.get("where"));
        Integer pageSize = null;
        Object pageSizeObj = body.get("pageSize");
        if (pageSizeObj instanceof Number n) {
            pageSize = n.intValue();
        } else if (pageSizeObj != null && !String.valueOf(pageSizeObj).isBlank()) {
            try {
                pageSize = Integer.parseInt(String.valueOf(pageSizeObj));
            } catch (NumberFormatException e) {
                throw Exceptions.badRequest("builder-browse-page-size-invalid").get();
            }
        }
        Integer page = 0;
        Object pageObj = body.get("page");
        if (pageObj instanceof Number n) {
            page = n.intValue();
        } else if (pageObj != null && !String.valueOf(pageObj).isBlank()) {
            try {
                page = Integer.parseInt(String.valueOf(pageObj));
            } catch (NumberFormatException e) {
                throw Exceptions.badRequest("builder-browse-page-invalid").get();
            }
        }
        DbModel model = ensureModel(session);
        DbTable dbTable = resolveBrowseRelation(model, table, session.mode());
        DataSourceFactory ds = resolveDatasource(session.datasourceName());
        try (DMLService dml = ds.dmlService()) {
            if (session.mode() == BuilderMode.SUBSET && !dml.supportsSubset()) {
                throw Exceptions.badRequest("subset-not-supported-for-datasource").get();
            }
            SubsetBrowseResult browse = dml.browseRows(dbTable, where, pageSize, page);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (browse.getRows() != null) {
                for (SubsetBrowseResult.BrowseRow row : browse.getRows()) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("pkKey", row.getPkKey());
                    m.put("values", row.getValues() != null ? row.getValues() : List.of());
                    rows.add(m);
                }
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("table", dbTable.qualifiedName());
            result.put("columns", browse.getColumns() != null ? browse.getColumns() : List.of());
            result.put("pkColumns", dbTable.getPkColumnNames() != null ? List.copyOf(dbTable.getPkColumnNames()) : List.of());
            result.put("rows", rows);
            result.put("totalCount", browse.getTotalCount());
            result.put("page", browse.getPage());
            result.put("pageSize", browse.getPageSize());
            return result;
        } catch (BaseRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw Exceptions.server("builder-browse-rows-failed").withCause(e).get();
        }
    }

    @PostMapping("/api/pk-predicate")
    public Map<String, Object> pkPredicate(@RequestBody Map<String, Object> body) {
        String sessionId = asString(body.get("sessionId"));
        String token = asString(body.get("token"));
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.SUBSET) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        String table = asString(body.get("table"));
        if (StringUtils.isEmptyOrNull(table)) {
            throw Exceptions.badRequest("builder-table-required").get();
        }
        Object keysObj = body.get("keys");
        if (!(keysObj instanceof Iterable<?> iterable)) {
            throw Exceptions.badRequest("subset-pk-selection-required").get();
        }
        List<String> keys = new ArrayList<>();
        for (Object k : iterable) {
            if (k != null && !String.valueOf(k).isBlank()) {
                keys.add(String.valueOf(k));
            }
        }
        DbModel model = ensureModel(session);
        DbTable dbTable = resolveTable(model, table);
        String where = SqlSubsetSupport.buildPkInPredicate(dbTable, keys);
        return Map.of("table", dbTable.qualifiedName(), "where", where);
    }

    @PostMapping("/api/preview-subset")
    public Map<String, Object> previewSubset(@RequestBody Map<String, Object> body) {
        String sessionId = asString(body.get("sessionId"));
        String token = asString(body.get("token"));
        ActiveSession session = sessionRegistry.require(sessionId, token);
        if (session.mode() != BuilderMode.SUBSET) {
            throw Exceptions.badRequest("builder-mode-unsupported").withExtra("mode", session.mode()).get();
        }
        SubsetConfig config = parseSubsetDrivers(body.get("drivers"));
        if (!config.isActive()) {
            return Map.of("success", true, "tables", List.of());
        }
        DbModel model = ensureModel(session);
        DataSourceFactory ds = resolveDatasource(session.datasourceName());
        try (DMLService dml = ds.dmlService()) {
            if (!dml.supportsSubset()) {
                throw Exceptions.badRequest("subset-not-supported-for-datasource").get();
            }
            SubsetPlan plan = new SubsetPlanner().plan(model, dml, config);
            List<Map<String, Object>> tables = new ArrayList<>();
            for (var summary : plan.summaries()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("table", summary.getTable());
                row.put("count", summary.getCount());
                row.put("driverCount", summary.getDriverCount());
                row.put("requiredByFkCount", summary.getRequiredByFkCount());
                row.put("requiredBy", summary.getRequiredBy() != null ? summary.getRequiredBy() : List.of());
                tables.add(row);
            }
            return Map.of("success", true, "tables", tables);
        } catch (BaseRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw Exceptions.server("builder-preview-subset-failed").withCause(e).get();
        }
    }

    private static SubsetConfig parseSubsetDrivers(Object driversObj) {
        List<SubsetDriver> drivers = new ArrayList<>();
        if (driversObj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                String table = m.get("table") != null ? String.valueOf(m.get("table")) : null;
                if (StringUtils.isEmptyOrNull(table)) {
                    continue;
                }
                boolean entireTable = Boolean.TRUE.equals(m.get("entireTable"));
                String where = m.get("where") != null ? String.valueOf(m.get("where")) : null;
                Integer limit = null;
                Object limitObj = m.get("limit");
                if (limitObj instanceof Number n) {
                    limit = n.intValue();
                }
                drivers.add(SubsetDriver.builder()
                        .table(table)
                        .where(where)
                        .limit(limit)
                        .entireTable(entireTable)
                        .build());
            }
        }
        return SubsetConfig.builder().enabled(!drivers.isEmpty()).drivers(drivers).build();
    }

    private static DbTable resolveTable(DbModel model, String tableName) {
        return SubsetPlanner.resolveTable(model, tableName);
    }

    /**
     * For BROWSE mode, allow sampling views that are not present as {@link DbTable} entries
     * by synthesizing a relation shell (no PK / columns) for {@code SELECT *}.
     */
    private static DbTable resolveBrowseRelation(DbModel model, String name, BuilderMode mode) {
        try {
            return resolveTable(model, name);
        } catch (BaseRuntimeException e) {
            if (mode != BuilderMode.BROWSE || !"subset-table-not-found".equals(e.getMessageId())) {
                throw e;
            }
            return findView(model, name)
                    .map(view -> new DbTable(view.getSchema(), view.getName()))
                    .orElseThrow(() -> e);
        }
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
        if (session.mode() == BuilderMode.BROWSE) {
            throw Exceptions.badRequest("builder-browse-read-only").get();
        }
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
