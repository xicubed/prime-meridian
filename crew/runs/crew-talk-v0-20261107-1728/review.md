# Review: crew-talk-v0

Security-sensitive round (a local HTTP bridge that can approve a crew member's shell command) —
reviewed accordingly: every claim below is checked against the actual code, not inferred from
the spec's description of what it's supposed to do.

Gate re-checked against `gate-1.txt`: PASS, 0 warnings, 513 tests / 2483 assertions (up from
468/2120 at the end of `crew-dots-v0`: +45 tests, exactly `test/pm/crew_talk_test.cljs`'s
`t01`–`t45`). The round's first gate attempt genuinely **FAILED** (`t21-validate-check-order`
expected 401, got 403 — `validate` was checking Origin before the token in one path) and was
fixed before the final PASS; I independently re-verified the fix landed correctly (below), rather
than trusting the gate note that it was resolved.

**Scope note.** `src/pm/bricks.cljs`, `src/pm/scene.cljs` and `src/pm/three.cljs` all show as
changed, but none are in this round's "Files and owners" table, and `log.md`'s manager notes
attribute them to a live, mid-round request from Michael ("nameplates read at a distance") that
Hal implemented directly — confirmed by reading the diffs: a new `pm.scene/plate` style map and a
`set-label-runs!`-adjacent canvas/font resize, nothing touching the bridge, tokens, or crew data.
Excluded from this review. `.gitignore` and `shadow-cljs.edn` (binding the dev server to
`127.0.0.1` and disabling the shadow-cljs HUD) predate this round's `spec.md` per their mtimes and
are explicitly called out in `log.md` as Hal's own pre-round security groundwork, not the
builder's — the builder's one touch of `shadow-cljs.edn` during the build was restoring Hal's own
backed-up version after a scratch test, not introducing it.

## Security checklist (each independently verified against the code, not the spec's prose)

- **127.0.0.1 only:** `scripts/crew_bridge.clj` calls `http/run-server` with `{:ip b/host ...}`
  where `pm.bridge/host` is the literal `"127.0.0.1"` — never `"0.0.0.0"` or unbound.
- **Token and exact Origin checked before anything:** `validate`'s `cond` order is method → URI
  shape → body-is-a-map → **token** → **origin** → member → action → prompt-id → text, matching
  `t21`'s six assertions exactly (bad-token+bad-origin → 401, not 403; good-token+bad-origin+
  unknown-member → 403, not 404; good-everything+unknown-member+bad-action → 404, not 400;
  good-member+bad-action+missing-prompt-id → 400 "bad action"; bad-method+bad-token → 405 first).
  I re-derived this order by hand from the live `src/pm/bridge.cljc`, not by trusting the test
  name. Both checks use exact comparisons: `token=` requires equal-length strings (and a `nil` ctx
  token always fails closed, so an unconfigured bridge refuses everything), and origin is `(not=
  origin allowed-origin)` — no prefix/regex/contains matching either side.
- **The member must be in crew.json:** `ctx`'s `:members` is `(set (keys (b/roster (crew-json))))`,
  re-read from `.crew/crew.json` **on every request** (not cached at server start), so a disabled
  or removed member is rejected immediately, not just until the next restart.
- **Allow only when blocked, the hash equals prompt-id, and the prompt is neither risky nor
  unknown:** `decide`'s `:allow` branch requires, in order, `(= :blocked (:status live))`, the
  live prompt's `:id` to equal the request's `prompt-id` (exact string match), `(= :bash (:kind
  p))`, and `(false? (:risky p))` (strict — a missing/nil `:risky` is **not** allowable, fails
  closed). `live` itself is fetched fresh via `herdr agent list`/`read` inside the request, not
  trusted from the client body or from `crew.edn`.
- **Risky is decided in the bridge:** `pr/prompt-of`/`risky?` run inside `live-for`, server-side,
  against the just-read screen text — the client's own copy of `:risky` (in its cached `crew.edn`)
  is never consulted by `decide`. Belt-and-suspenders: the browser-side `pm.talk/crew-send` also
  independently refuses to even transition an `:allow` action to `:sending` unless
  `(allowable? e)` (bash + `:risky false`) is true, so a buggy or compromised renderer can't reach
  `post-crew!` for a risky prompt either — but the authoritative check is the bridge's, confirmed
  above.
