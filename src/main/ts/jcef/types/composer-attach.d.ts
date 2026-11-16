interface AttachData {
  recent: RecentFile[];
  hasSelection: boolean;
  hasFile: boolean;
}

interface TreeEntry {
  name?: string;
  path: string;
  directory?: boolean;
}

interface TreeDir {
  entries: TreeEntry[] | null;
  pending: boolean;
  truncated: boolean;
}

interface TreeState {
  mode: string;
  multi: boolean;
  query: string;
  dirs: Record<string, TreeDir>;
  open: Record<string, boolean>;
  sel: Record<string, boolean>;
  exp: Record<string, string[]>;
  capped: Record<string, boolean>;
}

interface TreeRow extends HTMLElement {
  __ccPath?: string;
  __ccDir?: boolean;
}

interface Attachment {
  id?: unknown;
  kind?: unknown;
  label?: unknown;
}

interface AttachNs {
  data: AttachData;
  view: string;
  tree: TreeState | null;
  attIconGlyph(kind: string): string;
  folderGlyph(): string;
  fileIconGlyph(ext: string | undefined): string;
  extOf(name: unknown): string;
  isText(v: unknown): v is string;
  menuEl(): HTMLElement | null;
  bodyEl(): HTMLElement | null;
  reposition(): void;
  renderMenu(menu: HTMLElement, from?: string | null, focusSearch?: boolean): void;
  buildRootView(body: HTMLElement): void;
  enterTree(mode: string): void;
  leaveTree(): void;
  requestChildren(path: string): void;
  matchesQuery(entry: TreeEntry): boolean;
  hasMatch(path: string): boolean;
  openFor(path: string): boolean;
  visibleEntry(entry: TreeEntry): boolean;
  resetMatches(): void;
  buildTreeView(body: HTMLElement): void;
  doneButton(): HTMLElement;
  setMulti(on: boolean): void;
  renderTree(): void;
  treeEl(): HTMLElement | null;
  selectedCount(): number;
  confirmSelection(): void;
  dirState(path: string): string;
  applyRowState(row: HTMLElement, entry: TreeEntry): void;
  syncSelection(): void;
  markPaths(paths: string[], on: boolean): void;
  onRowPress(entry: TreeEntry, e: MouseEvent): void;
  setOpen(path: string, on: boolean): void;
  announceCap(entry: TreeEntry): void;
  rowByPath(path: string): TreeRow | null;
  visibleRows(): TreeRow[];
  rows: Roving;
  onMenuKey(e: KeyboardEvent): void;
  attachImageFile(file: File | null | undefined): void;
  isImageFile(f: File | null | undefined): boolean;
}
