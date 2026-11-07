# three.js addons r186 (vendored)

`loaders/GLTFLoader.js` and the two utils it imports, copied unmodified from the `three`
0.186.1 npm package (`examples/jsm/`). MIT (`LICENSE`). Their `from 'three'` resolves
through the page's import map to `../three-kit.mjs`, the kit's own THREE.
