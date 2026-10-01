# Contributing to woke.wtf Lite

## What belongs here

This mod is a client-side quality-of-life tool, and the boundary is the point
of the project. A change belongs here only if it changes what *your* client
shows or does for *you*. A change does not belong here if it:

- gives a gameplay advantage, or acts for the player (combat, movement,
  anything seen through walls, reach, auto-clicking);
- crafts, alters or replays network packets;
- tries to get past a server's own rules, a ban, or a permission check.

The full list is [docs/SCOPE.md](docs/SCOPE.md). If a change would need any of
the above to be useful, it is out of scope however small it is.

Within that boundary the mod is deliberately plain: a normal jar against the
public Fabric API. No injection, no native libraries, no bytecode rewriting.

## The rules the code is held to

These are mechanical, and the build and CI enforce them:

- No source file reaches 200 lines. If one is close, split it.
- The mod's own sources compile with zero warnings.
- Every class carrying pure logic has JUnit 5 tests. Pure logic lives in
  `src/main/java` and must not need a Minecraft class to run; the tests are
  dependency-free and headless, so `./gradlew test` needs no display.
- Mixins are not free. There are three, each approved and named in
  `src/client/resources/wokewtf-lite.client.mixins.json`. Adding one needs a
  reason that a normal Fabric API hook cannot serve.
- Every user-visible string is a translation key in
  `src/main/resources/assets/wokewtf-lite/lang/en_us.json`, never a literal in
  code. A key with no translation silently shows the raw key on screen, which
  is why the tests pin the language file.

## Building and testing

```bash
cd fabric-mod
./gradlew build              # compile, run every test, build the jar
./gradlew test               # the unit suite only
./gradlew runClient          # a development client with the mod loaded
./gradlew runClientGameTest  # the automated client gametest
```

What the two automated layers cover, and how to run the gametest headlessly, is
in [docs/TESTING.md](docs/TESTING.md). What they cannot cover is
[MANUAL_TEST_CHECKLIST.md](MANUAL_TEST_CHECKLIST.md) — a green build is not a
substitute for it.

## Commits and pull requests

One change per commit, in the imperative: *"Report only keybind conflicts that
involve our own bindings"*, not *"Update"*. Say why the change is needed in the
body when the reason is not obvious from the diff.

A pull request should say what it changes, how it was verified (test counts,
and anything manual), and what it deliberately does not do. If it touches the
legacy C++ tree at the repository root, it is almost certainly the wrong
change: that part is frozen.

## Reporting a bug

Use [ISSUE_TEMPLATE.md](ISSUE_TEMPLATE.md) with a test id from the manual
checklist where one applies. At most 20 log lines around the failure, with the
`(wtf Lite)` tag kept. If the mod logged nothing, say so — an empty log is a
result. [docs/DEBUGGING.md](docs/DEBUGGING.md) explains where the log is and
how to read it.
