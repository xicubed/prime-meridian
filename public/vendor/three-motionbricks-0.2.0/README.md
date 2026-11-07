# three-motionbricks 0.2.0 (vendored)

NVIDIA MotionBricks on three.js, by repalash: https://github.com/repalash/three-motionbricks
Built files `dist/index.mjs` and `dist/motionbricks.worker.js` from commit
`0dfe1929dc513a636643422420fe53a0646e4a1f` (Oct 31, 2026), unmodified except the
source-map comment removed. Code: Apache-2.0 (`LICENSE`).

Not vendored: the model. Its weights are under the NVIDIA Open Model Agreement and are
downloaded at runtime (a ~200 MB "lite" pack of the SOMA human model). The Smart Primitive
clips (sit, jump, vault) are under NVIDIA's Sample Data Evaluation License: evaluation only,
never shipped.

`import ... from "three"` resolves through the page's import map to `../three-kit.mjs`,
which re-exports the kit's own THREE (one three.js per page).
