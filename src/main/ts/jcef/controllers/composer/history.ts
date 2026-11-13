(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const CX = (CC.composer = CC.composer || ({} as ComposerNs));

  const IDLE: HistoryNav = { index: -1, draft: '' };

  function activeTranscript(): Element | null {
    if (typeof CC.gitChatActive === 'function' && CC.gitChatActive()) {
      return typeof CC.gitChatRows === 'function' ? CC.gitChatRows() : null;
    }
    return (
      (CC.transcript && CC.transcript.conversationEl && CC.transcript.conversationEl()) ||
      document.getElementById('conversation')
    );
  }

  function sentPrompts(): string[] {
    const root = activeTranscript();
    if (!root) return [];
    const prompts: string[] = [];
    root.querySelectorAll('.msg.user .body').forEach(function (body) {
      const raw = (body as BodyEl).__rawText;
      if (raw) prompts.push(raw);
    });
    return prompts;
  }

  function caretAt(input: HTMLTextAreaElement, offset: number): boolean {
    return input.selectionStart === offset && input.selectionEnd === offset;
  }

  CX.wireHistory = function (input: HTMLTextAreaElement): void {
    let nav = IDLE;
    let shown: string | null = null;
    input.addEventListener('keydown', function (e: KeyboardEvent) {
      if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
      if (e.shiftKey || e.altKey || e.ctrlKey || e.metaKey || e.isComposing) return;
      const up = e.key === 'ArrowUp';
      if (nav.index >= 0 && input.value !== shown) nav = IDLE;
      const walking = nav.index >= 0;
      if (!walking && !caretAt(input, up ? 0 : input.value.length)) return;
      const move = CX.historyStep(nav, sentPrompts(), up ? -1 : 1, input.value);
      if (!move) return;
      e.preventDefault();
      nav = move.nav;
      shown = move.text;
      input.value = move.text;
      CX.autosize(input);
      input.setSelectionRange(input.value.length, input.value.length);
      CX.renderGhost();
    });
  };
})();
