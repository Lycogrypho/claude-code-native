# Page and descriptor notes

The reasoning that used to stand in comments beside the page shell and the plugin descriptors. It was moved here when the
no-comments rule was extended to every file (D_0041): the files now carry code only, and a reason
belongs here, in a test or in the commit message. Each note is headed by the line it stood above.

## src/main/resources/jcef/shell.html

### `<title>Claude Code</title>`

The host injects the real, hash-pinned Content-Security-Policy here (and as an HTTP response header).
Scripts are allowed only by exact sha256 hash — no 'unsafe-inline', so injected markup can never execute.

### `<div id="a11y-status" class="visually-hidden" role="status" aria-live="polite" aria-atomic="true"></div>`

Status message region (WCAG 2.2 AA — 4.1.3 Status Messages).
The transcript streams in without ever moving focus, so a screen-reader user otherwise gets no signal
that Claude started answering, finished, or is asking for permission. This region announces those
transitions without stealing focus.

It is declared HERE, in the static shell, rather than created when the first message arrives: a live
region has to be present in the DOM *before* the text is written into it, or assistive technology
never announces that first change. `polite` so it waits for a pause instead of interrupting, and
`atomic` so the whole short phrase is read rather than just the diff.

### `<nav id="tabsbar" aria-label="Chats and agents" hidden></nav>`

The tab bar: chats, their agents, and the background tasks. Declared here (empty, hidden) rather
than created on first use, so the page has a stable place to put it and no layout jump when the
first agent appears. Filled by the tabs controller from the host's `cc.tabs` payload.

### `<div id="work">`

The work area: what belongs to ONE chat, and the dock, which belongs to none.

That split is the whole shape of this file, so it is worth stating as a rule rather than leaving it
to be inferred from the nesting. **A chat's context is its transcript and the three screens that
stand in for it** — install, sign-in, loading — and nothing else: they are what changes when you
switch chat, and they are grouped in `#tabview` for exactly that reason. Everything outside it is
one thing shared by every chat: `#tabsbar` above, `#dock` below (the meters, the palette, the
permission cards and the prompt box).

The composer stays usable while the binary starts: a prompt typed then is queued and sent once there
is a session (see `ClaudeSession.send`).

### `<div id="tabview">`

The chat's own area. Its four layers share ONE grid cell (`grid-area: 1 / 1`), so a screen covers
exactly the transcript and the transcript keeps its box — and therefore its scroll offset —
underneath instead of being replaced by one.

The waiting screens have been two other things and both failures are worth stating, because each
was a fix for the other. As `position: fixed`/`inset: 0` panels over `#app` they covered what they
did not own: first the chat tabs, then the composer. As ROWS INSIDE `#conversation` they were
content of the very element `cc.clear()` empties — and it empties it on every transcript switch and
every session stop — so the first clear DELETED all three from the document, and every later
`renderBoot`/`renderAuth` found no element and returned silently. The screens then only ever
appeared in a browser that had not cleared yet, i.e. in a freshly opened chat tab.

WHERE A LAYER GOES, for whoever adds the next one: here, beside `#conversation` — if it belongs to
the chat. Never a child of `#app`, never `position: fixed` over the page, and never inside a
container something else owns. If it belongs to the whole tool window it goes in `#dock` instead,
and that is now a question with an answer rather than a habit. The stacking is in base.css; the
screens' own rules are in boot.css.

### `<div id="boot" role="status" aria-live="polite">`

Boot screen, shown while the `claude` binary is launching.
Declared in the static shell and visible BY DEFAULT, hidden only once the host says otherwise. The
binary is launched FIRST and this page loads while it boots, so at load time the process is normally
not up yet: the honest initial state is "waiting", not "idle". Starting hidden would mean every tab
flashes a live-looking composer it cannot yet send anything with.

role="status" + aria-live so the wait is announced rather than being a purely visual state.

### `<div id="boot-missing" hidden>`

The FOURTH boot state: no `claude` binary was found. Static like the rest of the shell (the
a11y lesson: markup that exists at load announces; markup created on first use does not), shown
by the composer modules only when the host reports binaryMissing. Each install method renders as a
button plus the EXACT command it runs, copyable — corporate networks block individual
installers, and a command the user can read is a command they can take to whatever route their
machine allows. The path row is the manual escape hatch for an install that lives elsewhere.

### `<div id="auth-card" role="status" aria-live="polite" hidden>`

Sign-in card: raised whenever the plugin detects the binary is not authenticated (proactive
`auth status` probe, a failed turn, or a fresh install), independent of the boot overlay — a live
session can lose its login too. Static markup for the same a11y reason as the boot screen; every
step container exists up front and the composer modules shows exactly one (`cc.authState`).

### `<div class="auth-step" data-step="idle">`

Two sign-ins, both a BUTTON. The Console one is what an organisation needs: its OAuth consent
includes `org:create_api_key`, so the binary provisions a key for Claude Code itself — nobody
pastes a credential and nobody has to distribute one. Typing a key by hand is therefore the
exception now, not the second option, and lives behind the disclosure below.

