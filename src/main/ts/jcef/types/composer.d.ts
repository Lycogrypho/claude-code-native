interface PillOption {
  id?: string;
  value?: string | null;
  wire?: string;
  on?: boolean;
  label?: unknown;
  selected?: boolean;
  group?: string;
}

interface PillField {
  label?: unknown;
  id?: unknown;
  options?: PillOption[];
}

interface PillDef {
  key: string;
  field: string;
  idKey: string;
  msg(o: PillOption): unknown;
}

interface Pill {
  el: HTMLElement;
  label: HTMLElement;
  def: PillDef;
  icon: HTMLElement | null;
}

interface UsageWindow {
  key?: string;
  label?: string;
  pct?: number | null;
  resetsAt?: string | null;
}

interface ComposerState {
  running?: boolean;
  starting?: boolean;
  resuming?: boolean;
  binaryMissing?: boolean;
  needsLogin?: boolean;
  turnActive?: boolean;
  interrupting?: boolean;
  guardOn?: boolean;
  remoteControlOn?: boolean;
  remoteControlError?: string | null;
  godModeOn?: boolean;
  queue?: unknown[];
  suggestion?: unknown;
  thinkingStatus?: string | null;
  context?: { pct?: number } | null;
  tokensOut?: number;
  reasoningTokens?: number;
  costUsd?: number;
  usage?: UsageWindow[];
  [field: string]: unknown;
}

interface ComposerEls {
  card: HTMLElement;
  input: HTMLTextAreaElement;
  send: HTMLElement;
  pills: Record<string, Pill>;
  queue: HTMLElement;
  ghost: HTMLElement;
  readout: HTMLElement;
  usageBars: HTMLElement;
  attachments: HTMLElement;
  attachBtn: HTMLElement;
}

interface OverflowMetrics {
  available: number;
  overflowing: boolean;
  ends: number[];
  reserved: number;
  toggle: number;
}

interface OverflowPlan {
  visible: number;
  toggle: boolean;
}

interface OverflowOptions {
  row: HTMLElement;
  label: string;
  items(): HTMLElement[];
  reserved?(): HTMLElement[];
  place(btn: HTMLElement): void;
  activate?(el: HTMLElement, anchor: HTMLElement): boolean;
}

interface OverflowApi {
  update(force?: boolean): void;
  close(returnFocus: boolean): void;
  toggle: HTMLElement;
  collected(): HTMLElement[];
}

interface OverflowRow extends HTMLElement {
  __ccOverflow?: OverflowApi;
}

interface OpenMenu {
  el: HTMLElement;
  pill: string;
  anchor: HTMLElement;
  sig?: string;
}

interface Roving {
  set(row: HTMLElement | null | undefined): void;
  focus(row: HTMLElement | null | undefined): void;
  step(delta: number): void;
}

interface RecentFile {
  name?: string;
  path?: string;
  ext?: string;
}

interface InstallMethod {
  id: string;
  label?: string;
  shell?: string;
  display: string;
}

interface AuthCard extends HTMLElement {
  __url?: string | null;
}

interface WiredEl extends HTMLElement {
  __wired?: boolean;
}

interface HistoryNav {
  index: number;
  draft: string;
}

interface HistoryMove {
  nav: HistoryNav;
  text: string;
}

interface ComposerNs {
  h(tag: string, props?: HProps | null, ...children: Child[]): HTMLElement;
  send(obj: unknown): void;
  els: ComposerEls | null;
  lastState: ComposerState | null;
  hostClipboard: boolean;
  roving(rows: () => HTMLElement[]): Roving;
  overflowFit(m: OverflowMetrics | null | undefined): OverflowPlan;
  overflowMeasure(
    row: HTMLElement,
    items: HTMLElement[],
    reserved: HTMLElement[],
    toggle: HTMLElement | null
  ): OverflowMetrics;
  overflowLabel(el: HTMLElement): string;
  dotsGlyph(): string;
  createOverflow(opts: OverflowOptions): OverflowApi | null;
  refreshOverflow(force?: boolean): void;
  openMenu: OpenMenu | null;
  menuSig(def: PillDef): string;
  togglePillMenu(def: PillDef, anchorEl: HTMLElement): void;
  positionMenu(menu: HTMLElement, anchor: HTMLElement): void;
  closeMenu(): void;
  PILL_DEFS: PillDef[];
  buildPill(def: PillDef): Pill;
  renderPills(s: ComposerState): void;
  syncOpenMenu(): void;
  attach: AttachNs;
  attachGlyph(): string;
  toggleAttachMenu(anchorEl: HTMLElement): void;
  renderAttachments(): void;
  wireImageDrop(card: HTMLElement | null): void;
  insertAtCursor(input: HTMLInputElement | HTMLTextAreaElement, text: string): void;
  wireImagePaste(input: HTMLTextAreaElement | null): void;
  renderReadout(s: ComposerState): void;
  renderMini(): void;
  palette: PaletteNs;
  openPalette(): void;
  setCommands(list: PaletteCommand[]): void;
  renderBoot(s: ComposerState): void;
  setInstallMethods(methods: InstallMethod[]): void;
  authWanted(s: ComposerState): boolean;
  renderAuth(s: ComposerState): void;
  buildActionRows(): HTMLElement | null;
  viewsRow(): HTMLElement | null;
  mountSettingsButton(): HTMLElement | null;
  settings: SettingsNs;
  ensureBuilt(): boolean;
  autosize(input: HTMLTextAreaElement | HTMLInputElement | null): void;
  setGuardOn(on: boolean | undefined): void;
  setRemoteControlOn(on: boolean | undefined, error: unknown): void;
  setGodMode(on: boolean | undefined): void;
  flameGlyph(lit: boolean): string;
  buildToggles(barRight: HTMLElement): {
    follow: HTMLElement;
    guard: HTMLElement;
    rc: HTMLElement;
    flame: HTMLElement;
    vibe: HTMLElement;
  };
  applyFollow(): void;
  sendGlyph(): string;
  wireInput(input: HTMLTextAreaElement): void;
  historyStep(nav: HistoryNav, prompts: string[], dir: number, current: string): HistoryMove | null;
  wireHistory(input: HTMLTextAreaElement): void;
  renderFold(s: ComposerState): void;
  onSendClick(e: Event): void;
  setGhost(text: string): void;
  renderGhost(): void;
  renderQueue(queue: unknown): void;
  renderSendMode(s: ComposerState): void;
  announceTurnState(s: ComposerState): void;
  [name: string]: unknown;
}
