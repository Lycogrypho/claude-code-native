interface GuardLogEntry {
  id?: unknown;
  tab?: unknown;
  rule?: unknown;
  ruleLabel?: unknown;
  category?: unknown;
  categoryId?: unknown;
  command?: unknown;
  detail?: unknown;
  tool?: unknown;
  verdict?: unknown;
  verdictLabel?: unknown;
  viaLabel?: unknown;
  at?: unknown;
  explainable?: boolean;
}

interface GuardLogTab {
  id?: unknown;
  label?: unknown;
  count?: unknown;
}

interface GuardLogCategory {
  id?: unknown;
  label?: unknown;
  rules?: ({ id?: unknown; label?: unknown } | null)[];
}

interface GuardLogPayload {
  tabs?: (GuardLogTab | null)[];
  entries?: (GuardLogEntry | null)[];
  catalog?: (GuardLogCategory | null)[];
  recording?: boolean;
  window?: { kept?: unknown; max?: unknown; dropped?: unknown; missing?: unknown } | null;
}

interface GuardLogNs {
  payload: GuardLogPayload | null;
  tab: string;
  queryRaw: string;
  query: string;
  pickedCategories: string[] | null;
  pickedRules: string[] | null;
  text(value: unknown, fallback: string): string;
  textOrNull(value: unknown): string | null;
  list<T>(value: unknown): T[];
  num(value: unknown): number;
  tabs(): GuardLogTab[];
  catalog(): GuardLogCategory[];
  rulesOfCategories(picked: string[] | null): { id: string; label: string }[];
  visibleEntries(id: string): GuardLogEntry[];
  currentTab(): string;
  when(at: unknown): string;
  filtering(): boolean;
  repaint(): void;
  buildFiltersCard(): HTMLElement | null;
  entryNode(entry: GuardLogEntry): HTMLElement;
}

interface VulnFinding {
  id?: unknown;
  tier?: unknown;
  tierLabel?: unknown;
  name?: unknown;
  version?: unknown;
  ecosystem?: unknown;
  originLabel?: unknown;
  manifest?: unknown;
  summary?: unknown;
  cvss?: unknown;
  cvssType?: unknown;
  fixed?: unknown[];
  details?: unknown;
  references?: unknown[];
}

interface VulnReport {
  asOfMillis?: unknown;
  findings?: VulnFinding[];
  counts?: ({ tier?: unknown; label?: unknown; count?: unknown } | null)[];
  queried?: unknown;
  total?: unknown;
  shown?: unknown;
}

interface VulnPayload {
  available?: boolean;
  state?: unknown;
  status?: unknown;
  operator?: unknown;
  endpoint?: unknown;
  note?: unknown;
  inventory?: { components?: unknown } | null;
  disclosure?: { sent?: unknown[]; caveats?: unknown[] } | null;
  progress?: { done?: unknown; total?: unknown } | null;
  report?: VulnReport | null;
}

interface VulnInventory {
  endpoint?: unknown;
  components?: ({ ecosystem?: unknown; name?: unknown; version?: unknown; originLabel?: unknown } | null)[];
  truncated?: boolean;
  total?: unknown;
}

interface VulnNs {
  inventory: VulnInventory | null;
  expanded: Record<string, boolean>;
  pickedTiers: string[];
  text(v: unknown): string;
  num(v: unknown): number;
  repaint(): void;
  announce(message: string): void;
  inv(v: VulnPayload): { components?: unknown };
  bullets(title: string, list: unknown): HTMLElement | null;
  button(label: string, variant: string, onPress: () => void): HTMLElement;
  inventoryButton(v: VulnPayload): HTMLElement;
  consentCard(v: VulnPayload, state: string): HTMLElement | null;
  statusCard(v: VulnPayload, state: string): HTMLElement | null;
  findingsCard(v: VulnPayload): HTMLElement | null;
}

interface LogLine {
  seq?: unknown;
  at?: unknown;
  level?: unknown;
  category?: unknown;
  text?: unknown;
}

interface LogPayload {
  debug?: boolean;
  reset?: boolean;
  ring?: { max?: unknown; dropped?: unknown } | null;
  lines?: (LogLine | null)[];
}

interface LogNs {
  entriesEl: HTMLElement | null;
  emptyEl: HTMLElement | null;
  lines: LogLine[];
  lastSeq: number;
  level: string;
  debug: boolean;
  ring: { max: number; dropped: number };
  listEl: HTMLElement | null;
  text(value: unknown, fallback: string): string;
  num(value: unknown): number;
  request(): void;
  shows(line: LogLine): boolean;
  absorb(payload: LogPayload): void;
  setVisible(visible: boolean): void;
  when(at: unknown): string;
  repaint(): void;
  list(): HTMLElement;
  lineNode(line: LogLine): HTMLElement;
  append(lines: LogLine[]): void;
  reset(): void;
  applyFilter(): void;
}
