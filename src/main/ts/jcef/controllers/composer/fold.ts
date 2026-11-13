(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const CX = (CC.composer = CC.composer || ({} as ComposerNs));

  const h = CX.h;

  let folded = false;
  let foldable = false;
  let strip: HTMLElement | null = null;
  let toggle: HTMLButtonElement | null = null;
  let label: HTMLElement | null = null;

  function dock(): HTMLElement | null {
    return document.getElementById('dock');
  }

  function ensureStrip(): boolean {
    if (strip) return true;
    const d = dock();
    if (!d) return false;
    label = h('span', { class: 'dock-fold-phase' });
    toggle = h('button', {
      class: 'dock-fold-btn',
      attrs: { type: 'button', 'aria-controls': 'composer' },
      on: {
        click: function (e: Event) {
          e.preventDefault();
          setFolded(!folded, false);
        },
      },
    }) as HTMLButtonElement;
    strip = h('div', { class: 'dock-fold', attrs: { hidden: 'hidden' } }, label, toggle);
    d.insertBefore(strip, d.firstChild);
    return true;
  }

  function paint(): void {
    if (!strip || !toggle) return;
    const d = dock();
    if (d) d.classList.toggle('folded', folded);
    const hint = folded ? 'Show the input (Ctrl+Shift+↑)' : 'Hide the input (Ctrl+Shift+↓)';
    toggle.textContent = folded ? '▴ Show input' : '▾';
    toggle.title = hint;
    toggle.setAttribute('aria-label', hint);
    toggle.setAttribute('aria-expanded', String(!folded));
    if (foldable) {
      strip.removeAttribute('hidden');
      return;
    }
    const stranded = strip.contains(document.activeElement);
    strip.setAttribute('hidden', 'hidden');
    const input = CX.els && CX.els.input;
    if (stranded && input) input.focus();
  }

  function phaseOf(s: ComposerState): string {
    const cards = CC.els && CC.els.permissions;
    if (cards && cards.children.length > 0) return 'Waiting for your answer';
    if (s.interrupting) return 'Stopping…';
    if (s.turnActive) return 'Claude is working…';
    return 'Ready';
  }

  function setFolded(next: boolean, focusInput: boolean): void {
    if (next && !foldable) return;
    const input = CX.els && CX.els.input;
    if (next !== folded) {
      const tx = CC.transcript;
      const stick = !!(tx && tx.stickToBottom && tx.stickToBottom());
      const d = dock();
      const active = document.activeElement;
      const hadFocus = !!d && !!active && d.contains(active) && !(strip && strip.contains(active));
      folded = next;
      paint();
      if (folded && hadFocus && toggle) toggle.focus();
      if (tx && tx.scheduleScroll) tx.scheduleScroll(stick);
      if (CC.announce) CC.announce(folded ? 'Input hidden' : 'Input shown');
    }
    if (!folded && focusInput && input) input.focus();
  }

  CX.renderFold = function (s: ComposerState): void {
    if (!ensureStrip()) return;
    foldable = !!s.running && !CX.authWanted(s);
    if (!foldable) folded = false;
    if (label) label.textContent = phaseOf(s);
    paint();
  };

  CC.refreshFold = function (): void {
    if (CX.lastState) CX.renderFold(CX.lastState);
  };

  function readingTranscript(target: EventTarget | null): boolean {
    const el = target as HTMLElement | null;
    if (!el || !el.tagName) return false;
    if (el === document.body) return true;
    const conversation = document.getElementById('conversation');
    return (
      !!conversation &&
      conversation.contains(el) &&
      !el.isContentEditable &&
      !/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)
    );
  }

  document.addEventListener('keydown', function (e: KeyboardEvent) {
    if (!strip || !strip.isConnected) return;
    if (
      e.ctrlKey &&
      e.shiftKey &&
      !e.altKey &&
      !e.metaKey &&
      (e.key === 'ArrowDown' || e.key === 'ArrowUp')
    ) {
      const fold = e.key === 'ArrowDown';
      if (fold && !foldable) return;
      e.preventDefault();
      setFolded(fold, !fold);
      return;
    }
    if (!folded || !readingTranscript(e.target) || e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key.length === 1) setFolded(false, true);
  });
})();
