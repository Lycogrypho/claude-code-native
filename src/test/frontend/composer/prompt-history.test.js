const { loadFrontend } = require('../helpers/load');

function key(win, target, name, init = {}) {
  const ev = new win.KeyboardEvent(
    'keydown',
    Object.assign({ key: name, bubbles: true, cancelable: true }, init)
  );
  target.dispatchEvent(ev);
  return ev;
}

function setup(prompts) {
  const win = loadFrontend(['app-composer.js', 'app-transcript.js'], { vendor: false });
  win.cc.state({ running: true, starting: false, queue: [] });
  win.cc.batch(
    prompts.map((text, i) => ({ id: i + 1, order: i, speaker: 'USER', text, state: 'FINISHED', elapsed: 0 }))
  );
  const input = win.CC.els.composer.querySelector('.composer-input');
  input.focus();
  return { win, input };
}

describe('prompt history — the arrows recall what this tab already sent', () => {
  it('walks back from the newest prompt with ArrowUp and stops at the oldest', () => {
    const { win, input } = setup(['first', 'second']);
    expect(key(win, input, 'ArrowUp').defaultPrevented).toBe(true);
    expect(input.value).toBe('second');
    key(win, input, 'ArrowUp');
    expect(input.value).toBe('first');
    key(win, input, 'ArrowUp');
    expect(input.value).toBe('first');
  });

  it('walks forward with ArrowDown and gives back the draft past the newest', () => {
    const { win, input } = setup(['first', 'second']);
    input.value = 'half-written';
    input.setSelectionRange(0, 0);
    key(win, input, 'ArrowUp');
    key(win, input, 'ArrowUp');
    key(win, input, 'ArrowDown');
    expect(input.value).toBe('second');
    key(win, input, 'ArrowDown');
    expect(input.value).toBe('half-written');
  });

  it('leaves ArrowDown alone when no prompt is being recalled', () => {
    const { win, input } = setup(['first']);
    expect(key(win, input, 'ArrowDown').defaultPrevented).toBe(false);
    expect(input.value).toBe('');
  });

  it('moves the caret inside a multi-line prompt instead of recalling', () => {
    const { win, input } = setup(['first']);
    input.value = 'line one\nline two';
    input.setSelectionRange(input.value.length, input.value.length);
    expect(key(win, input, 'ArrowUp').defaultPrevented).toBe(false);
    expect(input.value).toBe('line one\nline two');
  });

  it('recalls the raw text the user typed, not its rendered markdown', () => {
    const { win, input } = setup(['**bold** and `code`']);
    key(win, input, 'ArrowUp');
    expect(input.value).toBe('**bold** and `code`');
  });

  it('skips a prompt repeated back to back', () => {
    const { win, input } = setup(['a', 'b', 'b']);
    key(win, input, 'ArrowUp');
    key(win, input, 'ArrowUp');
    expect(input.value).toBe('a');
  });

  it('starts again from the newest once the recalled text has been edited', () => {
    const { win, input } = setup(['first', 'second']);
    key(win, input, 'ArrowUp');
    key(win, input, 'ArrowUp');
    input.value = 'first, amended';
    input.setSelectionRange(0, 0);
    key(win, input, 'ArrowUp');
    expect(input.value).toBe('second');
    key(win, input, 'ArrowDown');
    expect(input.value).toBe('first, amended');
  });

  it('ignores the arrows with a modifier held', () => {
    const { win, input } = setup(['first']);
    expect(key(win, input, 'ArrowUp', { shiftKey: true }).defaultPrevented).toBe(false);
    expect(key(win, input, 'ArrowUp', { altKey: true }).defaultPrevented).toBe(false);
    expect(input.value).toBe('');
  });

  it('never recalls "undefined" after the transcript is rebuilt shorter', () => {
    const { win, input } = setup(['a', 'b', 'c']);
    key(win, input, 'ArrowUp');
    key(win, input, 'ArrowUp');
    key(win, input, 'ArrowUp');
    win.cc.clear();
    win.cc.batch([{ id: 9, order: 0, speaker: 'USER', text: 'x', state: 'FINISHED', elapsed: 0 }]);
    key(win, input, 'ArrowUp');
    expect(input.value).toBe('x');
    key(win, input, 'ArrowDown');
    expect(input.value).not.toBe('undefined');
  });

  it('moves the caret up a wrapped line instead of recalling when it is not at the start', () => {
    const { win, input } = setup(['first']);
    input.value = 'a single line long enough to wrap';
    input.setSelectionRange(10, 10);
    expect(key(win, input, 'ArrowUp').defaultPrevented).toBe(false);
    expect(input.value).toBe('a single line long enough to wrap');
  });

  it('recalls the Git chat prompts while the Git chat is shown', () => {
    const { win, input } = setup(['main chat prompt']);
    const rows = win.document.createElement('div');
    rows.innerHTML = '<div class="msg user"><div class="body"></div></div>';
    rows.querySelector('.body').__rawText = 'git chat prompt';
    const saved = { active: win.CC.gitChatActive, rows: win.CC.gitChatRows };
    try {
      win.CC.gitChatActive = () => true;
      win.CC.gitChatRows = () => rows;
      key(win, input, 'ArrowUp');
      expect(input.value).toBe('git chat prompt');
    } finally {
      win.CC.gitChatActive = saved.active;
      win.CC.gitChatRows = saved.rows;
    }
  });

  it('repaints the ghost suggestion after a recall', () => {
    const { win, input } = setup(['first']);
    const saved = win.CC.composer.renderGhost;
    let painted = 0;
    try {
      win.CC.composer.renderGhost = () => painted++;
      key(win, input, 'ArrowUp');
      expect(painted).toBe(1);
    } finally {
      win.CC.composer.renderGhost = saved;
    }
  });

  it('does nothing when the tab has sent nothing yet', () => {
    const { win, input } = setup([]);
    expect(key(win, input, 'ArrowUp').defaultPrevented).toBe(false);
    expect(input.value).toBe('');
  });
});
