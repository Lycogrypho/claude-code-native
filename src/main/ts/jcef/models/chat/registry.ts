(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const TX = (CC.transcript = CC.transcript || ({} as TranscriptNs));

  function el(tag: string, props?: HProps | null): HTMLElement {
    return CC.h(tag, props);
  }
  function esc(text: unknown): string {
    return CC.escape(text == null ? '' : text);
  }
  function md(text: unknown, hostLinks: boolean): string {
    if (CC.markdown) {
      try {
        return CC.markdown(text == null ? '' : text, { hostLinks: hostLinks });
      } catch (e) {}
    }
    return esc(text);
  }
  function safeSend(obj: unknown): void {
    if (CC.send) {
      try {
        CC.send(obj);
      } catch (e) {}
    }
  }
  function conversationEl(): HTMLElement | null {
    const node = (CC.els && CC.els.conversation) || document.getElementById('conversation');
    return node || null;
  }

  const rows = new Map<unknown, RowRec>();
  const toolCards = new Map<string, RowEl>();

  TX.el = el;
  TX.safeSend = safeSend;
  TX.conversationEl = conversationEl;
  TX.rows = rows;
  TX.toolCards = toolCards;

  TX.setBody = function (rec: RowRec, text: unknown): void {
    const body = rec.bodyNode;
    if (!body) {
      return;
    }
    const kind = rec.kind;
    if (kind === 'md') {
      body.innerHTML = md(text, rec.speaker === 'USER');
      body.__rawText = text == null ? '' : String(text);
    } else if (kind === 'pre') {
      body.textContent = text == null ? '' : String(text);
    } else {
      body.textContent = text == null ? '' : String(text);
      body.__rawText = text == null ? '' : String(text);
    }
  };
})();
