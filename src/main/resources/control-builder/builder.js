(function () {
  const cfg = window.__BUILDER__ || {};
  const mode = cfg.mode || "CLEAR_TABLES";
  const isDrop = mode === "DROP_TABLES";
  const isMask = mode === "MASK_COLUMNS";
  const isSeq = mode === "UP" + "DATE_SEQUENCES";
  const isSubset = mode === "SUBSET";

  const els = {
    title: document.getElementById("title"),
    allLabel: document.getElementById("allLabel"),
    allHint: document.getElementById("allHint"),
    datasource: document.getElementById("datasource"),
    tablesMode: document.getElementById("tablesMode"),
    maskMode: document.getElementById("maskMode"),
    seqMode: document.getElementById("seqMode"),
    subsetMode: document.getElementById("subsetMode"),
    status: document.getElementById("status"),
    list: document.getElementById("list"),
    listBody: document.getElementById("listBody"),
    filter: document.getElementById("filter"),
    clearAll: document.getElementById("clearAll"),
    selectAll: document.getElementById("selectAll"),
    clearSelection: document.getElementById("clearSelection"),
    apply: document.getElementById("apply"),
    cancel: document.getElementById("cancel"),
    maskStatus: document.getElementById("maskStatus"),
    maskTableFilter: document.getElementById("maskTableFilter"),
    maskTableList: document.getElementById("maskTableList"),
    maskTableTitle: document.getElementById("maskTableTitle"),
    maskSelectAllWrap: document.getElementById("maskSelectAllWrap"),
    maskSelectAllCols: document.getElementById("maskSelectAllCols"),
    maskColumnFilter: document.getElementById("maskColumnFilter"),
    maskColumnList: document.getElementById("maskColumnList"),
    maskSelectionSummary: document.getElementById("maskSelectionSummary"),
    maskClearSelection: document.getElementById("maskClearSelection"),
    seqStatus: document.getElementById("seqStatus"),
    seqFilter: document.getElementById("seqFilter"),
    seqList: document.getElementById("seqList"),
    seqTableFilter: document.getElementById("seqTableFilter"),
    seqTableList: document.getElementById("seqTableList"),
    seqColumnTitle: document.getElementById("seqColumnTitle"),
    seqColumnFilter: document.getElementById("seqColumnFilter"),
    seqColumnList: document.getElementById("seqColumnList"),
    seqSelectionSummary: document.getElementById("seqSelectionSummary"),
    seqClearSelection: document.getElementById("seqClearSelection"),
    subsetStatus: document.getElementById("subsetStatus"),
    subsetTableFilter: document.getElementById("subsetTableFilter"),
    subsetTableList: document.getElementById("subsetTableList"),
    subsetTableTitle: document.getElementById("subsetTableTitle"),
    subsetWorkbench: document.getElementById("subsetWorkbench"),
    subsetEntireTable: document.getElementById("subsetEntireTable"),
    subsetLimit: document.getElementById("subsetLimit"),
    subsetSeedLimitWrap: document.getElementById("subsetSeedLimitWrap"),
    subsetWhere: document.getElementById("subsetWhere"),
    subsetBrowseApply: document.getElementById("subsetBrowseApply"),
    subsetAddDriver: document.getElementById("subsetAddDriver"),
    subsetBrowseStatus: document.getElementById("subsetBrowseStatus"),
    subsetGridHead: document.getElementById("subsetGridHead"),
    subsetGridBody: document.getElementById("subsetGridBody"),
    subsetPager: document.getElementById("subsetPager"),
    subsetPrevPage: document.getElementById("subsetPrevPage"),
    subsetNextPage: document.getElementById("subsetNextPage"),
    subsetPageLabel: document.getElementById("subsetPageLabel"),
    subsetPageSize: document.getElementById("subsetPageSize"),
    subsetDriversList: document.getElementById("subsetDriversList"),
    subsetPreviewStatus: document.getElementById("subsetPreviewStatus"),
    subsetPreviewList: document.getElementById("subsetPreviewList"),
  };

  els.datasource.textContent = cfg.datasource || "—";
  const draft = cfg.draft || {};

  function setStatus(el, msg, isError) {
    if (!el) return;
    el.textContent = msg;
    el.classList.toggle("error", !!isError);
  }

  function authQuery() {
    return (
      "sessionId=" +
      encodeURIComponent(cfg.sessionId) +
      "&token=" +
      encodeURIComponent(cfg.token)
    );
  }

  async function postApply(config) {
    els.apply.disabled = true;
    try {
      const res = await fetch("/control/builder/api/apply", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionId: cfg.sessionId,
          token: cfg.token,
          config,
        }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        throw new Error(data.messageId || "Apply failed (" + res.status + ")");
      }
      if (window.opener && !window.opener.closed) {
        try {
          window.opener.postMessage(
            {
              type: "quemsi-builder-done",
              sessionId: cfg.sessionId,
              mode: mode,
              resultConfig: config,
            },
            "*"
          );
        } catch (ignore) {}
        window.close();
        return;
      }
      if (data.redirectUrl) {
        window.location.href = data.redirectUrl;
        return;
      }
      window.close();
    } catch (e) {
      setStatus(
        isMask
          ? els.maskStatus
          : isSeq
            ? els.seqStatus
            : isSubset
              ? els.subsetStatus
              : els.status,
        e.message || String(e),
        true
      );
      els.apply.disabled = false;
    }
  }

  async function doCancel() {
    try {
      await fetch("/control/builder/api/cancel", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId: cfg.sessionId, token: cfg.token }),
      });
    } catch (ignore) {}
    if (window.opener && !window.opener.closed) {
      try {
        window.opener.postMessage(
          { type: "quemsi-builder-cancelled", sessionId: cfg.sessionId },
          "*"
        );
      } catch (ignore) {}
      window.close();
      return;
    }
    const returnUrl = (window.__BUILDER__ && window.__BUILDER__.returnUrl) || null;
    if (returnUrl) {
      window.location.href = returnUrl;
      return;
    }
    window.close();
  }

  els.cancel.addEventListener("click", doCancel);

  /* ---------- ClearTables / DropTables ---------- */
  function initTablesMode() {
    document.title = (isDrop ? "DropTables" : "ClearTables") + " — Quemsi Agent";
    els.title.textContent = isDrop ? "Drop tables" : "Clear tables";
    els.allLabel.textContent = isDrop
      ? "Drop all (tables, views, sequences, …)"
      : "Clear all tables";
    if (isDrop) {
      els.allHint.hidden = false;
      els.allHint.textContent =
        "When “Drop all” is on, the step also removes views, sequences, triggers, functions, and related schema objects at runtime.";
    }
    els.tablesMode.hidden = false;

    let tables = [];
    const selected = new Set();

    if (draft.all === true) {
      els.clearAll.checked = true;
    }
    if (Array.isArray(draft.tables)) {
      draft.tables.forEach((t) => selected.add(String(t)));
    }

    function visibleNames() {
      const q = (els.filter.value || "").trim().toLowerCase();
      return tables.filter((name) => !q || name.toLowerCase().includes(q));
    }

    function syncSelectAllCheckbox() {
      const visible = visibleNames();
      if (!visible.length) {
        els.selectAll.checked = false;
        els.selectAll.indeterminate = false;
        return;
      }
      const selectedVisible = visible.filter((name) => selected.has(name)).length;
      els.selectAll.checked = selectedVisible === visible.length;
      els.selectAll.indeterminate = selectedVisible > 0 && selectedVisible < visible.length;
    }

    function syncAllMode() {
      const all = els.clearAll.checked;
      els.list.classList.toggle("disabled", all);
      els.clearSelection.disabled = all;
      els.filter.disabled = all;
      els.selectAll.disabled = all;
    }

    function updateStatusCount() {
      setStatus(els.status, tables.length + " table(s) · " + selected.size + " selected");
    }

    function render() {
      const q = (els.filter.value || "").trim().toLowerCase();
      els.listBody.innerHTML = "";
      tables.forEach((name) => {
        const row = document.createElement("label");
        row.className = "row";
        if (q && !name.toLowerCase().includes(q)) {
          row.classList.add("hidden");
        }
        const cb = document.createElement("input");
        cb.type = "checkbox";
        cb.value = name;
        cb.checked = selected.has(name);
        cb.addEventListener("change", () => {
          if (cb.checked) selected.add(name);
          else selected.delete(name);
          syncSelectAllCheckbox();
          updateStatusCount();
        });
        const span = document.createElement("span");
        span.textContent = name;
        row.appendChild(cb);
        row.appendChild(span);
        els.listBody.appendChild(row);
      });
      els.list.hidden = false;
      syncSelectAllCheckbox();
      syncAllMode();
      updateStatusCount();
    }

    els.filter.addEventListener("input", render);
    els.clearAll.addEventListener("change", syncAllMode);
    els.selectAll.addEventListener("change", () => {
      const visible = visibleNames();
      if (els.selectAll.checked) {
        visible.forEach((name) => selected.add(name));
      } else {
        visible.forEach((name) => selected.delete(name));
      }
      render();
    });
    els.clearSelection.addEventListener("click", () => {
      selected.clear();
      render();
    });
    els.apply.addEventListener("click", () => {
      postApply({
        all: !!els.clearAll.checked,
        tables: els.clearAll.checked ? [] : Array.from(selected),
      });
    });

    (async function loadTables() {
      try {
        const res = await fetch("/control/builder/api/tables?" + authQuery());
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load tables (" + res.status + ")");
        }
        const data = await res.json();
        tables = Array.isArray(data.tables) ? data.tables : [];
        render();
      } catch (e) {
        setStatus(els.status, e.message || String(e), true);
      }
    })();
  }

  /* ---------- MaskColumns ---------- */
  function initMaskMode() {
    document.title = "MaskColumns — Quemsi Agent";
    els.title.textContent = "Mask columns";
    els.allHint.hidden = false;
    els.allHint.textContent =
      "Pick tables and columns from the live schema. Nested Mongo field paths can still be added manually in the flow editor.";
    els.maskMode.hidden = false;

    let tables = [];
    /** key: schema\\0table\\0column */
    const selected = new Map();
    let activeTable = null;
    let activeMeta = { schema: "", name: "", columns: [] };
    const columnCache = new Map();

    if (Array.isArray(draft.columns)) {
      draft.columns.forEach((c) => {
        if (!c || !c.table || !c.column) return;
        const schema = c.schema != null ? String(c.schema) : "";
        const table = String(c.table);
        const column = String(c.column);
        selected.set(selKey(schema, table, column), { schema, table, column });
      });
    }

    function selKey(schema, table, column) {
      return String(schema || "") + "\0" + table + "\0" + column;
    }

    function splitQualified(qualified) {
      const i = qualified.indexOf(".");
      if (i < 0) return { schema: "", name: qualified };
      return { schema: qualified.slice(0, i), name: qualified.slice(i + 1) };
    }

    function countForTable(qualified) {
      const parts = splitQualified(qualified);
      let n = 0;
      selected.forEach((v) => {
        if (v.table === parts.name && String(v.schema || "") === String(parts.schema || "")) n += 1;
      });
      return n;
    }

    function updateSummary() {
      els.maskSelectionSummary.textContent = selected.size + " column(s) selected";
    }

    function renderTables() {
      const q = (els.maskTableFilter.value || "").trim().toLowerCase();
      els.maskTableList.innerHTML = "";
      tables.forEach((qualified) => {
        if (q && !qualified.toLowerCase().includes(q)) return;
        const row = document.createElement("div");
        row.className = "row table-item" + (activeTable === qualified ? " active" : "");
        row.tabIndex = 0;
        const span = document.createElement("span");
        span.textContent = qualified;
        const badge = document.createElement("span");
        badge.className = "badge";
        const n = countForTable(qualified);
        badge.textContent = n ? n + " selected" : "";
        row.appendChild(span);
        row.appendChild(badge);
        row.addEventListener("click", () => selectTable(qualified));
        row.addEventListener("keydown", (e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            selectTable(qualified);
          }
        });
        els.maskTableList.appendChild(row);
      });
      updateSummary();
    }

    async function selectTable(qualified) {
      activeTable = qualified;
      renderTables();
      try {
        if (columnCache.has(qualified)) {
          activeMeta = columnCache.get(qualified);
          renderColumns();
          return;
        }
        setStatus(els.maskStatus, "Loading columns for " + qualified + "…");
        const res = await fetch(
          "/control/builder/api/columns?" + authQuery() + "&table=" + encodeURIComponent(qualified)
        );
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load columns (" + res.status + ")");
        }
        const data = await res.json();
        activeMeta = {
          schema: data.schema != null ? String(data.schema) : splitQualified(qualified).schema,
          name: data.name != null ? String(data.name) : splitQualified(qualified).name,
          columns: Array.isArray(data.columns) ? data.columns : [],
        };
        columnCache.set(qualified, activeMeta);
        setStatus(els.maskStatus, tables.length + " table(s)");
        renderColumns();
      } catch (e) {
        setStatus(els.maskStatus, e.message || String(e), true);
      }
    }

    function selectionParts() {
      if (activeMeta && activeMeta.name) {
        return { schema: activeMeta.schema || "", name: activeMeta.name };
      }
      return splitQualified(activeTable || "");
    }

    function syncColSelectAll() {
      const q = (els.maskColumnFilter.value || "").trim().toLowerCase();
      const visible = (activeMeta.columns || []).filter((c) => !q || c.toLowerCase().includes(q));
      if (!visible.length) {
        els.maskSelectAllCols.checked = false;
        els.maskSelectAllCols.indeterminate = false;
        return;
      }
      const parts = selectionParts();
      let selectedVisible = 0;
      visible.forEach((col) => {
        if (selected.has(selKey(parts.schema, parts.name, col))) selectedVisible += 1;
      });
      els.maskSelectAllCols.checked = selectedVisible === visible.length;
      els.maskSelectAllCols.indeterminate = selectedVisible > 0 && selectedVisible < visible.length;
    }

    function renderColumns() {
      els.maskColumnList.innerHTML = "";
      if (!activeTable) {
        els.maskTableTitle.textContent = "Select a table";
        els.maskSelectAllWrap.hidden = true;
        els.maskColumnFilter.disabled = true;
        return;
      }
      els.maskTableTitle.textContent = activeTable;
      els.maskSelectAllWrap.hidden = false;
      els.maskColumnFilter.disabled = false;
      const q = (els.maskColumnFilter.value || "").trim().toLowerCase();
      const parts = selectionParts();
      (activeMeta.columns || []).forEach((col) => {
        if (q && !col.toLowerCase().includes(q)) return;
        const row = document.createElement("label");
        row.className = "row";
        const cb = document.createElement("input");
        cb.type = "checkbox";
        const key = selKey(parts.schema, parts.name, col);
        cb.checked = selected.has(key);
        cb.addEventListener("change", () => {
          if (cb.checked) {
            selected.set(key, { schema: parts.schema, table: parts.name, column: col });
          } else {
            selected.delete(key);
          }
          syncColSelectAll();
          renderTables();
          updateSummary();
        });
        const span = document.createElement("span");
        span.textContent = col;
        row.appendChild(cb);
        row.appendChild(span);
        els.maskColumnList.appendChild(row);
      });
      syncColSelectAll();
      updateSummary();
    }

    els.maskTableFilter.addEventListener("input", renderTables);
    els.maskColumnFilter.addEventListener("input", renderColumns);
    els.maskSelectAllCols.addEventListener("change", () => {
      if (!activeTable) return;
      const q = (els.maskColumnFilter.value || "").trim().toLowerCase();
      const parts = selectionParts();
      const visible = (activeMeta.columns || []).filter((c) => !q || c.toLowerCase().includes(q));
      visible.forEach((col) => {
        const key = selKey(parts.schema, parts.name, col);
        if (els.maskSelectAllCols.checked) {
          selected.set(key, { schema: parts.schema, table: parts.name, column: col });
        } else {
          selected.delete(key);
        }
      });
      renderColumns();
      renderTables();
    });
    els.maskClearSelection.addEventListener("click", () => {
      selected.clear();
      renderColumns();
      renderTables();
    });
    els.apply.addEventListener("click", () => {
      postApply({ columns: Array.from(selected.values()) });
    });

    (async function loadTables() {
      try {
        const res = await fetch("/control/builder/api/tables?" + authQuery());
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load tables (" + res.status + ")");
        }
        const data = await res.json();
        tables = Array.isArray(data.tables) ? data.tables : [];
        setStatus(els.maskStatus, tables.length + " table(s)");
        renderTables();
        if (tables.length) {
          await selectTable(tables[0]);
        }
      } catch (e) {
        setStatus(els.maskStatus, e.message || String(e), true);
      }
    })();
  }

  /* ---------- UpdateSequences ---------- */
  function initSeqMode() {
    document.title = "UpdateSequences — Quemsi Agent";
    els.title.textContent = "Update sequences";
    els.allHint.hidden = false;
    els.allHint.textContent =
      "Map each sequence to the table column that should drive its next value. Template-based updates stay in the flow editor.";
    els.seqMode.hidden = false;

    let sequences = [];
    let tables = [];
    const selected = new Map();
    let activeSeq = null;
    let activeTable = null;
    let activeTableMeta = { schema: "", name: "", columns: [] };
    const columnCache = new Map();

    if (Array.isArray(draft.customMappings)) {
      draft.customMappings.forEach((m) => {
        if (!m || !m.sequence || !m.table || !m.column) return;
        const schema = m.schema != null ? String(m.schema) : "";
        const sequence = String(m.sequence);
        const table = String(m.table);
        const column = String(m.column);
        selected.set(mapKey(sequence, schema, table, column), {
          sequence,
          schema,
          table,
          column,
        });
      });
    }

    function mapKey(sequence, schema, table, column) {
      return [sequence, schema || "", table, column].join("\0");
    }

    function splitQualified(qualified) {
      const i = qualified.indexOf(".");
      if (i < 0) return { schema: "", name: qualified };
      return { schema: qualified.slice(0, i), name: qualified.slice(i + 1) };
    }

    function countForSeq(seq) {
      let n = 0;
      selected.forEach((v) => {
        if (v.sequence === seq.name && String(v.schema || "") === String(seq.schema || "")) n += 1;
      });
      return n;
    }

    function updateSummary() {
      els.seqSelectionSummary.textContent = selected.size + " mapping(s)";
    }

    function renderSequences() {
      const q = (els.seqFilter.value || "").trim().toLowerCase();
      els.seqList.innerHTML = "";
      sequences.forEach((seq) => {
        const label = seq.qualified || seq.name;
        if (q && !label.toLowerCase().includes(q)) return;
        const row = document.createElement("div");
        const active =
          activeSeq &&
          activeSeq.name === seq.name &&
          String(activeSeq.schema || "") === String(seq.schema || "");
        row.className = "row table-item" + (active ? " active" : "");
        row.tabIndex = 0;
        const span = document.createElement("span");
        span.textContent = label;
        const badge = document.createElement("span");
        badge.className = "badge";
        const n = countForSeq(seq);
        badge.textContent = n ? n + " mapped" : "";
        row.appendChild(span);
        row.appendChild(badge);
        row.addEventListener("click", () => {
          activeSeq = seq;
          renderSequences();
          renderColumns();
        });
        els.seqList.appendChild(row);
      });
      updateSummary();
    }

    function renderTables() {
      const q = (els.seqTableFilter.value || "").trim().toLowerCase();
      els.seqTableList.innerHTML = "";
      tables.forEach((qualified) => {
        if (q && !qualified.toLowerCase().includes(q)) return;
        const row = document.createElement("div");
        row.className = "row table-item" + (activeTable === qualified ? " active" : "");
        row.tabIndex = 0;
        const span = document.createElement("span");
        span.textContent = qualified;
        row.appendChild(span);
        row.addEventListener("click", () => selectTable(qualified));
        els.seqTableList.appendChild(row);
      });
    }

    function renderColumns() {
      els.seqColumnList.innerHTML = "";
      if (!activeTable) {
        els.seqColumnTitle.textContent = "Column";
        els.seqColumnFilter.disabled = true;
        return;
      }
      if (!activeSeq) {
        els.seqColumnTitle.textContent = "Select a sequence first";
        els.seqColumnFilter.disabled = true;
        return;
      }
      els.seqColumnTitle.textContent = activeTable + " → " + (activeSeq.qualified || activeSeq.name);
      els.seqColumnFilter.disabled = false;
      const q = (els.seqColumnFilter.value || "").trim().toLowerCase();
      const tableSchema = activeTableMeta.schema || "";
      const tableName = activeTableMeta.name || splitQualified(activeTable).name;
      const schema =
        activeSeq.schema != null && String(activeSeq.schema) !== ""
          ? String(activeSeq.schema)
          : tableSchema;
      (activeTableMeta.columns || []).forEach((col) => {
        if (q && !col.toLowerCase().includes(q)) return;
        const row = document.createElement("label");
        row.className = "row";
        const cb = document.createElement("input");
        cb.type = "checkbox";
        const key = mapKey(activeSeq.name, schema, tableName, col);
        cb.checked = selected.has(key);
        cb.addEventListener("change", () => {
          if (cb.checked) {
            selected.set(key, {
              sequence: activeSeq.name,
              schema: schema,
              table: tableName,
              column: col,
            });
          } else {
            selected.delete(key);
          }
          renderSequences();
          updateSummary();
        });
        const span = document.createElement("span");
        span.textContent = col;
        row.appendChild(cb);
        row.appendChild(span);
        els.seqColumnList.appendChild(row);
      });
      updateSummary();
    }

    async function selectTable(qualified) {
      activeTable = qualified;
      renderTables();
      try {
        if (columnCache.has(qualified)) {
          activeTableMeta = columnCache.get(qualified);
          renderColumns();
          return;
        }
        setStatus(els.seqStatus, "Loading columns for " + qualified + "…");
        const res = await fetch(
          "/control/builder/api/columns?" + authQuery() + "&table=" + encodeURIComponent(qualified)
        );
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load columns (" + res.status + ")");
        }
        const data = await res.json();
        activeTableMeta = {
          schema: data.schema != null ? String(data.schema) : splitQualified(qualified).schema,
          name: data.name != null ? String(data.name) : splitQualified(qualified).name,
          columns: Array.isArray(data.columns) ? data.columns : [],
        };
        columnCache.set(qualified, activeTableMeta);
        setStatus(
          els.seqStatus,
          sequences.length + " sequence(s) · " + tables.length + " table(s)"
        );
        renderColumns();
      } catch (e) {
        setStatus(els.seqStatus, e.message || String(e), true);
      }
    }

    els.seqFilter.addEventListener("input", renderSequences);
    els.seqTableFilter.addEventListener("input", renderTables);
    els.seqColumnFilter.addEventListener("input", renderColumns);
    els.seqClearSelection.addEventListener("click", () => {
      selected.clear();
      renderSequences();
      renderColumns();
      updateSummary();
    });
    els.apply.addEventListener("click", () => {
      postApply({ customMappings: Array.from(selected.values()) });
    });

    (async function load() {
      try {
        const [seqRes, tableRes] = await Promise.all([
          fetch("/control/builder/api/sequences?" + authQuery()),
          fetch("/control/builder/api/tables?" + authQuery()),
        ]);
        if (!seqRes.ok) {
          const body = await seqRes.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load sequences (" + seqRes.status + ")");
        }
        if (!tableRes.ok) {
          const body = await tableRes.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load tables (" + tableRes.status + ")");
        }
        const seqData = await seqRes.json();
        const tableData = await tableRes.json();
        sequences = Array.isArray(seqData.sequences) ? seqData.sequences : [];
        tables = Array.isArray(tableData.tables) ? tableData.tables : [];
        setStatus(
          els.seqStatus,
          sequences.length + " sequence(s) · " + tables.length + " table(s)"
        );
        if (!sequences.length) {
          setStatus(
            els.seqStatus,
            "No sequences found (MySQL/MongoDB do not use sequences).",
            true
          );
        }
        renderSequences();
        renderTables();
        if (sequences.length) {
          activeSeq = sequences[0];
          renderSequences();
        }
        if (tables.length) {
          await selectTable(tables[0]);
        }
      } catch (e) {
        setStatus(els.seqStatus, e.message || String(e), true);
      }
    })();
  }

  if (isMask) {
    initMaskMode();
  } else if (isSeq) {
    initSeqMode();
  } else if (isSubset) {
    initSubsetMode();
  } else {
    initTablesMode();
  }

  /* ---------- Subset ---------- */
  function initSubsetMode() {
    document.title = "Subset — Quemsi Agent";
    els.title.textContent = "Configure subset drivers";
    els.allHint.hidden = false;
    els.allHint.textContent =
      "Select a table to browse. Seed limit applies only when adding a filter driver (not selected rows / entire table). Page size is for the grid pager.";
    els.subsetMode.hidden = false;
    document.querySelector(".wrap")?.classList.add("subset-wide");

    let tables = [];
    /** @type {Array<{table:string,where?:string,limit?:number|null,entireTable?:boolean}>} */
    let drivers = [];
    let activeTable = null;
    /** @type {Set<string>} */
    let selectedKeys = new Set();
    let previewTimer = null;
    let browsePage = 0;
    let browseTotal = 0;
    let browsePageSize = 50;

    if (Array.isArray(draft.drivers)) {
      drivers = draft.drivers
        .filter((d) => d && d.table)
        .map((d) => ({
          table: String(d.table),
          where: d.where != null ? String(d.where) : "",
          limit: d.limit != null && d.limit !== "" ? Number(d.limit) : null,
          entireTable: !!d.entireTable,
        }));
    }

    function driverIndexFor(table) {
      return drivers.findIndex((d) => d.table === table);
    }

    function syncEntireControls() {
      const entire = els.subsetEntireTable.checked;
      els.subsetWhere.disabled = entire;
      els.subsetLimit.disabled = entire;
      if (els.subsetSeedLimitWrap) {
        els.subsetSeedLimitWrap.style.opacity = entire ? "0.5" : "1";
      }
      els.subsetGridBody.querySelectorAll('input[type="checkbox"]').forEach((cb) => {
        cb.disabled = entire;
        if (entire) cb.checked = false;
      });
      if (entire) selectedKeys.clear();
    }

    function renderTables() {
      const q = (els.subsetTableFilter.value || "").trim().toLowerCase();
      els.subsetTableList.innerHTML = "";
      tables.forEach((name) => {
        if (q && !name.toLowerCase().includes(q)) return;
        const row = document.createElement("div");
        const has = driverIndexFor(name) >= 0;
        row.className =
          "row table-item" +
          (activeTable === name ? " active" : "") +
          (has ? " has-driver" : "");
        row.tabIndex = 0;
        const span = document.createElement("span");
        span.textContent = name;
        row.appendChild(span);
        if (has) {
          const badge = document.createElement("span");
          badge.className = "badge";
          badge.textContent = "driver";
          row.appendChild(badge);
        }
        row.addEventListener("click", () => selectTable(name));
        els.subsetTableList.appendChild(row);
      });
    }

    function selectTable(name) {
      activeTable = name;
      selectedKeys.clear();
      browsePage = 0;
      els.subsetTableTitle.textContent = name;
      els.subsetWorkbench.hidden = false;
      const existing = drivers[driverIndexFor(name)];
      els.subsetEntireTable.checked = !!(existing && existing.entireTable);
      els.subsetWhere.value = existing && !existing.entireTable ? existing.where || "" : "";
      els.subsetLimit.value =
        existing && existing.limit != null && !existing.entireTable ? String(existing.limit) : "";
      els.subsetGridHead.innerHTML = "";
      els.subsetGridBody.innerHTML = "";
      syncEntireControls();
      renderTables();
      loadBrowse({ clearSelection: true });
    }

    function updatePager() {
      const size = browsePageSize || 50;
      const totalPages = Math.max(1, Math.ceil(browseTotal / size) || 1);
      const pageDisplay = browseTotal === 0 ? 0 : browsePage + 1;
      els.subsetPager.hidden = false;
      els.subsetPageLabel.textContent =
        browseTotal === 0
          ? "0 rows"
          : "Page " + pageDisplay + " of " + totalPages + " · " + browseTotal + " rows";
      els.subsetPrevPage.disabled = browsePage <= 0 || browseTotal === 0;
      els.subsetNextPage.disabled = browseTotal === 0 || (browsePage + 1) * size >= browseTotal;
    }

    function renderDrivers() {
      els.subsetDriversList.innerHTML = "";
      if (!drivers.length) {
        const empty = document.createElement("div");
        empty.className = "row";
        empty.textContent = "No drivers yet";
        els.subsetDriversList.appendChild(empty);
        return;
      }
      drivers.forEach((d, i) => {
        const row = document.createElement("div");
        row.className = "row driver-item";
        const title = document.createElement("strong");
        title.textContent = d.table;
        const detail = document.createElement("span");
        detail.style.fontSize = "0.8rem";
        detail.style.color = "var(--muted)";
        if (d.entireTable) {
          detail.textContent = "Entire table";
        } else {
          let t = d.where || "";
          if (d.limit != null) t += (t ? " · " : "") + "seed limit " + d.limit;
          detail.textContent = t || "(empty)";
        }
        const actions = document.createElement("div");
        actions.className = "driver-actions";
        const focusBtn = document.createElement("button");
        focusBtn.type = "button";
        focusBtn.className = "btn secondary";
        focusBtn.textContent = "Edit";
        focusBtn.addEventListener("click", () => selectTable(d.table));
        const rm = document.createElement("button");
        rm.type = "button";
        rm.className = "btn secondary";
        rm.textContent = "Remove";
        rm.addEventListener("click", () => {
          drivers.splice(i, 1);
          renderDrivers();
          renderTables();
          schedulePreview();
        });
        actions.appendChild(focusBtn);
        actions.appendChild(rm);
        row.appendChild(title);
        row.appendChild(detail);
        row.appendChild(actions);
        els.subsetDriversList.appendChild(row);
      });
    }

    function renderPreview(tableSummaries) {
      els.subsetPreviewList.innerHTML = "";
      if (!tableSummaries || !tableSummaries.length) {
        setStatus(els.subsetPreviewStatus, drivers.length ? "No tables in plan" : "No drivers yet");
        return;
      }
      setStatus(els.subsetPreviewStatus, tableSummaries.length + " table(s) in plan");
      tableSummaries.forEach((s) => {
        const row = document.createElement("div");
        row.className = "row driver-item";
        const title = document.createElement("strong");
        title.textContent = s.table;
        const detail = document.createElement("span");
        detail.style.fontSize = "0.8rem";
        detail.style.color = "var(--muted)";
        const parts = [s.count + " rows"];
        if (s.driverCount) parts.push(s.driverCount + " from driver");
        if (s.requiredByFkCount) {
          const via = Array.isArray(s.requiredBy) ? s.requiredBy.join(", ") : "";
          parts.push(s.requiredByFkCount + " via FK" + (via ? " (" + via + ")" : ""));
        }
        detail.textContent = parts.join(" · ");
        row.appendChild(title);
        row.appendChild(detail);
        els.subsetPreviewList.appendChild(row);
      });
    }

    function schedulePreview() {
      if (previewTimer) clearTimeout(previewTimer);
      previewTimer = setTimeout(runPreview, 350);
    }

    async function runPreview() {
      if (!drivers.length) {
        renderPreview([]);
        return;
      }
      setStatus(els.subsetPreviewStatus, "Updating preview…");
      try {
        const res = await fetch("/control/builder/api/preview-subset", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            sessionId: cfg.sessionId,
            token: cfg.token,
            drivers: drivers,
          }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(data.messageId || data.message || "Preview failed (" + res.status + ")");
        }
        renderPreview(Array.isArray(data.tables) ? data.tables : []);
      } catch (e) {
        setStatus(els.subsetPreviewStatus, e.message || String(e), true);
      }
    }

    function renderGrid(columns, rows) {
      els.subsetGridHead.innerHTML = "";
      els.subsetGridBody.innerHTML = "";
      const headRow = document.createElement("tr");
      const th0 = document.createElement("th");
      th0.textContent = "";
      headRow.appendChild(th0);
      (columns || []).forEach((c) => {
        const th = document.createElement("th");
        th.textContent = c;
        headRow.appendChild(th);
      });
      els.subsetGridHead.appendChild(headRow);
      const entire = els.subsetEntireTable.checked;
      (rows || []).forEach((r) => {
        const tr = document.createElement("tr");
        const td0 = document.createElement("td");
        const cb = document.createElement("input");
        cb.type = "checkbox";
        cb.disabled = entire;
        cb.checked = selectedKeys.has(r.pkKey);
        cb.addEventListener("change", () => {
          if (cb.checked) selectedKeys.add(r.pkKey);
          else selectedKeys.delete(r.pkKey);
        });
        td0.appendChild(cb);
        tr.appendChild(td0);
        (r.values || []).forEach((v) => {
          const td = document.createElement("td");
          td.textContent = v == null ? "" : String(v);
          tr.appendChild(td);
        });
        els.subsetGridBody.appendChild(tr);
      });
    }

    async function loadBrowse(opts) {
      if (!activeTable) return;
      const clearSelection = !!(opts && opts.clearSelection);
      if (clearSelection) selectedKeys.clear();
      browsePageSize = Number(els.subsetPageSize.value) || 50;
      setStatus(els.subsetBrowseStatus, "Loading rows…");
      try {
        const entire = els.subsetEntireTable.checked;
        const res = await fetch("/control/builder/api/browse-rows", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            sessionId: cfg.sessionId,
            token: cfg.token,
            table: activeTable,
            entireTable: entire,
            where: entire ? null : els.subsetWhere.value || null,
            pageSize: browsePageSize,
            page: browsePage,
          }),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(data.messageId || data.message || "Browse failed (" + res.status + ")");
        }
        browseTotal = typeof data.totalCount === "number" ? data.totalCount : 0;
        browsePage = typeof data.page === "number" ? data.page : browsePage;
        browsePageSize = typeof data.pageSize === "number" ? data.pageSize : browsePageSize;
        renderGrid(data.columns || [], data.rows || []);
        syncEntireControls();
        updatePager();
        const selNote = selectedKeys.size ? " · " + selectedKeys.size + " selected" : "";
        setStatus(
          els.subsetBrowseStatus,
          (data.rows || []).length +
            " row(s) on this page" +
            (entire ? " (entire table — selection disabled)" : selNote)
        );
      } catch (e) {
        setStatus(els.subsetBrowseStatus, e.message || String(e), true);
        els.subsetPager.hidden = true;
      }
    }

    function applyFilter() {
      browsePage = 0;
      loadBrowse({ clearSelection: true });
    }

    async function addToSubset() {
      if (!activeTable) return;
      const entire = els.subsetEntireTable.checked;
      let driver;
      if (entire) {
        driver = { table: activeTable, entireTable: true, where: "", limit: null };
      } else if (selectedKeys.size > 0) {
        try {
          const res = await fetch("/control/builder/api/pk-predicate", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              sessionId: cfg.sessionId,
              token: cfg.token,
              table: activeTable,
              keys: Array.from(selectedKeys),
            }),
          });
          const data = await res.json().catch(() => ({}));
          if (!res.ok) {
            throw new Error(data.messageId || data.message || "Could not build PK predicate");
          }
          driver = {
            table: activeTable,
            entireTable: false,
            where: data.where,
            limit: null,
          };
        } catch (e) {
          setStatus(els.subsetBrowseStatus, e.message || String(e), true);
          return;
        }
      } else {
        const where = (els.subsetWhere.value || "").trim();
        if (!where) {
          setStatus(
            els.subsetBrowseStatus,
            "Set entire table, select rows, or enter a filter before adding.",
            true
          );
          return;
        }
        const limitVal = els.subsetLimit.value;
        driver = {
          table: activeTable,
          entireTable: false,
          where: where,
          limit: limitVal === "" ? null : Number(limitVal),
        };
      }
      const idx = driverIndexFor(activeTable);
      if (idx >= 0) drivers[idx] = driver;
      else drivers.push(driver);
      setStatus(els.subsetBrowseStatus, "Driver added for " + activeTable);
      renderDrivers();
      renderTables();
      schedulePreview();
    }

    els.subsetTableFilter.addEventListener("input", renderTables);
    els.subsetEntireTable.addEventListener("change", () => {
      syncEntireControls();
      browsePage = 0;
      loadBrowse({ clearSelection: true });
    });
    els.subsetBrowseApply.addEventListener("click", applyFilter);
    els.subsetAddDriver.addEventListener("click", addToSubset);
    els.subsetPrevPage.addEventListener("click", () => {
      if (browsePage > 0) {
        browsePage -= 1;
        loadBrowse({ clearSelection: false });
      }
    });
    els.subsetNextPage.addEventListener("click", () => {
      const size = browsePageSize || 50;
      if ((browsePage + 1) * size < browseTotal) {
        browsePage += 1;
        loadBrowse({ clearSelection: false });
      }
    });
    els.subsetPageSize.addEventListener("change", () => {
      browsePage = 0;
      loadBrowse({ clearSelection: false });
    });
    els.apply.addEventListener("click", () => {
      if (!drivers.length) {
        setStatus(els.subsetStatus, "Add at least one driver before applying.", true);
        return;
      }
      postApply({
        enabled: true,
        drivers: drivers.map((d) => ({
          table: d.table,
          where: d.entireTable ? "" : d.where || "",
          limit: d.entireTable ? null : d.limit,
          entireTable: !!d.entireTable,
        })),
      });
    });

    renderDrivers();
    schedulePreview();

    (async function loadTables() {
      try {
        const res = await fetch("/control/builder/api/tables?" + authQuery());
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body.messageId || "Failed to load tables (" + res.status + ")");
        }
        const data = await res.json();
        tables = Array.isArray(data.tables) ? data.tables : [];
        setStatus(els.subsetStatus, tables.length + " table(s)");
        renderTables();
      } catch (e) {
        setStatus(els.subsetStatus, e.message || String(e), true);
      }
    })();
  }
})();
