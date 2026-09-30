# Report a failed manual test

Copy this block into your message and fill it in — one block per failed test.
The test ids come from `MANUAL_TEST_CHECKLIST.md`.

Attach **at most 20 log lines**: the lines around the failure, from
`logs/latest.log`, with the `(wtf Lite)` tag kept. If the mod logged nothing,
say that — an empty log is a result. Where the log lives and how to read it is
described in the README's *Debugging* section.

---

## T-__ — <module or screen name>

**What I did**

The exact steps. Include the value of any setting you changed, and whether the
module was switched on or off when it happened.

**What I expected**

The *Expect* line of that test entry in `MANUAL_TEST_CHECKLIST.md`.

**What happened**

What the game actually showed or did, including "nothing happened" if that is
the truth. Describe what you saw, not what you think the cause is.

**Log lines (max 20)**

```
[06:18:28] [Render thread/INFO] (wtf Lite) ...
```

---

## Notes

- One block per test id. Do not merge several failures into one.
- A crash log, if there is one, is `logs/latest.log`'s neighbouring
  `crash-reports/crash-*.txt`; attach just the top of it instead of 20 lines.
- If the failure is visual only (a widget in the wrong place, wrong colour),
  say so and skip the log lines unless the mod logged something.
