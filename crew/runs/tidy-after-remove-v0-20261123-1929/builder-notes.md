# Builder notes — tidy-after-remove-v0 (T-16)

What to look at headless:

- No lobby post/board/label at the cottage steps (`[:lobby :box]` absent from the scene);
  the crew key's Lobby row still flies to the cottage; `bb hal-say --at lobby` still walks there.
- Up close under a tall build (e.g. the lamp post as a trial, camera at its foot looking
  up): the card sits `card-pin-gap-px` (8px) below the canvas top instead of vanishing;
  pan the build off the left/right/bottom or turn away: hidden as before.
- A real build's `sign-text` reads without "proposed:" (and see the note in §C.2 of the
  spec: real builds draw no sign; the removal preview keeps its "proposed:" first line
  by design).
