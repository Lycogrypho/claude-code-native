(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const CX = (CC.composer = CC.composer || ({} as ComposerNs));

  const IDLE: HistoryNav = { index: -1, draft: '' };

  function sentPrompts(): string[] {
    const conversation =
      (CC.transcript && CC.transcript.conversationEl && CC.transcript.conversationEl()) ||
      document.getElementById('conversation');
    if (!conversation) return [];
    const prompts: string[] = [];
    conversation.querySelectorAll('.msg.user .body').forEach(function (body) {
      const raw = (body as BodyEl).__rawText;
      if (raw) prompts.push(raw);
    });
    return prompts;
  }

  function caretOnFirstLine(input: HTMLTextAreaElement): boolean {
    return (
      input.selectionStart === input.selectionEnd &&
      input.value.slice(0, input.selectionStart).indexOf('\n') < 0
    );
  }

  function caretOnLastLine(input: HTMLTextAreaElement): boolean {
    return (
      input.selectionStart === input.selectionEnd && input.value.slice(input.selectionEnd).indexOf('\n') < 0
    );
  }

  CX.wireHistory = function (input: HTMLTextAreaElement): void {
    let nav = IDLE;
    let shown: string | null = null;
    input.addEventListener('keydown', function (e: KeyboardEvent) {
      if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
      if (e.shiftKey || e.altKey || e.ctrlKey || e.metaKey || e.isComposing) return;
      const up = e.key === 'ArrowUp';
      if (up ? !caretOnFirstLine(input) : !caretOnLastLine(input)) return;
      if (nav.index >= 0 && input.value !== shown) nav = IDLE;
      const move = CX.historyStep(nav, sentPrompts(), up ? -1 : 1, input.value);
      if (!move) return;
      e.preventDefault();
      nav = move.nav;
      shown = move.text;
      input.value = move.text;
      CX.autosize(input);
      input.setSelectionRange(input.value.length, input.value.length);
    });
  };
})();
