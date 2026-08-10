(function () {
  const cfg = window.__BUILDER__ || {};
  const mode = cfg.mode || "CLEAR_TABLES";
  const isDrop = mode === "DROP_TABLES";

  const els = {
    title: document.getElementById("title"),
    allLabel: document.getElementById("allLabel"),
    allHint: document.getElementById("allHint"),
    datasource: document.getElementById("datasource"),
    status: document.getElementById("status"),
    list: document.getElementById("list"),
    filter: document.getElementById("filter"),
    clearAll: document.getElementById("clearAll"),
    selectVisible: document.getElementById("selectVisible"),
    clearSelection: document.getElementById("clearSelection"),
    apply: document.getElementById("apply"),
    cancel: document.getElementById("cancel"),
  };

  document.title = (isDrop ? "DropTables" : "ClearTables") + " — Quemsi Agent";
  if (els.title) {
    els.title.textContent = isDrop ? "Drop tables" : "Clear tables";
  }
  if (els.allLabel) {
    els.allLabel.textContent = isDrop ? "Drop all (tables, views, sequences, …)" : "Clear all tables";
  }
  if (els.allHint && isDrop) {
    els.allHint.hidden = false;
    els.allHint.textContent =
      "When “Drop all” is on, the step also removes views, sequences, triggers, functions, and related schema objects at runtime.";
  }

  els.datasource.textContent = cfg.datasource || "—";

  const draft = cfg.draft || {};
  let tables = [];
  const selected = new Set();

  if (draft.all === true) {
    els.clearAll.checked = true;
  }
  if (Array.isArray(draft.tables)) {
    draft.tables.forEach((t) => selected.add(String(t)));
  }

  function setStatus(msg, isError) {
    els.status.textContent = msg;
    els.status.classList.toggle("error", !!isError);
  }

  function syncAllMode() {
    const all = els.clearAll.checked;
    els.list.classList.toggle("disabled", all);
    els.selectVisible.disabled = all;
    els.clearSelection.disabled = all;
    els.filter.disabled = all;
  }

  function render() {
    const q = (els.filter.value || "").trim().toLowerCase();
    els.list.innerHTML = "";
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
      });
      const span = document.createElement("span");
      span.textContent = name;
      row.appendChild(cb);
      row.appendChild(span);
      els.list.appendChild(row);
    });
    els.list.hidden = false;
    syncAllMode();
  }

  async function loadTables() {
    try {
      const url =
        "/control/builder/api/tables?sessionId=" +
        encodeURIComponent(cfg.sessionId) +
        "&token=" +
        encodeURIComponent(cfg.token);
      const res = await fetch(url);
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.messageId || "Failed to load tables (" + res.status + ")");
      }
      const data = await res.json();
      tables = Array.isArray(data.tables) ? data.tables : [];
      setStatus(tables.length + " table(s)");
      render();
    } catch (e) {
      setStatus(e.message || String(e), true);
    }
  }

  els.filter.addEventListener("input", render);
  els.clearAll.addEventListener("change", syncAllMode);
  els.selectVisible.addEventListener("click", () => {
    document.querySelectorAll(".row:not(.hidden) input[type=checkbox]").forEach((cb) => {
      cb.checked = true;
      selected.add(cb.value);
    });
  });
  els.clearSelection.addEventListener("click", () => {
    selected.clear();
    render();
  });

  els.apply.addEventListener("click", async () => {
    els.apply.disabled = true;
    const config = {
      all: !!els.clearAll.checked,
      tables: els.clearAll.checked ? [] : Array.from(selected),
    };
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
      setStatus(e.message || String(e), true);
      els.apply.disabled = false;
    }
  });

  els.cancel.addEventListener("click", async () => {
    try {
      await fetch("/control/builder/api/cancel", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId: cfg.sessionId, token: cfg.token }),
      });
    } catch (ignore) {
      /* still close / leave */
    }
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
  });

  loadTables();
})();