### `<div class="auth-step" data-step="waiting" hidden>`

Every non-idle step MUST offer a way back to idle. This one did not, and a login flow that
never reached the URL stage stranded the card here for good: no Sign in, no API key field,
no Cancel — the whole card looked dead. A step with no exit is a bug, not a spinner.

### `<div class="auth-step" data-step="browser" hidden>`

ONE step for the whole browser leg. The binary listens for the OAuth callback itself, so
completing the sign-in in the browser finishes this card with nothing to paste — the code
field is the OPTIONAL fallback for when the callback can't reach it, not a step of its own.
Splitting them (as this card originally did) jumped straight to a "paste the code" screen
and made the fallback look mandatory.

### `<div class="auth-row auth-url-actions">`

Buttons, not the URL. An OAuth authorize URL is ~200 characters of query string: shown in
full it swamps the card, and truncated it is worse than useless — it looks like something
you could read or retype and it is neither. So the two things anyone actually does with
it get a button each.

### `` `#palette` is the FIRST row of it, in the flow, and that placement is the fix for a bug rather than ``

/#tabview

### `<div id="dock">`

The dock: what sits under the transcript and above nothing.
`#palette` is the FIRST row of it, in the flow, and that placement is the fix for a bug rather than
a style: it used to be a `position: fixed` overlay held clear of the composer by
`padding-bottom: 72px` — a guess at a height that changes with every pill, queued prompt and
attachment chip, so the list covered the box you were typing in exactly when you had most in it. A
row above the composer cannot cover the composer at any height.

### `<div id="palette" hidden></div>`

The slash palette. the composer modules moves it INTO the prompt card, where it is anchored to the
card's top edge and hangs over whatever is above — the meters are worth covering for as long as
you are choosing a command; the box you are typing in is not, which is why it hangs upwards.

### `<div id="controls" class="composer-controls">`

ONE row above the prompt box: where you are on the left, what you can do to this chat on the
right. Two groups rather than one list, because they are two kinds of thing — a state and a set
of actions — but one line, because two stacked strips of controls over a text box is a wall.
Declared HERE, in the static shell, rather than created by the composer — because two families
need them and neither may assume the other has run. `#composer` is filled by the composer modules on
the first state push, so a page that has had a session payload but no state yet would otherwise
have a dashboard whose view buttons exist and are attached to nothing.
the composer modules MOVES the row to its final place (under the reset line, above the prompt box); if
it never runs it simply stays here, which is the same dock and still works.

### `</div>`

/#work

## src/main/resources/META-INF/claude-git.xml

### `<idea-plugin>`

Optional-dependency descriptor, loaded by the IntelliJ Platform ONLY when the bundled Git plugin (id
`Git4Idea`) is present and enabled. Its sole purpose is to satisfy the `config-file` requirement of the
optional <depends> in plugin.xml; that <depends> is what grants our plugin runtime classloader access to
`git4idea.*` (the Gradle `bundledPlugin("Git4Idea")` dependency only covers compilation), and transitively to
the platform modules Git4Idea itself declares — `intellij.platform.vcs.dvcs` (com.intellij.dvcs.repo.Repository)
and the VCS-log modules (com.intellij.vcs.log.VcsFullCommitDetails and its supertypes).

DELIBERATELY OPTIONAL, unlike `com.intellij.modules.jcef`. The chat UI *is* a JCEF page, so a missing browser
leaves nothing to degrade to and that dependency is hard. Git is the opposite: an IDE without the Git plugin,
or a project that is not a Git working copy, must still load this plugin and run every chat — the Git surface
simply reports "unavailable" (see GitHistoryService.isAvailable). No extra extensions are needed: the Git
service is registered by the @Service annotation and every entry point checks availability first, so this
descriptor is intentionally empty.

## src/main/resources/META-INF/claude-github.xml

### `<idea-plugin>`

Optional-dependency descriptor, loaded by the IntelliJ Platform ONLY when the bundled GitHub plugin (id
`org.jetbrains.plugins.github`) is present and enabled. It satisfies the `config-file` requirement of the
optional <depends> in plugin.xml, which grants runtime classloader access to `org.jetbrains.plugins.github.*`
(the Gradle `bundledPlugin("org.jetbrains.plugins.github")` dependency only covers compilation).

DELIBERATELY OPTIONAL: an IDE without the GitHub plugin must still load this plugin; the pull-request tools
answer "not available" (see GitHubGateway.isAvailable). No extensions are registered here on purpose.

## src/main/resources/META-INF/claude-terminal.xml

### `<idea-plugin>`

