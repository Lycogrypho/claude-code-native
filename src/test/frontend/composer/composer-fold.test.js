const { loadFrontend, readCss } = require('../helpers/load');

function key(win, target, name, init = {}) {
  const ev = new win.KeyboardEvent(
    'keydown',
    Object.assign({ key: name, bubbles: true, cancelable: true }, init)
  );
  target.dispatchEvent(ev);
  return ev;
}

function setup(state = {}) {
  const win = loadFrontend(['app-composer.js', 'app-transcript.js', 'app-permissions.js'], { vendor: false });
  win.cc.state(Object.assign({ running: true, starting: false, queue: [] }, state));
  const dock = win.document.getElementById('dock');
  const strip = dock.querySelector('.dock-fold');
  const toggle = strip && strip.querySelector('.dock-fold-btn');
  const label = strip && strip.querySelector('.dock-fold-phase');
  const input = win.CC.els.composer.querySelector('.composer-input');
  return { win, dock, strip, toggle, label, input };
}

describe('composer fold — the input area can be hidden to give the transcript the room', () => {
  it('puts a toggle at the top of the dock, expanded to begin with', () => {
    const { dock, strip, toggle } = setup();
    expect(dock.firstElementChild).toBe(strip);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.getAttribute('aria-controls')).toBe('composer');
  });

  it('folds and unfolds on the toggle', () => {
    const { dock, toggle } = setup();
    toggle.click();
    expect(dock.classList.contains('folded')).toBe(true);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    expect(dock.classList.contains('folded')).toBe(false);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
  });

  it('hides the composer and its controls, never the permission cards', () => {
    const css = readCss().replace(/\/\*[\s\S]*?\*\//g, '');
    const at = css.indexOf('#dock.folded #composer');
    expect(at).toBeGreaterThan(-1);
    const rule = css.slice(at, css.indexOf('}', at));
    expect(rule).toMatch(/#dock\.folded #controls/);
    expect(rule).toMatch(/display:\s*none/);
    expect(css).not.toMatch(/#dock\.folded #permissions/);
  });

  it('names no palette rule: the palette lives inside the composer it folds with', () => {
    const { win } = setup();
    expect(win.CC.els.composer.querySelector('#palette')).not.toBeNull();
    expect(readCss()).not.toMatch(/#dock\.folded #palette/);
  });

  it('tells the turn phase while folded', () => {
    const { win, toggle, label } = setup();
    toggle.click();
    expect(label.textContent).toBe('Ready');
    win.cc.state({ running: true, starting: false, queue: [], turnActive: true });
    expect(label.textContent).toBe('Claude is working…');
    win.cc.state({ running: true, starting: false, queue: [], turnActive: true, interrupting: true });
    expect(label.textContent).toBe('Stopping…');
  });

  it('says a card is waiting for an answer', () => {
    const { win, toggle, label } = setup();
    toggle.click();
    win.cc.permissions([
      { id: 'q1', title: 'Question', questions: [{ question: 'Which?', options: [{ label: 'A' }] }] },
    ]);
    win.cc.state({ running: true, starting: false, queue: [], turnActive: true });
    expect(label.textContent).toBe('Waiting for your answer');
  });

  it('folds on Ctrl+Shift+ArrowDown and unfolds on Ctrl+Shift+ArrowUp, back in the input', () => {
    const { win, dock, input } = setup();
    input.focus();
    expect(key(win, input, 'ArrowDown', { ctrlKey: true, shiftKey: true }).defaultPrevented).toBe(true);
    expect(dock.classList.contains('folded')).toBe(true);
    key(win, win.document.body, 'ArrowUp', { ctrlKey: true, shiftKey: true });
    expect(dock.classList.contains('folded')).toBe(false);
    expect(win.document.activeElement).toBe(input);
  });

  it('moves the focus to the toggle when the input it was in is folded away', () => {
    const { win, input, toggle } = setup();
    input.focus();
    key(win, input, 'ArrowDown', { ctrlKey: true, shiftKey: true });
    expect(win.document.activeElement).toBe(toggle);
  });

  it('unfolds and focuses the input when the user starts typing while folded', () => {
    const { win, dock, toggle, input } = setup();
    toggle.click();
    key(win, win.document.body, 'h');
    expect(dock.classList.contains('folded')).toBe(false);
    expect(win.document.activeElement).toBe(input);
  });

  it('leaves a plain ArrowUp to scroll the transcript while folded', () => {
    const { win, dock, toggle } = setup();
    toggle.click();
    key(win, win.document.body, 'ArrowUp');
    expect(dock.classList.contains('folded')).toBe(true);
  });

  it('unfolds when the user types with the focus in the transcript', () => {
    const { win, dock, toggle, input } = setup();
    toggle.click();
    const row = win.document.createElement('div');
    win.document.getElementById('conversation').appendChild(row);
    key(win, row, 'h');
    expect(dock.classList.contains('folded')).toBe(false);
    expect(win.document.activeElement).toBe(input);
  });

  it('leaves Space on a card option to choose it, and stays folded', () => {
    const { win, dock, toggle } = setup();
    win.cc.permissions([
      { id: 'q1', title: 'Question', questions: [{ question: 'Which?', options: [{ label: 'A' }] }] },
    ]);
    toggle.click();
    const option = win.CC.els.permissions.querySelector('.q-option');
    option.focus();
    const ev = key(win, option, ' ');
    expect(ev.defaultPrevented).toBe(false);
    expect(dock.classList.contains('folded')).toBe(true);
  });

  it('tells a card is waiting as soon as it arrives, and Ready when it is gone', () => {
    const { win, toggle, label } = setup();
    toggle.click();
    win.cc.permissions([
      { id: 'q1', title: 'Question', questions: [{ question: 'Which?', options: [{ label: 'A' }] }] },
    ]);
    expect(label.textContent).toBe('Waiting for your answer');
    win.cc.permissions([]);
    expect(label.textContent).toBe('Ready');
  });

  it('moves the focus to the toggle when any control it was on is folded away', () => {
    const { win, dock, toggle } = setup();
    const control = dock.querySelector('#controls button, #composer button:not(.dock-fold-btn)');
    control.focus();
    expect(win.document.activeElement).toBe(control);
    key(win, control, 'ArrowDown', { ctrlKey: true, shiftKey: true });
    expect(win.document.activeElement).toBe(toggle);
  });

  it('hands the focus to the input when the strip it was on disappears', () => {
    const { win, toggle, input } = setup();
    toggle.click();
    toggle.focus();
    win.cc.state({ running: false, starting: true, queue: [] });
    expect(win.document.activeElement).toBe(input);
  });

  it('leaves keys typed into another field alone', () => {
    const { win, dock, toggle } = setup();
    toggle.click();
    const other = win.document.createElement('input');
    win.document.body.appendChild(other);
    other.focus();
    key(win, other, 'h');
    expect(dock.classList.contains('folded')).toBe(true);
  });

  it('offers no fold while the session is not running, and unfolds when it stops', () => {
    const { win, dock, toggle, strip } = setup();
    toggle.click();
    win.cc.state({ running: false, starting: true, queue: [] });
    expect(dock.classList.contains('folded')).toBe(false);
    expect(strip.hasAttribute('hidden')).toBe(true);
    key(win, win.document.body, 'ArrowDown', { ctrlKey: true, shiftKey: true });
    expect(dock.classList.contains('folded')).toBe(false);
  });
});
