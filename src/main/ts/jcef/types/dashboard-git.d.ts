interface GitAction {
  id?: unknown;
  label?: unknown;
  group?: unknown;
  kind?: unknown;
  hint?: unknown;
  status?: unknown;
}

interface GitRef {
  name?: unknown;
  kind?: unknown;
  hash?: unknown;
  current?: boolean;
}

interface GitCommit {
  hash?: unknown;
  short?: unknown;
  parents?: unknown[];
  author?: unknown;
  authoredAtMillis?: unknown;
  files?: unknown;
  subject?: unknown;
}

interface GitRepo {
  present?: boolean;
  root?: unknown;
  branch?: unknown;
  head?: unknown;
}

interface GitPayload {
  available?: boolean;
  repo?: GitRepo | null;
  actions?: (GitAction | null)[];
  commitActions?: (GitAction | null)[];
  changes?: unknown[];
  commits?: (GitCommit | null)[];
  refs?: (GitRef | null)[];
  topology?: { upstream?: unknown; ahead?: unknown; behind?: unknown; mergeBase?: unknown } | null;
}

interface LaneEntry {
  hash: string;
  parents: string[];
  changes?: string[];
  commit?: GitCommit;
}

interface LaneRow {
  item: LaneEntry;
  hash: string;
  index: number;
  lane: number;
  up: number[];
  down: number[];
  through: number[];
  parents: string[];
  merge: boolean;
}

interface LaneModel {
  rows: LaneRow[];
  lanes: number;
}

interface GitNs {
  BRANCHES_ACTION: string;
  gitOf(git: unknown): GitPayload | null;
  repoOf(g: GitPayload): GitRepo;
  list<T>(value: unknown): T[];
  text(value: unknown, fallback: string): string;
  textOrNull(value: unknown): string | null;
  actionById(g: GitPayload, id: string): GitAction | null;
  actionButton(action: GitAction): HTMLElement;
  gutter(row: LaneRow, lanes: number): HTMLElement;
  fileCount(n: unknown): string | null;
  ageSince(atMillis: unknown): string | null;
  ageText(ms: unknown): string | null;
}
