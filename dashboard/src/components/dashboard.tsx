"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ApiRequestError, apiRequest, dashboardApiPath } from "@/lib/client-api";

type Health = {
  status: string;
  checkedAt: string;
  uptimeSeconds: number;
  databaseStatus: string;
  storageStatus: string;
};
type Metrics = {
  totalApplications: number;
  activeApplications: number;
  activeFiles: number;
  storageBytes: number;
  publicFiles: number;
  privateFiles: number;
};
type DailyMetric = {
  day: string;
  uploads: number;
  downloads: number;
  deletions: number;
  applicationChanges: number;
};
type Activity = {
  id: string;
  eventType: string;
  applicationCode: string | null;
  resourceId: string | null;
  details: string;
  occurredAt: string;
};
type DashboardFile = {
  id: string;
  applicationCode: string;
  applicationName: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  checksumSha256: string;
  visibility: "PUBLIC" | "PRIVATE";
  folder: string | null;
  uploadedBy: string | null;
  createdAt: string;
};
type DashboardFilePage = {
  items: DashboardFile[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};
type ClientApplication = {
  id: string;
  code: string;
  name: string;
  active: boolean;
  allowedOrigins: string[];
  maxFileSizeBytes: number | null;
  allowedContentTypes: string[];
  storageQuotaBytes: number | null;
  apiKeyPrefix: string;
  createdAt: string;
  updatedAt: string;
};
type CreatedApplication = { application: ClientApplication; apiKey: string };
type AppForm = {
  code: string;
  name: string;
  allowedOrigins: string;
  maxFileSizeMb: string;
  storageQuotaMb: string;
  allowedContentTypes: string;
  active: boolean;
};
type Config = {
  apiConfigured: boolean;
  apiUrl: string;
  publicUrl: string;
  sessionSecretConfigured: boolean;
  sessionCookieSecure: boolean;
  eventRetentionDays: number;
};
type AuthState = "checking" | "anonymous" | "authenticated" | "unavailable";
type Tab = "overview" | "applications" | "files" | "configuration";

const EMPTY_FORM: AppForm = {
  code: "",
  name: "",
  allowedOrigins: "",
  maxFileSizeMb: "",
  storageQuotaMb: "",
  allowedContentTypes: "",
  active: true,
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toLocaleString("pt-PT", { maximumFractionDigits: 1 })} ${units[unit]}`;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("pt-PT", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return days > 0 ? `${days}d ${hours}h ${minutes}m` : `${hours}h ${minutes}m`;
}

function eventLabel(event: string): string {
  const labels: Record<string, string> = {
    APP_CREATED: "Aplicação criada",
    APP_UPDATED: "Aplicação actualizada",
    API_KEY_ROTATED: "Chave rodada",
    FILE_UPLOADED: "Ficheiro enviado",
    FILE_DOWNLOADED: "Ficheiro consultado",
    FILE_DELETED: "Ficheiro eliminado",
  };
  return labels[event] ?? event;
}

function initialForm(application?: ClientApplication): AppForm {
  if (!application) return EMPTY_FORM;
  return {
    code: application.code,
    name: application.name,
    allowedOrigins: application.allowedOrigins.join("\n"),
    maxFileSizeMb: application.maxFileSizeBytes
      ? String(Math.round((application.maxFileSizeBytes / 1024 / 1024) * 100) / 100)
      : "",
    storageQuotaMb: application.storageQuotaBytes
      ? String(Math.round((application.storageQuotaBytes / 1024 / 1024) * 100) / 100)
      : "",
    allowedContentTypes: application.allowedContentTypes.join(", "),
    active: application.active,
  };
}

export default function Dashboard() {
  const [auth, setAuth] = useState<AuthState>("checking");
  const [tab, setTab] = useState<Tab>("overview");
  const [health, setHealth] = useState<Health | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [daily, setDaily] = useState<DailyMetric[]>([]);
  const [activity, setActivity] = useState<Activity[]>([]);
  const [applications, setApplications] = useState<ClientApplication[]>([]);
  const [files, setFiles] = useState<DashboardFilePage | null>(null);
  const [fileSearch, setFileSearch] = useState("");
  const [fileQuery, setFileQuery] = useState("");
  const [fileAppFilter, setFileAppFilter] = useState("");
  const [fileVisibility, setFileVisibility] = useState("");
  const [filePageNumber, setFilePageNumber] = useState(0);
  const [filesLoading, setFilesLoading] = useState(false);
  const [config, setConfig] = useState<Config | null>(null);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [keyInput, setKeyInput] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ClientApplication | null>(null);
  const [form, setForm] = useState<AppForm>(EMPTY_FORM);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [revealedKey, setRevealedKey] = useState("");
  const [revealedFor, setRevealedFor] = useState("");
  const [copied, setCopied] = useState(false);

  const loadDashboard = useCallback(async () => {
    setRefreshing(true);
    const results = await Promise.allSettled([
      apiRequest<Health>("/admin/dashboard/health"),
      apiRequest<Metrics>("/admin/dashboard/metrics"),
      apiRequest<DailyMetric[]>("/admin/dashboard/daily?days=14"),
      apiRequest<Activity[]>("/admin/dashboard/activity?limit=12"),
    ]);

    const [healthResult, metricsResult, dailyResult, activityResult] = results;
    if (healthResult.status === "fulfilled") setHealth(healthResult.value);
    if (metricsResult.status === "fulfilled") setMetrics(metricsResult.value);
    if (dailyResult.status === "fulfilled") setDaily(dailyResult.value);
    if (activityResult.status === "fulfilled") setActivity(activityResult.value);

    const failures = results.filter(
      (result): result is PromiseRejectedResult => result.status === "rejected",
    );
    if (failures.length === 0) {
      setError("");
    } else {
      const sessionExpired = failures.some(
        ({ reason }) => reason instanceof Error && reason.message.includes("Sessão expirada"),
      );
      if (sessionExpired) {
        setAuth("anonymous");
      } else if (failures.some(({ reason }) => reason instanceof ApiRequestError && reason.status === 404)) {
        setError(
          "A API Java em produção ainda não tem as rotas de monitorização do painel. " +
            "Publique a versão actualizada do Serviço de Ficheiros para activar saúde, métricas e actividade.",
        );
      } else {
        const reason = failures[0].reason;
        setError(reason instanceof Error ? reason.message : "Falha ao actualizar o painel.");
      }
    }
    setRefreshing(false);
  }, []);

  const loadApplications = useCallback(async () => {
    try {
      const result = await apiRequest<ClientApplication[]>("/admin/applications");
      setApplications(result);
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar as aplicações.");
    }
  }, []);

  const loadFiles = useCallback(async () => {
    setFilesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(filePageNumber), size: "20" });
      if (fileQuery.trim()) params.set("q", fileQuery.trim());
      if (fileAppFilter) params.set("applicationCode", fileAppFilter);
      if (fileVisibility) params.set("visibility", fileVisibility);
      setFiles(await apiRequest<DashboardFilePage>(`/admin/dashboard/files?${params}`));
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar os ficheiros.");
    } finally {
      setFilesLoading(false);
    }
  }, [fileAppFilter, filePageNumber, fileQuery, fileVisibility]);

  useEffect(() => {
    let active = true;
    apiRequest<ClientApplication[]>("/admin/applications")
      .then((result) => {
        if (active) {
          setApplications(result);
          setAuth("authenticated");
        }
      })
      .catch((reason: unknown) => {
        if (!active) return;
        const message = reason instanceof Error ? reason.message : "";
        if (message.includes("Sessão expirada")) {
          setAuth("anonymous");
        } else {
          setError(message || "Não foi possível contactar o serviço.");
          setAuth("unavailable");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (auth !== "authenticated") return;
    void loadDashboard();
    const timer = window.setInterval(() => void loadDashboard(), 20_000);
    return () => window.clearInterval(timer);
  }, [auth, loadDashboard]);

  useEffect(() => {
    if (auth === "authenticated" && (tab === "applications" || tab === "files")) void loadApplications();
    if (auth === "authenticated" && tab === "configuration") {
      apiRequest<Config>("/config").then(setConfig).catch((reason: unknown) => {
        setError(reason instanceof Error ? reason.message : "Falha ao ler a configuração.");
      });
    }
  }, [auth, tab, loadApplications]);

  useEffect(() => {
    if (auth === "authenticated" && tab === "files") void loadFiles();
  }, [auth, tab, filePageNumber, fileQuery, fileAppFilter, fileVisibility, loadFiles]);

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    try {
      await apiRequest<{ authenticated: boolean }>("/login", {
        method: "POST",
        body: JSON.stringify({ adminKey: keyInput }),
      });
      setKeyInput("");
      setAuth("authenticated");
      setTab("overview");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível iniciar sessão.");
    }
  }

  async function signOut() {
    await fetch(dashboardApiPath("/logout"), { method: "POST" });
    setAuth("anonymous");
    setHealth(null);
    setMetrics(null);
    setDaily([]);
    setActivity([]);
    setApplications([]);
  }

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError("");
    setModalOpen(true);
  }

  function openEdit(application: ClientApplication) {
    setEditing(application);
    setForm(initialForm(application));
    setFormError("");
    setModalOpen(true);
  }

  async function saveApplication(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setFormError("");
    const toBytes = (mb: string) => (mb.trim() ? Math.round(Number(mb) * 1024 * 1024) : null);
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      allowedOrigins: form.allowedOrigins.split("\n").map((item) => item.trim()).filter(Boolean),
      maxFileSizeBytes: toBytes(form.maxFileSizeMb),
      storageQuotaBytes: toBytes(form.storageQuotaMb),
      allowedContentTypes: form.allowedContentTypes.split(",").map((item) => item.trim()).filter(Boolean),
      active: form.active,
    };
    try {
      const endpoint = editing
        ? `/admin/applications/${encodeURIComponent(editing.code)}`
        : "/admin/applications";
      const result = await apiRequest<ClientApplication | CreatedApplication>(endpoint, {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify(payload),
      });
      if ("apiKey" in result) {
        setRevealedKey(result.apiKey);
        setRevealedFor(result.application.name);
      }
      setModalOpen(false);
      await loadApplications();
      await loadDashboard();
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : "Não foi possível guardar a aplicação.");
    } finally {
      setSaving(false);
    }
  }

  async function rotateKey(application: ClientApplication) {
    if (!window.confirm(`Rodar a chave de "${application.name}"? A chave actual deixará de funcionar imediatamente.`)) {
      return;
    }
    try {
      const result = await apiRequest<CreatedApplication>(
        `/admin/applications/${encodeURIComponent(application.code)}/rotate-key`,
        { method: "POST", body: "{}" },
      );
      setRevealedKey(result.apiKey);
      setRevealedFor(result.application.name);
      await loadApplications();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível rodar a chave.");
    }
  }

  async function copyRevealedKey() {
    await navigator.clipboard.writeText(revealedKey);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  const maxActivity = useMemo(
    () => Math.max(1, ...daily.flatMap((item) => [item.uploads, item.downloads, item.deletions])),
    [daily],
  );

  if (auth === "checking") {
    return <main className="loading-screen"><span className="spinner" />A verificar a sessão…</main>;
  }

  if (auth === "anonymous" || auth === "unavailable") {
    return (
      <main className="login-page">
        <div className="login-decoration" aria-hidden="true"><i /><i /><i /><i /></div>
        <section className="login-card">
          <div className="brand-mark">F<span>.</span></div>
          <p className="eyebrow">PAINEL DE OPERAÇÕES</p>
          <h1>Serviço de ficheiros</h1>
          <p className="login-copy">Administre aplicações, acompanhe actividade e verifique a saúde do serviço.</p>
          {auth === "unavailable" ? (
            <div className="alert error-alert">
              <span>{error || "Serviço indisponível."}</span>
              <button className="text-button" onClick={() => window.location.reload()}>Tentar novamente</button>
            </div>
          ) : (
            <form className="login-form" onSubmit={signIn}>
              <label htmlFor="admin-key">Chave de administração</label>
              <div className="password-wrap">
                <input
                  autoComplete="current-password"
                  id="admin-key"
                  onChange={(event) => setKeyInput(event.target.value)}
                  placeholder="Introduza a chave configurada no servidor"
                  required
                  type={showKey ? "text" : "password"}
                  value={keyInput}
                />
                <button className="password-toggle" onClick={() => setShowKey(!showKey)} type="button">
                  {showKey ? "Ocultar" : "Mostrar"}
                </button>
              </div>
              {error && <p className="form-error">{error}</p>}
              <button className="primary-button full-button" type="submit">Entrar no painel <span>→</span></button>
            </form>
          )}
          <p className="login-footnote"><span className="lock-icon">●</span> A chave é cifrada no servidor e não fica guardada no browser.</p>
        </section>
        <footer className="login-footer">Ficheiros seguros. Operação sob controlo.</footer>
      </main>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark small-mark">F<span>.</span></div>
          <div><strong>fileservice</strong><small>CONTROL CENTER</small></div>
        </div>
        <div className="nav-caption">ESPAÇO DE TRABALHO</div>
        <nav className="main-nav" aria-label="Navegação principal">
          <button className={tab === "overview" ? "nav-item active" : "nav-item"} onClick={() => setTab("overview")}>
            <span className="nav-icon">◫</span> Visão geral
          </button>
          <button className={tab === "applications" ? "nav-item active" : "nav-item"} onClick={() => setTab("applications")}>
            <span className="nav-icon">▤</span> Aplicações
            {metrics && <span className="nav-count">{metrics.totalApplications}</span>}
          </button>
          <button className={tab === "files" ? "nav-item active" : "nav-item"} onClick={() => setTab("files")}>
            <span className="nav-icon">▧</span> Ficheiros
            {metrics && <span className="nav-count">{metrics.activeFiles}</span>}
          </button>
          <button className={tab === "configuration" ? "nav-item active" : "nav-item"} onClick={() => setTab("configuration")}>
            <span className="nav-icon">⚙</span> Configuração
          </button>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-service">
            <span className={health?.status === "UP" ? "status-dot" : "status-dot down"} />
            <div><strong>{health?.status === "UP" ? "Serviço operacional" : "A verificar serviço"}</strong><small>Monitorização activa</small></div>
          </div>
          <button className="nav-item logout-button" onClick={() => void signOut()}>
            <span className="nav-icon">↪</span> Terminar sessão
          </button>
          <div className="sidebar-version">Ficheiros · Administração</div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumb">Serviço de ficheiros <span>/</span> <strong>{tab === "overview" ? "Visão geral" : tab === "applications" ? "Aplicações" : tab === "files" ? "Ficheiros" : "Configuração"}</strong></div>
          <div className="topbar-right">
            <span className="secure-label"><span>●</span> Sessão segura</span>
            <button className="avatar" title="Terminar sessão" onClick={() => void signOut()}>AD</button>
          </div>
        </header>

        <div className="page-content">
          {error && <div className="inline-alert"><span>{error}</span><button onClick={() => setError("")} aria-label="Fechar aviso">×</button></div>}

          {tab === "overview" && (
            <>
              <section className="page-heading">
                <div>
                  <p className="eyebrow">CENTRO DE OPERAÇÕES</p>
                  <h1>Visão geral</h1>
                  <p className="muted">Acompanhe a actividade e a saúde do seu serviço.</p>
                </div>
                <button className="secondary-button" disabled={refreshing} onClick={() => void loadDashboard()}>
                  <span className={refreshing ? "refresh-icon spinning" : "refresh-icon"}>↻</span>
                  {refreshing ? "A actualizar" : "Actualizar"}
                </button>
              </section>

              <section className="metric-grid">
                <article className="metric-card health-card">
                  <div className="metric-top"><span className="metric-icon green-icon">⌁</span><span className="metric-tag">AO VIVO</span></div>
                  <p className="metric-label">Estado do serviço</p>
                  <div className="metric-value health-value"><span className={health?.status === "UP" ? "status-dot large-dot" : "status-dot down large-dot"} />{health?.status === "UP" ? "Operacional" : health?.status ?? "Desconhecido"}</div>
                  <p className="metric-foot">{health ? `Disponível · uptime ${formatDuration(health.uptimeSeconds)}` : "A aguardar monitorização"}</p>
                </article>
                <MetricCard icon="▤" iconClass="blue-icon" label="Aplicações registadas" value={metrics?.totalApplications ?? "—"} foot={`${metrics?.activeApplications ?? 0} activas`} />
                <MetricCard icon="▧" iconClass="violet-icon" label="Ficheiros activos" value={metrics?.activeFiles ?? "—"} foot={`${metrics?.publicFiles ?? 0} públicos · ${metrics?.privateFiles ?? 0} privados`} />
                <MetricCard icon="◒" iconClass="orange-icon" label="Armazenamento usado" value={metrics ? formatBytes(metrics.storageBytes) : "—"} foot="Soma dos ficheiros activos" />
              </section>

              <section className="content-grid">
                <article className="panel chart-panel">
                  <div className="panel-heading">
                    <div><h2>Actividade do serviço</h2><p>Operações registadas · últimos 14 dias</p></div>
                    <span className="live-pill"><i /> ACTUALIZAÇÃO AUTOMÁTICA</span>
                  </div>
                  <div className="chart-legend">
                    <span><i className="legend-upload" />Uploads</span>
                    <span><i className="legend-download" />Consultas</span>
                    <span><i className="legend-delete" />Eliminações</span>
                  </div>
                  <div className="bar-chart" role="img" aria-label="Gráfico diário de uploads, consultas e eliminações">
                    {daily.length === 0 ? <div className="empty-chart">Ainda não existem eventos para apresentar.</div> : daily.map((item) => {
                      const total = item.uploads + item.downloads + item.deletions;
                      return (
                        <div className="bar-column" key={item.day} title={`${item.day}: ${item.uploads} uploads, ${item.downloads} consultas, ${item.deletions} eliminações`}>
                          <div className="bar-stack">
                            <i className="bar-upload" style={{ height: `${Math.max(item.uploads ? 3 : 0, (item.uploads / maxActivity) * 100)}%` }} />
                            <i className="bar-download" style={{ height: `${Math.max(item.downloads ? 3 : 0, (item.downloads / maxActivity) * 100)}%` }} />
                            <i className="bar-delete" style={{ height: `${Math.max(item.deletions ? 3 : 0, (item.deletions / maxActivity) * 100)}%` }} />
                          </div>
                          <span>{new Intl.DateTimeFormat("pt-PT", { day: "2-digit", month: "2-digit" }).format(new Date(`${item.day}T12:00:00`))}</span>
                          <small>{total}</small>
                        </div>
                      );
                    })}
                  </div>
                  <div className="chart-baseline" />
                </article>

                <article className="panel monitor-panel">
                  <div className="panel-heading"><div><h2>Monitorização</h2><p>Estado verificado automaticamente</p></div><span className="pulse-ring"><i /></span></div>
                  <div className={health?.status === "UP" ? "monitor-status is-up" : "monitor-status is-down"}>
                    <span className={health?.status === "UP" ? "status-dot" : "status-dot down"} />
                    <div><strong>{health?.status === "UP" ? "Tudo a funcionar" : "Estado indisponível"}</strong><small>{health?.status ?? "Sem resposta do serviço"}</small></div>
                  </div>
                  <div className="monitor-details">
                    <div><span>Base de dados</span><strong className={health?.databaseStatus === "UP" ? "healthy-text" : "unhealthy-text"}>{health?.databaseStatus ?? "—"}</strong></div>
                    <div><span>Armazenamento</span><strong className={health?.storageStatus === "UP" ? "healthy-text" : "unhealthy-text"}>{health?.storageStatus ?? "—"}</strong></div>
                    <div><span>Tempo activo</span><strong>{health ? formatDuration(health.uptimeSeconds) : "—"}</strong></div>
                    <div><span>Última verificação</span><strong>{health ? formatDate(health.checkedAt) : "—"}</strong></div>
                    <div><span>Frequência</span><strong>20 segundos</strong></div>
                  </div>
                  <div className="monitor-note"><span>i</span> Saúde geral do Spring Actuator. Os detalhes internos não são expostos publicamente.</div>
                </article>
              </section>

              <section className="panel activity-panel">
                <div className="panel-heading">
                  <div><h2>Actividade recente</h2><p>Eventos operacionais · retenção de 90 dias</p></div>
                  <button className="link-button" onClick={() => setTab("applications")}>Gerir aplicações <span>→</span></button>
                </div>
                <ActivityTable events={activity} />
              </section>
              <p className="data-note">Os registos apresentados são eventos de negócio (uploads, consultas, eliminações e alterações administrativas), não logs do sistema operativo.</p>
            </>
          )}

          {tab === "applications" && (
            <>
              <section className="page-heading">
                <div><p className="eyebrow">ACESSO E INTEGRAÇÕES</p><h1>Aplicações</h1><p className="muted">Configure as aplicações autorizadas a usar o serviço.</p></div>
                <button className="primary-button" onClick={openCreate}><span>＋</span> Nova aplicação</button>
              </section>
              <div className="apps-summary">
                <div><strong>{applications.length}</strong><span>total</span></div>
                <div><strong>{applications.filter((app) => app.active).length}</strong><span>activas</span></div>
                <div><strong>{applications.filter((app) => !app.active).length}</strong><span>desactivadas</span></div>
                <p>As chaves são apresentadas uma única vez, na criação ou rotação.</p>
              </div>
              <section className="panel applications-panel">
                {applications.length === 0 ? (
                  <div className="empty-state"><div className="empty-icon">▤</div><h2>Sem aplicações registadas</h2><p>Crie uma aplicação para lhe atribuir credenciais de acesso ao serviço.</p><button className="primary-button" onClick={openCreate}>Criar primeira aplicação</button></div>
                ) : (
                  <div className="application-list">
                    {applications.map((application) => (
                      <article className="application-row" key={application.id}>
                        <div className="app-avatar">{application.name.slice(0, 1).toUpperCase()}</div>
                        <div className="app-main">
                          <div className="app-title"><h2>{application.name}</h2><span className={application.active ? "state-pill active-pill" : "state-pill disabled-pill"}><i />{application.active ? "Activa" : "Desactivada"}</span></div>
                          <p><code>{application.code}</code><span className="app-divider">·</span><span>Chave <code>{application.apiKeyPrefix}••••••</code></span></p>
                          <div className="app-meta">
                            <span>{application.allowedOrigins.length} origens permitidas</span>
                            <span>{application.maxFileSizeBytes ? `Limite ${formatBytes(application.maxFileSizeBytes)}` : "Limite global"}</span>
                            <span>Actualizada {formatDate(application.updatedAt)}</span>
                          </div>
                        </div>
                        <div className="app-actions">
                          <button className="secondary-button compact" onClick={() => openEdit(application)}>Editar</button>
                          <button className="icon-action" title="Rodar chave da API" onClick={() => void rotateKey(application)}>⟳</button>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </section>
              <div className="security-callout"><span className="callout-icon">⌑</span><p><strong>Proteja as credenciais.</strong> Não inclua chaves de API no browser nem em repositórios. Use o backend da aplicação e links temporários assinados para uploads feitos pelo browser.</p><button className="link-button" onClick={() => setTab("configuration")}>Ver recomendações <span>→</span></button></div>
            </>
          )}

          {tab === "files" && (
            <>
              <section className="page-heading">
                <div><p className="eyebrow">ARMAZENAMENTO</p><h1>Ficheiros</h1><p className="muted">Pesquise, visualize e descarregue ficheiros guardados no serviço.</p></div>
                <button className="secondary-button" disabled={filesLoading} onClick={() => void loadFiles()}><span className={filesLoading ? "refresh-icon spinning" : "refresh-icon"}>↻</span> Actualizar</button>
              </section>
              <form className="file-filters" onSubmit={(event) => { event.preventDefault(); setFilePageNumber(0); setFileQuery(fileSearch.trim()); }}>
                <label className="file-search"><span>⌕</span><input aria-label="Pesquisar ficheiros" placeholder="Pesquisar por nome, pasta ou aplicação…" value={fileSearch} onChange={(event) => setFileSearch(event.target.value)} /></label>
                <select aria-label="Filtrar por aplicação" value={fileAppFilter} onChange={(event) => { setFileAppFilter(event.target.value); setFilePageNumber(0); }}>
                  <option value="">Todas as aplicações</option>
                  {applications.map((application) => <option key={application.code} value={application.code}>{application.name}</option>)}
                </select>
                <select aria-label="Filtrar por visibilidade" value={fileVisibility} onChange={(event) => { setFileVisibility(event.target.value); setFilePageNumber(0); }}>
                  <option value="">Todas as visibilidades</option><option value="PRIVATE">Privados</option><option value="PUBLIC">Públicos</option>
                </select>
                <button className="primary-button" type="submit">Pesquisar</button>
              </form>
              <div className="files-summary">
                <div><strong>{files?.totalItems ?? "—"}</strong><span>ficheiros encontrados</span></div>
                <div><strong>{formatBytes(metrics?.storageBytes ?? 0)}</strong><span>espaço ocupado</span></div>
                <p>Conteúdo servido apenas após autenticação administrativa.</p>
              </div>
              <section className="panel files-panel">
                {filesLoading && !files ? <div className="empty-state"><span className="spinner" />A carregar ficheiros…</div> :
                  files?.items.length ? (
                    <div className="table-scroll">
                      <table className="files-table">
                        <thead><tr><th>FICHEIRO</th><th>APLICAÇÃO</th><th>VISIBILIDADE</th><th>TAMANHO</th><th>DATA</th><th>ACÇÕES</th></tr></thead>
                        <tbody>{files.items.map((file) => {
                          const contentPath = dashboardApiPath(`/admin/dashboard/files/${file.id}/content`);
                          return <tr key={file.id}>
                            <td><div className="file-name-cell"><span className="file-type-icon">{file.contentType.startsWith("image/") ? "▧" : file.contentType === "application/pdf" ? "▤" : "▱"}</span><div><strong title={file.filename}>{file.filename}</strong><small>{file.contentType}{file.folder ? ` · ${file.folder}` : ""}</small></div></div></td>
                            <td><strong className="file-app-name">{file.applicationName}</strong><small className="file-app-code">{file.applicationCode}</small></td>
                            <td><span className={file.visibility === "PUBLIC" ? "visibility-pill public-pill" : "visibility-pill private-pill"}>{file.visibility === "PUBLIC" ? "Público" : "Privado"}</span></td>
                            <td className="file-size">{formatBytes(file.sizeBytes)}</td>
                            <td className="file-date">{formatDate(file.createdAt)}</td>
                            <td><div className="file-actions"><a className="file-action" href={contentPath} target="_blank" rel="noreferrer" title="Visualizar ficheiro" aria-label={`Visualizar ${file.filename}`}>Ver</a><a className="file-action download-action" href={`${contentPath}?download=true`} title="Descarregar ficheiro" aria-label={`Descarregar ${file.filename}`}>↓</a></div></td>
                          </tr>;
                        })}</tbody>
                      </table>
                    </div>
                  ) : <div className="empty-state"><div className="empty-icon">▧</div><h2>{filesLoading ? "A carregar ficheiros…" : "Nenhum ficheiro encontrado"}</h2><p>{fileQuery || fileAppFilter || fileVisibility ? "Altere os filtros ou a pesquisa para ver outros resultados." : "Os ficheiros carregados pelas aplicações aparecerão aqui."}</p></div>}
                {files && files.totalPages > 1 && <div className="pagination"><span>Página {files.page + 1} de {files.totalPages} · {files.totalItems} ficheiros</span><div><button className="secondary-button compact" disabled={filePageNumber === 0 || filesLoading} onClick={() => setFilePageNumber((current) => Math.max(0, current - 1))}>← Anterior</button><button className="secondary-button compact" disabled={filePageNumber + 1 >= files.totalPages || filesLoading} onClick={() => setFilePageNumber((current) => current + 1)}>Seguinte →</button></div></div>}
              </section>
              <div className="security-callout"><span className="callout-icon">⌑</span><p><strong>Acesso administrativo.</strong> Esta lista pode incluir metadados e conteúdo de ficheiros privados. Proteja a chave de administração e limite o acesso ao painel a utilizadores autorizados.</p></div>
            </>
          )}

          {tab === "configuration" && (
            <>
              <section className="page-heading">
                <div><p className="eyebrow">AMBIENTE E DIAGNÓSTICO</p><h1>Configuração</h1><p className="muted">Verifique a ligação e consulte as definições recomendadas.</p></div>
                <button className="secondary-button" onClick={() => { setConfig(null); apiRequest<Config>("/config").then(setConfig).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Falha.")); }}>↻ Actualizar</button>
              </section>
              <section className="config-checks">
                <ConfigCheck title="Ligação à API" ok={config?.apiConfigured ?? false} detail={config?.apiConfigured ? `Endpoint usado pelo servidor Next.js: ${config.apiUrl}` : "Defina FILESERVICE_API_URL no ambiente do painel."} />
                <ConfigCheck title="Sessão cifrada" ok={config?.sessionSecretConfigured ?? false} detail={config?.sessionSecretConfigured ? "Segredo de sessão válido (mínimo 32 caracteres)." : "Defina DASHBOARD_SESSION_SECRET com um segredo aleatório."} />
                <ConfigCheck title="Ligação ao serviço" ok={health?.status === "UP"} detail={health?.status === "UP" ? `Estado ${health.status} · ${health ? formatDate(health.checkedAt) : ""}` : "O endpoint de monitorização não confirmou estado saudável."} />
              </section>
              <section className="config-grid">
                <article className="panel config-panel">
                  <div className="panel-heading"><div><h2>Variáveis do painel</h2><p>Configurar no ficheiro .env.local do Next.js</p></div><span className="config-symbol">⌘</span></div>
                  <div className="env-list">
                    <EnvRow name="FILESERVICE_API_URL" value={config?.apiUrl ?? "https://portal.pumangol.co.ao/ficheiros"} description="API de produção; as chamadas são feitas pelo servidor Next.js, nunca pelo browser." />
                    <EnvRow name="FILESERVICE_PUBLIC_URL" value={config?.publicUrl || "Não definida"} description="URL pública usada pelas aplicações cliente." />
                    <EnvRow name="DASHBOARD_SESSION_SECRET" value="••••••••••••••••••••••••••••••••" description="Segredo aleatório para cifrar o cookie de sessão (32+ caracteres)." />
                  </div>
                </article>
                <article className="panel config-panel">
                  <div className="panel-heading"><div><h2>Proxy reverso Nginx</h2><p>Exemplo para publicar o painel em /gestao-ficheiros/</p></div><span className="config-symbol">⌁</span></div>
                  <pre className="code-block">{`location /gestao-ficheiros/ {
    proxy_pass http://127.0.0.1:3000/gestao-ficheiros/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}`}</pre>
                  <p className="config-footnote">Defina <code>FILESERVICE_API_URL=https://portal.pumangol.co.ao/ficheiros</code> para usar a API de produção. Se Next.js e Java estiverem no mesmo servidor, pode usar <code>http://127.0.0.1:18085</code> como optimização interna.</p>
                </article>
              </section>
              <div className="security-callout config-warning"><span className="callout-icon">!</span><p><strong>Configuração do servidor não é alterada pelo painel.</strong> Variáveis do systemd, PostgreSQL, Nginx, permissões de disco e logs do Linux devem ser alterados no servidor por um administrador. O painel apenas administra as aplicações registadas e apresenta eventos de actividade da API.</p></div>
              <section className="panel retention-panel"><div className="retention-icon">◷</div><div><h2>Retenção dos eventos</h2><p>Os eventos de actividade são guardados no PostgreSQL e removidos automaticamente após {config?.eventRetentionDays ?? 90} dias.</p></div><span className="retention-badge">90 DIAS</span></section>
            </>
          )}
        </div>
      </main>

      {modalOpen && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) setModalOpen(false); }}>
          <section aria-labelledby="app-form-title" aria-modal="true" className="modal-card" role="dialog">
            <div className="modal-heading"><div><p className="eyebrow">{editing ? "DEFINIÇÕES DA INTEGRAÇÃO" : "NOVA INTEGRAÇÃO"}</p><h2 id="app-form-title">{editing ? "Editar aplicação" : "Registar aplicação"}</h2></div><button className="close-button" onClick={() => setModalOpen(false)} aria-label="Fechar">×</button></div>
            <form className="app-form" onSubmit={(event) => void saveApplication(event)}>
              <div className="form-grid">
                {!editing && <label>Código único<input autoFocus pattern="[a-z0-9]([a-z0-9-]{0,48}[a-z0-9])" required value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} placeholder="portal-exemplo" /><small>Minúsculas, números e hífenes; 2–50 caracteres.</small></label>}
                <label className="full-field">Nome da aplicação<input required maxLength={150} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Portal de Exemplo" /></label>
                <label className="full-field">Origens permitidas<textarea rows={3} value={form.allowedOrigins} onChange={(event) => setForm({ ...form, allowedOrigins: event.target.value })} placeholder={"https://portal.exemplo.co.ao\nhttp://localhost:3000"} /><small>Uma origem por linha; inclua protocolo e porta, sem caminho.</small></label>
                <label>Limite por ficheiro (MB)<input type="number" min="0.01" step="0.01" value={form.maxFileSizeMb} onChange={(event) => setForm({ ...form, maxFileSizeMb: event.target.value })} placeholder="Usar limite global" /></label>
                <label>Quota de armazenamento (MB)<input type="number" min="0.01" step="0.01" value={form.storageQuotaMb} onChange={(event) => setForm({ ...form, storageQuotaMb: event.target.value })} placeholder="Sem quota específica" /></label>
                <label className="full-field">Tipos de conteúdo permitidos<input value={form.allowedContentTypes} onChange={(event) => setForm({ ...form, allowedContentTypes: event.target.value })} placeholder="image/*, application/pdf" /><small>Separados por vírgula; vazio usa a lista global.</small></label>
                {editing && <label className="toggle-field"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /><span><strong>Aplicação activa</strong><small>Aplicações desactivadas deixam de conseguir autenticar-se.</small></span></label>}
              </div>
              {formError && <p className="form-error">{formError}</p>}
              <div className="modal-actions"><button className="secondary-button" type="button" onClick={() => setModalOpen(false)}>Cancelar</button><button className="primary-button" disabled={saving} type="submit">{saving ? "A guardar…" : editing ? "Guardar alterações" : "Registar aplicação"}</button></div>
            </form>
          </section>
        </div>
      )}

      {revealedKey && (
        <div className="modal-backdrop">
          <section aria-labelledby="key-title" aria-modal="true" className="modal-card key-modal" role="dialog">
            <div className="key-success">✓</div><p className="eyebrow">CREDENCIAL GERADA</p><h2 id="key-title">Guarde esta chave agora</h2>
            <p className="muted">A chave de <strong>{revealedFor}</strong> só é apresentada uma vez. Copie-a para um cofre de segredos antes de fechar.</p>
            <div className="revealed-key"><code>{revealedKey}</code><button className="secondary-button compact" onClick={() => void copyRevealedKey()}>{copied ? "Copiada ✓" : "Copiar"}</button></div>
            <div className="key-warning"><span>!</span> Não a envie por chat, e-mail ou código frontend. A rotação invalida imediatamente a chave anterior.</div>
            <button className="primary-button full-button" onClick={() => { setRevealedKey(""); setRevealedFor(""); }}>Já guardei a chave</button>
          </section>
        </div>
      )}
    </div>
  );
}

