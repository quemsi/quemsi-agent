(function () {
  const cfg = window.__BUILDER__ || {};
  const mode = cfg.mode || "CLEAR_TABLES";
  const isDrop = mode === "DROP_TABLES";
  const isMask = mode === "MASK_COLUMNS";

  const els = {
    title: document.getElementById("title"),
    allLabel: document.getElementById("allLabel"),
    allHint: document.getElementById("allHint"),
    datasource: document.getElementById("datasource"),
    tablesMode: document.getElementById("tablesMode"),
    maskMode: document.getElementById("maskMode"),
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
      setStatus(isMask ? els.maskStatus : els.status, e.message || String(e), true);
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

  if (isMask) {
    initMaskMode();
  } else {
    initTablesMode();
  }
})();