Optional-dependency descriptor, loaded by the IntelliJ Platform ONLY when the bundled Terminal plugin
(org.jetbrains.plugins.terminal) is present. Its sole purpose is to satisfy the `config-file` requirement of
the optional <depends> in plugin.xml; that <depends> is what grants our plugin runtime classloader access to
TerminalToolWindowManager (the Gradle bundledPlugin dependency only covers compilation). No extra extensions
are needed — TerminalLauncher calls the API directly — so this descriptor is intentionally empty.

## src/main/resources/META-INF/plugin.xml

### `<vendor url="https://github.com/serialexperimentslainnnn/claude-code-for-jetbrains">lain</vendor>`

`email` is an optional attribute and is deliberately omitted: a plugin descriptor is a public
document, and an address published there is scraped, not contacted. Support and vulnerability
reports both route through the repository (issues / private security advisories).

### `<depends>com.intellij.modules.platform</depends>`

Works in any IntelliJ-Platform IDE (IDEA, PyCharm, …).

### `<depends>com.intellij.modules.jcef</depends>`

The embedded browser. NOT optional and NOT a nicety: the entire chat UI is a JCEF page since 4.0.0,
so without it this plugin has no interface at all.

REQUIRED since 262, where the platform moved JCEF OUT of the core and into a bundled plugin of its
own (`plugins/jcef-plugin`, id `com.intellij.modules.jcef`, "Web Browser (JCEF)"). A plugin that does
not declare it no longer gets those classes in its classloader, and every chat dies on
`NoClassDefFoundError: com/intellij/ui/jcef/JBCefApp` at `JcefHost.<init>` — reported against 5.1.1 on
IU-262.9437.185, and the same break hit AsciiDoc, Cline and others on the 2026.2 line.

Verified build by build, because this is exactly what decides the floor: on 251/252 the id does not
exist at all (JBCefApp ships inside `lib/app-client.jar`), on 253 and 261 the platform declares
`<module value="com.intellij.modules.jcef"/>` in `product-backend.jar`, and on 262 it is the bundled
plugin. So declaring it costs 2025.1 and 2025.2 — see `sinceBuild` in build.gradle.kts — and buys back
2026.2, where the plugin is currently unusable rather than degraded.

### `<depends optional="true" config-file="claude-terminal.xml">org.jetbrains.plugins.terminal</depends>`

Bundled IDE Terminal: grants runtime classloader access to TerminalToolWindowManager so we can open an
interactive `claude login` session (the OAuth flow needs a TTY the stream-json process doesn't have).
Optional + config-file so the plugin still loads on a (hypothetical) IDE without Terminal; TerminalLauncher
additionally guards with PluginManager.isPluginInstalled.

### `<depends optional="true" config-file="claude-git.xml">Git4Idea</depends>`

Bundled Git plugin: grants runtime classloader access to `git4idea.*` (GitRepositoryManager,
GitHistoryUtils) so the plugin can read the project's branch, recent commits and changed files.
OPTIONAL on purpose — the opposite call to the JCEF one above. There is no browser-less mode, but there
very much is a Git-less one: an IDE without the Git plugin, or a project that simply is not a working
copy, must still load this plugin and run every chat. `GitAvailability`/`GitHistoryService.isAvailable`
gate every entry point, and `GitGateway` is the ONLY file that names a git4idea type, so nothing can be
class-loaded when the dependency is unsatisfied.

### `<depends optional="true" config-file="claude-github.xml">org.jetbrains.plugins.github</depends>`

Bundled GitHub plugin: grants runtime classloader access to `org.jetbrains.plugins.github.*` so the
pull-request tools can ask the IDE's own accounts and API. Optional like Git: `GitHubGateway` is the
only file naming those types and checks the plugin first, so a missing GitHub plugin degrades to
"not available".

### `<depends optional="true" config-file="claude-java.xml">com.intellij.modules.java</depends>`

Bundled Java plugin: grants runtime classloader access to UAST (`org.jetbrains.uast.*`), which ships inside
it. Optional: PyCharm and the other IDEs without Java load this plugin without the uast domain;
`JavaAvailability` gates the only file that names those types.

### `<editorNotificationProvider implementation="dev.lain.claudejb.controller.mcp.tools.code.BannerProvider"/>`

The banner Claude can show over a file's editor (presence ▸ banner_show); it reads BannerRegistry.

### `<toolWindow id="Claude Code"`

Right-hand tool window, next to where the JetBrains AI Assistant lives.

### `<projectConfigurable`

groupId="root" puts these at the top level of the settings tree. Without it the platform files a
configurable under "Other Settings", which is where a plugin nobody is looking for belongs; this
one is opened on purpose, and the security page is opened while something is being blocked.

### `<projectConfigurable`

Its own root entry rather than a child of the page above: every control on it decides what may
reach the machine, and SensitiveGuard.SETTINGS_PATH sends the user straight here from a block.

### `<listener class="dev.lain.claudejb.view.diff.DiffTabCleanup"`

Our diff tabs are mock:// in-memory previews the IDE can never restore; drop them before the
workspace state is written. ProjectCloseListener is an APPLICATION-bus topic. See DiffTabCleanup.
