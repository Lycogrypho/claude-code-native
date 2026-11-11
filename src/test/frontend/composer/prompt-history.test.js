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

  it('does nothing when the tab has sent nothing yet', () => {
    const { win, input } = setup([]);
    expect(key(win, input, 'ArrowUp').defaultPrevented).toBe(false);
    expect(input.value).toBe('');
  });
});
