// "three" for vendored ES modules: the kit's own THREE (window.hiroshi.THREE), so the page
// keeps one three.js. Import only after the kit has published window.hiroshi.
// List exactly the names the vendored modules import.
const T = globalThis.hiroshi && globalThis.hiroshi.THREE;
if (!T) throw new Error("three-kit.mjs: the kit's THREE isn't ready (window.hiroshi.THREE)");
export const { Quaternion, Vector3, Group, Euler, Object3D, MeshStandardMaterial, Mesh,
               CapsuleGeometry, SphereGeometry, BoxGeometry } = T;
export default T;
