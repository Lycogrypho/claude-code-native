const { loadFrontend, readCss } = require('../helpers/load');

describe('transcript — the user row stands out by colour and a left bar, not by a card', () => {
  const css = readCss().replace(/\/\*[\s\S]*?\*\//g, '');
  const rule = (selector) => {
    const at = css.indexOf(`\n${selector} {`);
    expect(at).toBeGreaterThan(-1);
    return css.slice(at, css.indexOf('}', at));
  };

  it('marks the row with an accent bar on its left edge', () => {
    expect(rule('.msg.user')).toMatch(/border-left:[^;]*var\(--accent\)/);
  });

  it('adds no other chrome around the row, so it stays a row', () => {
    const row = rule('.msg.user');
    expect(row).not.toMatch(/(^|[\s;{])border:/);
    expect(row).not.toMatch(/background/);
    expect(row).not.toMatch(/box-shadow/);
  });

  it('tints the prompt text from the theme accent and text, so it follows light and dark themes', () => {
    expect(rule('.msg.user .body')).toMatch(
      /(^|[\s;{])color:\s*color-mix\([^;]*var\(--accent\)[^;]*var\(--text\)/
    );
  });

  it('tints the You label with the accent', () => {
    expect(rule('.msg.user .msg-head .name')).toMatch(/(^|[\s;{])color:\s*var\(--accent\)/);
  });

  it('applies to the row the transcript actually renders for a user prompt', () => {
    const win = loadFrontend(['app-transcript.js']);
    win.cc.batch([{ id: 1, order: 0, speaker: 'USER', text: 'hello', state: 'FINISHED', elapsed: 0 }]);
    expect(win.document.querySelector('.msg.user .msg-head .name')).not.toBeNull();
    expect(win.document.querySelector('.msg.user .body')).not.toBeNull();
  });
});
