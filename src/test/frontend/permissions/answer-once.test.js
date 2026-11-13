const { loadFrontend } = require('../helpers/load');

const permCard = {
  id: 'p1',
  tool: 'Edit',
  title: 'Edit',
  summary: 'Edit a.txt',
  reviewable: true,
  diff: '@@\n-a\n+b',
};

const questionCard = {
  id: 'q1',
  title: 'Question',
  questions: [
    { question: 'First?', options: [{ label: 'A' }, { label: 'B' }] },
    { question: 'Second?', options: [{ label: 'C' }] },
  ],
};

const mount = (cards) => {
  const win = loadFrontend(['app-permissions.js']);
  const sent = [];
  win.CC.send = (m) => sent.push(m);
  win.cc.permissions(cards);
  return { win, sent };
};

const button = (win, text) =>
  [...win.CC.els.permissions.querySelectorAll('button')].find((b) => b.textContent.trim() === text);

const clickOption = (win, label) =>
  [...win.CC.els.permissions.querySelectorAll('.q-option')]
    .find((el) => el.querySelector('.q-option-label').textContent === label)
    .click();

describe('a permission card is answered once', () => {
  it('sends one resolvePermission however often Accept is clicked', () => {
    const { win, sent } = mount([permCard]);
    button(win, 'Accept').click();
    button(win, 'Accept').click();
    button(win, 'Reject').click();
    expect(sent.filter((m) => m.type === 'resolvePermission')).toEqual([
      { type: 'resolvePermission', id: 'p1', allow: true },
    ]);
    expect(button(win, 'Reject').disabled).toBe(true);
  });

  it('keeps View diff usable, since it does not answer the card', () => {
    const { win, sent } = mount([permCard]);
    button(win, 'View diff').click();
    button(win, 'View diff').click();
    expect(sent.filter((m) => m.type === 'viewDiff').length).toBe(2);
    expect(button(win, 'Accept').disabled).toBe(false);
  });
});

describe('a question card is submitted once, and never empty', () => {
  it('keeps Submit disabled until every question has an answer', () => {
    const { win } = mount([questionCard]);
    expect(button(win, 'Submit').disabled).toBe(true);
    clickOption(win, 'A');
    expect(button(win, 'Submit').disabled).toBe(true);
    clickOption(win, 'C');
    expect(button(win, 'Submit').disabled).toBe(false);
  });

  it('keeps Submit disabled while Other is chosen with nothing typed', () => {
    const { win } = mount([questionCard]);
    clickOption(win, 'C');
    clickOption(win, 'Other');
    const input = win.CC.els.permissions.querySelector('.q-other-input');
    expect(button(win, 'Submit').disabled).toBe(true);
    input.value = 'mine';
    input.dispatchEvent(new win.Event('input'));
    expect(button(win, 'Submit').disabled).toBe(false);
  });

  it('sends one resolveQuestion on a double click', () => {
    const { win, sent } = mount([questionCard]);
    clickOption(win, 'B');
    clickOption(win, 'C');
    button(win, 'Submit').click();
    button(win, 'Submit').click();
    expect(sent.filter((m) => m.type === 'resolveQuestion').length).toBe(1);
  });
});
