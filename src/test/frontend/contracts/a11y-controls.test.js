const { loadFrontend } = require('../helpers/load');

const questionCard = {
  id: 'q1',
  title: 'Question',
  questions: [
    { question: 'Which one?', options: [{ label: 'A' }, { label: 'B' }] },
    { question: 'And then?', multiSelect: true, options: [{ label: 'C' }] },
  ],
};

function isRealButton(el) {
  return !!el && el.tagName === 'BUTTON' && el.getAttribute('type') === 'button';
}

describe('controls are reachable and understandable without a pointer', () => {
  it('exposes the chosen option of a question as pressed, not only as a colour', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard]);
    const options = [...win.CC.els.permissions.querySelectorAll('.q-option')];
    expect(options.every((o) => o.getAttribute('aria-pressed') === 'false')).toBe(true);
    options[1].click();
    expect(options.map((o) => o.getAttribute('aria-pressed')).slice(0, 3)).toEqual([
      'false',
      'true',
      'false',
    ]);
  });

  it('groups the options of each question under the question that labels them', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard]);
    const groups = [...win.CC.els.permissions.querySelectorAll('.q-options')];
    expect(groups.length).toBe(2);
    const labels = groups.map((group) => {
      expect(group.getAttribute('role')).toBe('group');
      return win.document.getElementById(group.getAttribute('aria-labelledby')).textContent;
    });
    expect(labels).toEqual(['Which one?', 'And then?']);
  });

  it('labels the free-text answer of each question', () => {
    const win = loadFrontend(['app-permissions.js']);
    win.cc.permissions([questionCard]);
    const inputs = [...win.CC.els.permissions.querySelectorAll('.q-other-input')];
    expect(inputs.map((i) => i.getAttribute('aria-label'))).toEqual([
      'Your own answer to: Which one?',
      'Your own answer to: And then?',
    ]);
  });

  it('removes a queued prompt with a real button', () => {
    const win = loadFrontend(['app-composer.js'], { vendor: false });
    win.cc.state({ running: true, starting: false, queue: ['first'] });
    const x = win.CC.els.composer.querySelector('.queue-x');
    expect(isRealButton(x)).toBe(true);
    expect(x.getAttribute('aria-label')).toBe('Remove queued prompt');
  });

  it('removes an attachment with a real button', () => {
    const win = loadFrontend(['app-composer.js'], { vendor: false });
    win.cc.state({ running: true, starting: false, queue: [] });
    win.cc.attachments([{ id: 'a1', kind: 'file', label: 'a.txt' }]);
    const x = win.document.querySelector('.att-x');
    expect(isRealButton(x)).toBe(true);
    expect(x.getAttribute('aria-label')).toBe('Remove attachment');
  });

  it('closes the find bar with a real button', () => {
    const win = loadFrontend(['app-transcript.js'], { vendor: false });
    win.document.dispatchEvent(
      new win.KeyboardEvent('keydown', { key: 'f', ctrlKey: true, bubbles: true, cancelable: true })
    );
    const x = win.document.querySelector('.find-x');
    expect(isRealButton(x)).toBe(true);
    expect(x.getAttribute('aria-label')).toBe('Close find');
  });
});
