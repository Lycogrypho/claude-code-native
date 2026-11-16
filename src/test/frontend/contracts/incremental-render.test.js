const { loadFrontend } = require('../helpers/load');

function openFind(win, query) {
  win.document.body.dispatchEvent(
    new win.KeyboardEvent('keydown', { key: 'f', ctrlKey: true, bubbles: true, cancelable: true })
  );
  const input = win.document.querySelector('.find-input');
  input.value = query;
  input.dispatchEvent(new win.Event('input', { bubbles: true }));
  return win.document.querySelector('.find-count');
}

const row = (id, text) => ({ id, order: id, speaker: 'ASSISTANT', text, state: 'FINISHED' });

describe('a push repaints what changed, not the page', () => {
  it('re-highlights only the rows of the batch while a find query is open', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 'alpha one'), row(2, 'alpha two')]);
    const count = openFind(win, 'alpha');
    expect(count.textContent).toBe('1 / 2');
    const untouched = win.document.querySelectorAll('mark.cc-hit')[0];

    win.cc.batch([row(2, 'alpha two and alpha three')]);

    const marks = [...win.document.querySelectorAll('mark.cc-hit')];
    expect(marks.length).toBe(3);
    expect(marks[0]).toBe(untouched);
    expect(count.textContent).toBe('1 / 3');
  });

  it('keeps the hits in reading order after a row in the middle changes', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 'alpha one'), row(2, 'no hit'), row(3, 'alpha three')]);
    const count = openFind(win, 'alpha');
    win.cc.batch([row(2, 'alpha in the middle')]);
    expect(count.textContent).toBe('1 / 3');
    win.CC.transcript.findNext();
    expect(win.document.querySelector('mark.cc-hit.active').parentNode.textContent).toContain(
      'in the middle'
    );
  });

  it('puts an entry that arrives out of order where its order says', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([row(1, 'first'), row(3, 'third')]);
    win.cc.batch([row(2, 'second')]);
    win.cc.batch([row(4, 'fourth')]);
    const texts = [...win.CC.els.conversation.querySelectorAll('.msg .body')].map((b) =>
      b.textContent.trim()
    );
    expect(texts).toEqual(['first', 'second', 'third', 'fourth']);
  });

  it('leaves the readout and the usage bars alone when what they show has not changed', () => {
    const win = loadFrontend(['app-composer.js'], { vendor: false });
    const state = {
      running: true,
      starting: false,
      queue: [],
      tokensOut: 1200,
      usage: [{ label: '5h', pct: 12.5, resetsAt: 0 }],
    };
    win.cc.state(state);
    const readoutCell = win.CC.els.composer.querySelector('.readout .ro-item');
    const usageCell = win.CC.els.composer.querySelector('.usage-bars .ub-item');
    expect(readoutCell).not.toBeNull();
    expect(usageCell).not.toBeNull();

    win.cc.state(Object.assign({}, state));
    expect(win.CC.els.composer.querySelector('.readout .ro-item')).toBe(readoutCell);
    expect(win.CC.els.composer.querySelector('.usage-bars .ub-item')).toBe(usageCell);

    win.cc.state(Object.assign({}, state, { tokensOut: 2400 }));
    expect(win.CC.els.composer.querySelector('.readout').textContent).toContain('2.4k out');
  });

  it('keeps the keyboard focus on the same tab when the tab bar is redrawn', () => {
    const win = loadFrontend(['app-tabs.js'], { vendor: false });
    win.CC.send = () => {};
    const chats = [
      { id: '1', title: 'Chat 1', selected: true },
      { id: '2', title: 'Chat 2' },
    ];
    win.cc.tabs({ chats, tree: [], tasks: [] });
    const second = [...win.document.querySelectorAll('.pill')].find((p) => p.textContent === 'Chat 2');
    second.focus();
    expect(win.document.activeElement).toBe(second);

    win.cc.tabs({ chats: [chats[0], { id: '2', title: 'Chat 2', attention: true }], tree: [], tasks: [] });

    const focused = win.document.activeElement;
    expect(focused.classList.contains('pill')).toBe(true);
    expect(focused.textContent).toBe('Chat 2');
  });

  it('moves the focus to the selected tab when the focused one is closed', () => {
    const win = loadFrontend(['app-tabs.js'], { vendor: false });
    win.CC.send = () => {};
    const chats = [
      { id: '1', title: 'Chat 1', selected: true },
      { id: '2', title: 'Chat 2' },
    ];
    win.cc.tabs({ chats, tree: [], tasks: [] });
    [...win.document.querySelectorAll('.pill')].find((p) => p.textContent === 'Chat 2').focus();

    win.cc.tabs({ chats: [chats[0]], tree: [], tasks: [] });

    expect(win.document.activeElement.textContent).toBe('Chat 1');
  });
});
