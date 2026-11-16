interface PaletteCommand {
  name?: unknown;
  description?: unknown;
}

interface PaletteItem {
  name: string;
  description: string;
  score: number;
}

interface PaletteState {
  items: PaletteItem[];
  active: number;
  navigated: boolean;
}

interface PaletteEl extends HTMLElement {
  __built?: boolean;
  __list?: HTMLElement;
}

interface PaletteNs {
  state: PaletteState;
  queryOf(value: unknown): string | null;
  setCommands(list: PaletteCommand[]): void;
  rank(q: string): PaletteItem[];
  composerInput(): HTMLTextAreaElement | null;
  paletteEl(): PaletteEl | null;
  isOpen(): boolean;
  ensureBuilt(): PaletteEl | null;
  linkInput(open: boolean): void;
  syncActiveDescendant(): void;
  renderList(onPick: (idx: number) => void): void;
  updateActiveClass(): void;
}

interface SettingItem {
  key: unknown;
  label?: unknown;
  group?: unknown;
  sub?: unknown;
  type?: unknown;
  on?: boolean;
  hostOwned?: boolean;
}

interface SettingsPanel {
  group: string;
  title: string;
  path: string;
  rows: SettingItem[];
  subs: SettingsSub[];
  list: SettingItem[];
}

interface SettingsSub {
  name: string;
  list: SettingItem[];
}

interface SettingsGroup extends SettingsSub {
  direct: SettingItem[];
  subs: SettingsSub[];
}

interface SettingsRow extends HTMLElement {
  __ccKey?: string;
  __ccGroup?: string;
  __ccFocusId?: string;
}

interface SettingsNs {
  SEP: string;
  payload: { items?: unknown } | null;
  view: string | null;
  items(): SettingItem[];
  labelOf(it: SettingItem): string;
  groupOf(it: SettingItem): string;
  isRadio(it: SettingItem): boolean;
  groups(): SettingsGroup[];
  panelFor(path: string): SettingsPanel | null;
  structureSig(): string;
  allRows(): SettingsRow[];
  applyState(row: HTMLElement, on: boolean): void;
  enterGroup(path: string): void;
  openGroup(path: string): void;
  leaveGroup(): void;
  close(returnFocus: boolean): void;
  buildBody(): DocumentFragment;
}