function MetricCard({ icon, iconClass, label, value, foot }: { icon: string; iconClass: string; label: string; value: string | number; foot: string }) {
  return (
    <article className="metric-card">
      <div className="metric-top"><span className={`metric-icon ${iconClass}`}>{icon}</span><span className="metric-arrow">↗</span></div>
      <p className="metric-label">{label}</p><div className="metric-value">{typeof value === "number" ? value.toLocaleString("pt-PT") : value}</div><p className="metric-foot">{foot}</p>
    </article>
  );
}

function ActivityTable({ events }: { events: Activity[] }) {
  if (events.length === 0) {
    return <div className="empty-activity"><span>◷</span><div><strong>Sem actividade registada</strong><p>As operações recentes aparecerão aqui assim que existirem eventos.</p></div></div>;
  }
  return (
    <div className="table-scroll">
      <table className="activity-table"><thead><tr><th>EVENTO</th><th>APLICAÇÃO</th><th>DETALHE</th><th>DATA E HORA</th></tr></thead>
        <tbody>{events.map((item) => <tr key={item.id}><td><span className={`event-mark event-${item.eventType.toLowerCase()}`}>{item.eventType.startsWith("APP") || item.eventType === "API_KEY_ROTATED" ? "▤" : item.eventType === "FILE_DELETED" ? "×" : "↥"}</span><strong>{eventLabel(item.eventType)}</strong></td><td>{item.applicationCode ? <code>{item.applicationCode}</code> : <span className="muted">Acesso público</span>}</td><td className="detail-cell">{item.details}</td><td className="date-cell">{formatDate(item.occurredAt)}</td></tr>)}</tbody>
      </table>
    </div>
  );
}

function ConfigCheck({ title, ok, detail }: { title: string; ok: boolean; detail: string }) {
  return <article className="config-check"><span className={ok ? "check-icon check-ok" : "check-icon check-fail"}>{ok ? "✓" : "!"}</span><div><strong>{title}</strong><p>{detail}</p></div><span className={ok ? "check-status check-status-ok" : "check-status check-status-fail"}>{ok ? "OK" : "ATENÇÃO"}</span></article>;
}

function EnvRow({ name, value, description }: { name: string; value: string; description: string }) {
  return <div className="env-row"><div><code>{name}</code><p>{description}</p></div><code className="env-value">{value}</code></div>;
}
