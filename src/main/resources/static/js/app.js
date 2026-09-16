(() => {
  const KEY_STORAGE = "apiTracker.apiKey";
  const TOKEN_STORAGE = "apiTracker.jwt";
  const USER_STORAGE = "apiTracker.username";
  const REFRESH_MS = 15000;

  const els = {
    apiKey: document.getElementById("apiKey"),
    saveKeyBtn: document.getElementById("saveKeyBtn"),
    refreshBtn: document.getElementById("refreshBtn"),
    loginUsername: document.getElementById("loginUsername"),
    loginPassword: document.getElementById("loginPassword"),
    loginBtn: document.getElementById("loginBtn"),
    logoutBtn: document.getElementById("logoutBtn"),
    authLoggedOut: document.getElementById("authLoggedOut"),
    authLoggedIn: document.getElementById("authLoggedIn"),
    loggedInUser: document.getElementById("loggedInUser"),
    statusBanner: document.getElementById("statusBanner"),
    apisBody: document.getElementById("apisBody"),
    alertsList: document.getElementById("alertsList"),
    checksBody: document.getElementById("checksBody"),
    detailPanel: document.getElementById("detailPanel"),
    detailSub: document.getElementById("detailSub"),
    createForm: document.getElementById("createForm"),
    lastRefresh: document.getElementById("lastRefresh"),
    metricTotal: document.getElementById("metricTotal"),
    metricUp: document.getElementById("metricUp"),
    metricDegraded: document.getElementById("metricDegraded"),
    metricDown: document.getElementById("metricDown"),
    metricAlerts: document.getElementById("metricAlerts"),
    metricUptime: document.getElementById("metricUptime"),
    metricLatency: document.getElementById("metricLatency"),
    detailSummary: document.getElementById("detailSummary"),
  };

  let selectedApiId = null;
  let refreshTimer = null;

  function getApiKey() {
    return (els.apiKey.value || sessionStorage.getItem(KEY_STORAGE) || "").trim();
  }

  function getJwt() {
    return (sessionStorage.getItem(TOKEN_STORAGE) || "").trim();
  }

  function isLoggedIn() {
    return Boolean(getJwt() || getApiKey());
  }

  function updateAuthUi() {
    const jwt = getJwt();
    const username = sessionStorage.getItem(USER_STORAGE) || "admin";
    if (jwt) {
      els.authLoggedOut.hidden = true;
      els.authLoggedIn.hidden = false;
      els.loggedInUser.textContent = `Signed in as ${username}`;
    } else {
      els.authLoggedOut.hidden = false;
      els.authLoggedIn.hidden = true;
    }
  }

  function showBanner(message, ok = false) {
    els.statusBanner.hidden = !message;
    els.statusBanner.textContent = message || "";
    els.statusBanner.classList.toggle("ok", Boolean(ok));
  }

  async function api(path, options = {}) {
    const headers = Object.assign({ Accept: "application/json" }, options.headers || {});
    const jwt = getJwt();
    const key = getApiKey();
    if (jwt) {
      headers.Authorization = `Bearer ${jwt}`;
    } else if (key) {
      headers["X-API-Key"] = key;
    }
    if (options.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }

    const response = await fetch(path, Object.assign({}, options, { headers }));
    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = text;
      }
    }

    if (!response.ok) {
      if (response.status === 401 && jwt) {
        sessionStorage.removeItem(TOKEN_STORAGE);
        sessionStorage.removeItem(USER_STORAGE);
        updateAuthUi();
      }
      const message =
        (data && (data.message || data.error)) ||
        `Request failed (${response.status})`;
      throw new Error(message);
    }
    return data;
  }

  function formatTime(value) {
    if (!value) return "—";
    try {
      return new Date(value).toLocaleString();
    } catch {
      return value;
    }
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function formatPercent(value) {
    if (value == null || Number.isNaN(value)) return "—";
    return `${Number(value).toFixed(1)}%`;
  }

  function formatLatency(value) {
    if (value == null || Number.isNaN(value)) return "—";
    return `${Math.round(Number(value))} ms`;
  }

  function renderApis(apis, summaryById) {
    if (!apis.length) {
      els.apisBody.innerHTML = `<tr><td colspan="7" class="empty">No APIs registered yet.</td></tr>`;
      return;
    }

    els.apisBody.innerHTML = apis
      .map((apiItem) => {
        const endpoint = `${apiItem.httpMethod} ${apiItem.baseUrl}${apiItem.path}`;
        const disabled = apiItem.enabled === false;
        const summary = summaryById.get(apiItem.id) || {};
        return `
          <tr>
            <td>
              <strong>${escapeHtml(apiItem.name)}</strong>
              ${disabled ? "<div class='endpoint'>disabled</div>" : ""}
            </td>
            <td><span class="badge ${escapeHtml(apiItem.currentStatus)}">${escapeHtml(apiItem.currentStatus)}</span></td>
            <td>${escapeHtml(formatPercent(summary.uptimePercent))}</td>
            <td>${escapeHtml(formatLatency(summary.avgLatencyMs))}</td>
            <td class="endpoint">${escapeHtml(endpoint)}</td>
            <td>${escapeHtml(formatTime(apiItem.lastCheckedAt))}</td>
            <td class="actions">
              <button class="btn btn-quiet btn-tiny" data-action="check" data-id="${apiItem.id}">Check</button>
              <button class="btn btn-quiet btn-tiny" data-action="history" data-id="${apiItem.id}" data-name="${escapeHtml(apiItem.name)}">History</button>
              ${
                disabled
                  ? ""
                  : `<button class="btn btn-danger btn-tiny" data-action="disable" data-id="${apiItem.id}">Disable</button>`
              }
            </td>
          </tr>`;
      })
      .join("");
  }

  function renderAlerts(alerts) {
    if (!alerts.length) {
      els.alertsList.innerHTML = `<li class="empty">No open alerts.</li>`;
      return;
    }

    els.alertsList.innerHTML = alerts
      .map(
        (alert) => `
        <li class="alert-item">
          <strong>${escapeHtml(alert.summary)}</strong>
          <span>${escapeHtml(alert.apiName)} · opened ${escapeHtml(formatTime(alert.openedAt))}</span>
          ${alert.jiraIssueKey
            ? `<span>Jira ${
                alert.jiraIssueUrl
                  ? `<a href="${escapeHtml(alert.jiraIssueUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(alert.jiraIssueKey)}</a>`
                  : escapeHtml(alert.jiraIssueKey)
              }</span>`
            : ""}
        </li>`
      )
      .join("");
  }

  function renderChecks(apiName, page, summary) {
    const rows = page?.content || [];
    els.detailPanel.hidden = false;
    els.detailSub.textContent = `${apiName} · last ${summary?.windowHours || 24}h`;
    if (summary) {
      els.detailSummary.hidden = false;
      els.detailSummary.innerHTML = `
        <span><strong>${escapeHtml(formatPercent(summary.uptimePercent))}</strong> uptime</span>
        <span><strong>${escapeHtml(formatLatency(summary.avgLatencyMs))}</strong> avg</span>
        <span><strong>${summary.totalChecks ?? 0}</strong> checks</span>
        <span><strong>${summary.failedChecks ?? 0}</strong> failed</span>
        <span>min/max <strong>${escapeHtml(formatLatency(summary.minLatencyMs))} / ${escapeHtml(formatLatency(summary.maxLatencyMs))}</strong></span>
      `;
    } else {
      els.detailSummary.hidden = true;
      els.detailSummary.innerHTML = "";
    }
    if (!rows.length) {
      els.checksBody.innerHTML = `<tr><td colspan="7" class="empty">No checks recorded yet.</td></tr>`;
      return;
    }
    els.checksBody.innerHTML = rows
      .map(
        (row) => `
        <tr>
          <td>${escapeHtml(formatTime(row.checkedAt))}</td>
          <td>${row.success ? "yes" : "no"}</td>
          <td>${escapeHtml(row.apiStatus || "—")}</td>
          <td>${row.httpStatus ?? "—"}</td>
          <td>${row.latencyMs ?? "—"} ms</td>
          <td>${row.timedOut ? "yes" : "no"}</td>
          <td class="endpoint">${escapeHtml(row.errorMessage || "—")}</td>
        </tr>`
      )
      .join("");
  }

  function updateMetrics(apis, alerts, fleet) {
    els.metricTotal.textContent = String(apis.length);
    els.metricUp.textContent = String(apis.filter((a) => a.currentStatus === "UP").length);
    els.metricDegraded.textContent = String(apis.filter((a) => a.currentStatus === "DEGRADED").length);
    els.metricDown.textContent = String(apis.filter((a) => a.currentStatus === "DOWN").length);
    els.metricAlerts.textContent = String(alerts.length);
    els.metricUptime.textContent = formatPercent(fleet?.fleetUptimePercent);
    els.metricLatency.textContent = formatLatency(fleet?.fleetAvgLatencyMs);
  }

  async function refresh() {
    if (!isLoggedIn()) {
      showBanner("Login with admin/admin (or set an API key under Advanced)");
      els.apisBody.innerHTML = `<tr><td colspan="7" class="empty">Sign in to load monitored APIs.</td></tr>`;
      els.alertsList.innerHTML = `<li class="empty">Sign in to load alerts.</li>`;
      return;
    }

    try {
      const [apis, alerts, fleet] = await Promise.all([
        api("/api/v1/monitored-apis"),
        api("/api/v1/alerts?status=OPEN"),
        api("/api/v1/summary?hours=24"),
      ]);
      const summaryById = new Map((fleet.apis || []).map((item) => [item.apiId, item]));
      renderApis(apis, summaryById);
      renderAlerts(alerts);
      updateMetrics(apis, alerts, fleet);
      showBanner("");
      els.lastRefresh.textContent = `Updated ${new Date().toLocaleTimeString()}`;

      if (selectedApiId) {
        const selected = apis.find((item) => item.id === selectedApiId);
        if (selected) {
          const [history, summary] = await Promise.all([
            api(`/api/v1/monitored-apis/${selectedApiId}/checks?size=20`),
            api(`/api/v1/monitored-apis/${selectedApiId}/summary?hours=24`),
          ]);
          renderChecks(selected.name, history, summary);
        }
      }
    } catch (error) {
      showBanner(error.message || "Failed to load data");
      els.apisBody.innerHTML = `<tr><td colspan="7" class="empty">Unable to load APIs.</td></tr>`;
      els.alertsList.innerHTML = `<li class="empty">Unable to load alerts.</li>`;
    }
  }

  async function login() {
    try {
      const data = await fetch("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          username: els.loginUsername.value.trim(),
          password: els.loginPassword.value,
        }),
      }).then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) {
          throw new Error(body.message || "Login failed");
        }
        return body;
      });

      sessionStorage.setItem(TOKEN_STORAGE, data.accessToken);
      sessionStorage.setItem(USER_STORAGE, data.username || els.loginUsername.value.trim());
      els.loginPassword.value = "";
      updateAuthUi();
      showBanner("Logged in", true);
      await refresh();
    } catch (error) {
      showBanner(error.message || "Login failed");
    }
  }

  function logout() {
    sessionStorage.removeItem(TOKEN_STORAGE);
    sessionStorage.removeItem(USER_STORAGE);
    updateAuthUi();
    showBanner("Logged out");
    refresh();
  }

  els.apisBody.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const id = button.getAttribute("data-id");
    const action = button.getAttribute("data-action");

    try {
      if (action === "check") {
        await api(`/api/v1/monitored-apis/${id}/check-now`, { method: "POST" });
        showBanner("Check completed", true);
        await refresh();
      } else if (action === "disable") {
        await api(`/api/v1/monitored-apis/${id}`, { method: "DELETE" });
        showBanner("API disabled", true);
        await refresh();
      } else if (action === "history") {
        selectedApiId = id;
        const [history, summary] = await Promise.all([
          api(`/api/v1/monitored-apis/${id}/checks?size=20`),
          api(`/api/v1/monitored-apis/${id}/summary?hours=24`),
        ]);
        renderChecks(button.getAttribute("data-name") || "API", history, summary);
      }
    } catch (error) {
      showBanner(error.message || "Action failed");
    }
  });

  els.createForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(els.createForm);
    const latencyRaw = form.get("latencyThresholdMs");
    const payload = {
      name: form.get("name"),
      baseUrl: form.get("baseUrl"),
      path: form.get("path") || "/health",
      httpMethod: form.get("httpMethod"),
      expectedStatusCode: Number(form.get("expectedStatusCode")),
      timeoutMs: Number(form.get("timeoutMs")),
      intervalSeconds: Number(form.get("intervalSeconds")),
      latencyThresholdMs: latencyRaw ? Number(latencyRaw) : null,
      ownerEmail: form.get("ownerEmail") || null,
      enabled: true,
    };

    try {
      await api("/api/v1/monitored-apis", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      els.createForm.reset();
      els.createForm.elements.path.value = "/health";
      els.createForm.elements.expectedStatusCode.value = "200";
      els.createForm.elements.timeoutMs.value = "3000";
      els.createForm.elements.intervalSeconds.value = "60";
      els.createForm.elements.httpMethod.value = "GET";
      showBanner("API registered", true);
      await refresh();
    } catch (error) {
      showBanner(error.message || "Create failed");
    }
  });

  els.saveKeyBtn.addEventListener("click", () => {
    sessionStorage.setItem(KEY_STORAGE, els.apiKey.value.trim());
    showBanner("API key saved for this browser session", true);
    updateAuthUi();
    refresh();
  });

  els.loginBtn.addEventListener("click", () => login());
  els.loginPassword.addEventListener("keydown", (event) => {
    if (event.key === "Enter") login();
  });
  els.logoutBtn.addEventListener("click", () => logout());
  els.refreshBtn.addEventListener("click", () => refresh());

  els.apiKey.value = sessionStorage.getItem(KEY_STORAGE) || "";
  updateAuthUi();
  refresh();
  refreshTimer = setInterval(refresh, REFRESH_MS);
})();
