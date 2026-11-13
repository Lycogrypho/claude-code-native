(function () {
  'use strict';

  const CC = (window.CC = window.CC || ({} as CcShared));
  const CX = (CC.composer = CC.composer || ({} as ComposerNs));

  function withoutRepeats(prompts: string[]): string[] {
    return prompts.filter(function (p, i) {
      return p !== '' && p !== prompts[i - 1];
    });
  }

  CX.historyStep = function (
    nav: HistoryNav,
    prompts: string[],
    dir: number,
    current: string
  ): HistoryMove | null {
    const list = withoutRepeats(prompts);
    const at = nav.index >= 0 && list[nav.index] === current ? nav.index : list.length;
    const draft = nav.index < 0 ? current : nav.draft;
    if (dir < 0) {
      const index = at - 1;
      if (index < 0) return null;
      return { nav: { index: index, draft: draft }, text: list[index] };
    }
    if (nav.index < 0) return null;
    const index = at + 1;
    if (index >= list.length) return { nav: { index: -1, draft: '' }, text: nav.draft };
    return { nav: { index: index, draft: nav.draft }, text: list[index] };
  };
})();
