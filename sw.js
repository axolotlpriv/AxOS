/* AxOS service worker: the AxCore engine (Scramjet) runs through this file.
   It only touches requests for /service/ pages and the Scramjet WebAssembly file; everything else is left alone. */
const SCOPE = new URL(self.registration.scope).pathname;
const PREFIX = SCOPE + "service/";
const WASM = SCOPE + "proxy/scramjet.wasm.wasm";
importScripts(SCOPE + "proxy/scramjet.all.js");
const { ScramjetServiceWorker } = $scramjetLoadWorker();
const scramjet = new ScramjetServiceWorker();
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", e => e.waitUntil(self.clients.claim()));
self.addEventListener("message", e => { if (e.data && e.data.axping) e.source && e.source.postMessage({ axpong: 1, scope: SCOPE }); });
self.addEventListener("fetch", event => {
  const u = new URL(event.request.url);
  if (u.origin !== self.location.origin || !(u.pathname.startsWith(PREFIX) || u.pathname === WASM)) return;
  event.respondWith((async () => {
    await scramjet.loadConfig();
    if (scramjet.route(event)) return scramjet.fetch(event);
    return fetch(event.request);
  })());
});