- **No shell:** every `b/step-argv` branch returns a plain `[herdr "agent" ...]` string vector, no
  shell-string variant exists at all (`t27` checks `[:shell "ls"]` → `nil`), and
  `scripts/crew_bridge.clj`/`live-for`/`run-decided!` call it only through `babashka.process/sh`
  with that vector — a `:prompt` step inserts a literal `"--"` before the text so a value like
  `"--wait"` can't be read as a flag by `herdr`'s own argv parser (`t27`). Member and text never
  pass through anything that could be shell-interpreted.
- **Length caps:** request body capped at 4096 bytes, checked on raw UTF-8 byte length **before**
  JSON parsing even starts (`scripts/crew_bridge.clj`); `validate-text` caps at 1000 chars (deny
  notes, say text) after rejecting any C0/DEL control character; `pm.prompt/cap-text` separately
  caps what crew_status.clj ever writes into `crew.edn` at 400 chars — two independent caps at two
  different trust boundaries, not the same check reused.
- **The log never holds the token:** `log-line`'s signature is `(iso member action status
  reason)` — there is no parameter through which a token or arbitrary request body could flow,
  so this is a structural guarantee, not just "the code happens not to log it." `t28` confirms the
  rendered line never contains the live token string. `reason` strings used at every call site are
  always one of `decide`'s fixed phrases or the literal `"herdr failed"`/`"too large"` — never
  echoed request content.
- **Nothing beyond names, states and the parsed prompt leaves a screen:** `scripts/crew_status.clj`'s
  updated docstring states this explicitly, and the code only ever writes `:as-of`, `:agents`
  (status + an optional 4-key prompt map), and `:titles` — the raw screen text itself is consumed
  only by `pm.prompt/prompt-of` to produce that 4-key shape and is never written out.
  `pm.crew/parse` additionally re-validates on the read side: an agent's `:prompt` is kept only
  when `(valid-prompt? p)` (id regex, `:kind` ∈ `#{:bash :unknown}`, text ≤400 chars, `:risky`
  boolean) and is always rebuilt via `select-keys` to exactly those 4 keys, so even a
  hand-crafted or buggy `crew.edn` can't smuggle extra fields into the game.

## Other checks

- **No member sent keys or prompts to the crew during this round:** no `crew/bridge.log` exists in
  the tree, and nothing in `crew/runs/crew-talk-v0-20261107-1728/replies/*.txt` shows the builder
  or tester actually invoking `herdr agent send-keys`/`herdr agent prompt`, or starting
  `bb crew-bridge` against the live roster — all 45 tests exercise `pm.bridge`/`pm.prompt`'s pure
  functions directly, never a running server.
- **Pure logic tested:** `pm.bridge.cljc` and `pm.prompt.cljc` are both free of atoms, `js/Date`,
  and I/O (confirmed by reading them end to end); every function has direct test coverage, and I
  independently re-derived `validate`'s check order and `allowable?`'s `false?`-strictness from
  the code itself rather than taking the test suite's word for it.
- **Names from the roster:** no walker/agent id (`un-automate-monde`, `le-testeur`, etc.) appears
  in operative code in any touched file. The only hits are two illustrative examples inside
  `scripts/crew_status.clj`'s docstring comment — unchanged by this round (present in the same
  form before it) and not executable code.

## Finding (non-blocking, worth fixing)

Spec §3.3 point 8 says a failed `:wait-idle` step should skip the `:prompt` step and **turn the
reply's reason into** `"declined; note not sent (still busy)"`, still with a 200. The code
(`run-decided!`) does skip the prompt step correctly, but the final `{:ok true :reason (:reason
decision)}` always reuses `decision`'s original reason ("declined") — the "note not sent" text is
never produced anywhere, so a denial whose note silently didn't get typed in (because the member
went busy) looks identical in the UI to one where it did. Not a security issue — the log and the
bridge's actual behavior (skip the step, still succeed) are both correct — just a user-facing gap
against the written spec.

## Verdict

**Pass.** Every item on the review's security checklist holds up under direct inspection of the
code, not just the spec's description of it, and the one real spec deviation found (the missing
"note not sent" reason text) is cosmetic, not a security or correctness problem.
