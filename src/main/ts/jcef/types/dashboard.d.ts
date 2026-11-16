interface SessionPayload {
  model?: unknown;
  cwd?: unknown;
  home?: unknown;
  account?: { email?: unknown; org?: unknown; plan?: unknown; provider?: unknown } | null;
  [name: string]: unknown;
}

interface WorkloadAgent {
  agentId?: string | null;
  parent?: string | null;
  label?: unknown;
  type?: unknown;
  status?: unknown;
  running?: boolean;
  chain?: string | null;
}

interface WorkloadTask {
  id?: unknown;
  agentId?: string | null;
  type?: unknown;
  desc?: unknown;
  status?: string | null;
  running?: boolean;
  chain?: string | null;
}

interface WorkloadChat {
  chatId?: string | number | null;
  title?: unknown;
  selected?: boolean;
  tree?: (WorkloadAgent | null)[];
  tasks?: (WorkloadTask | null)[];
}

interface WorkloadWindowSpec {
  minutes?: number | null;
  options?: ({ minutes?: unknown; label?: unknown } | null)[];
}

interface WorkloadsNs {
  chatNode(chat: WorkloadChat): DiagramNode & { children: DiagramNode[] };
  taskNode(t: WorkloadTask, chatId: unknown): DiagramNode;
}

interface DashView {
  title: string;
  empty: string;
  cards(s: SessionPayload): (HTMLElement | null)[];
  visible?(shown: boolean): void;
}

interface DashPanelState {
  lastSession: SessionPayload | null;
  lastMcp: unknown;
  toggleBtn: HTMLElement | null;
  planBtn: HTMLElement | null;
  gitBtn: HTMLElement | null;
  vulnBtn: HTMLElement | null;
  panel: HTMLElement | null;
  inner: HTMLElement | null;
  toggles: HTMLElement | null;
  shown: boolean;
  built: boolean;
  gitTab: boolean;
  gitOpened: boolean;
  gitSub: string;
  currentView: string;
}

interface DashNs {
  core(): CcShared | null;
  conversation(): HTMLElement | null;
  appRoot(): HTMLElement | null;
  h(tag: string, props?: HProps | null, ...children: Child[]): HTMLElement;
  send(obj: unknown): void;
  num(v: unknown): number | null;
  fmtInt(v: unknown): string | null;
  fmtUsd(v: unknown): string | null;
  statRow(label: string, value: unknown): HTMLElement | null;
  card(title: string, body: unknown, wide?: boolean, anchor?: string): HTMLElement | null;
  leaveDashboard(): void;
  buildPlanCard(plan: unknown): HTMLElement | null;
  buildUsageCard(usage: unknown): HTMLElement | null;
  buildContextCard(ctx: unknown): HTMLElement | null;
  buildCostCard(cost: unknown): HTMLElement | null;
  buildAccountCard(acct: unknown): HTMLElement | null;
  buildEnvCard(payload: SessionPayload): HTMLElement | null;
  buildMcpCard(payload: unknown): HTMLElement | null;
  workloads: WorkloadsNs;
  buildWorkloadsCard(payload: SessionPayload): HTMLElement | null;
  git: GitNs;
  gitViewTabs(current: string): HTMLElement;
  gitLanes(entries: unknown): LaneModel;
  buildGitHeadCard(git: unknown): HTMLElement | null;
  buildGitActionsCard(git: unknown): HTMLElement | null;
  buildGitHistoryCard(git: unknown): HTMLElement | null;
  buildGitTopologyCard(git: unknown): HTMLElement | null;
  gitChatPane(): HTMLElement | null;
  gitChatShown(): void;
  guardLog: GuardLogNs;
  buildGuardCards(): (HTMLElement | null)[];
  guardTab(): string;
  guardVisible(visible: boolean): void;
  repaintGuard(): void;
  log: LogNs;
  buildLogCards(): (HTMLElement | null)[];
  logVisible(visible: boolean): void;
  repaintLog(): void;
  vuln: VulnNs;
  buildVulnCards(v: unknown): (HTMLElement | null)[];
  state: DashPanelState;
  VIEWS: Record<string, DashView>;
  defaultView(): string;
  gitChatOpen(): boolean;
  gitSubView(): string;
  setGitSubView(view: string): void;
  lastSession(): SessionPayload | null;
  reconcile(container: HTMLElement, cards: HTMLElement[]): void;
  syncViewVisibility(): void;
  render(): void;
  renderIfShown(): void;
  repaint(): void;
  viewButton(label: string, view: string | null): HTMLElement;
  announceView(): void;
  markActiveButton(): void;
  mountToggles(): void;
  ensureBuilt(): void;
  applyVisibility(): void;
  toggle(): void;
  toggleDashboard(): void;
  dashboardShown(): boolean;
  [name: string]: unknown;
}
