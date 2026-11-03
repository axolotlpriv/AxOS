/* AxOS service worker: the AxCore engine (Scramjet) runs through this file.
   It only touches requests for /service/ pages and the Scramjet WebAssembly file; everything else is left alone.
   It also keeps a small HTTP cache of static files (scripts, styles, images, fonts) so revisiting a site doesn't
   pull them through the relay again. The cache respects the site's own max-age and is capped in size. */
const SCOPE = new URL(self.registration.scope).pathname;
const PREFIX = SCOPE + "service/";
const WASM = SCOPE + "proxy/scramjet.wasm.wasm";
const CACHE = "ax-http-v1", MAX_ENTRIES = 900, MAX_BODY = 6e6, MAX_TTL = 7 * 864e5, DEFAULT_TTL = 10 * 60e3;
importScripts(SCOPE + "proxy/scramjet.all.js");
const { ScramjetServiceWorker } = $scramjetLoadWorker();
const scramjet = new ScramjetServiceWorker();
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", e => e.waitUntil(self.clients.claim()));
self.addEventListener("message", e => { if (e.data && e.data.axping) e.source && e.source.postMessage({ axpong: 1, scope: SCOPE }); });
const STATIC_TYPE = /^(image\/|font\/|text\/css|(application|text)\/(x-)?(java|ecma)script|application\/(font|vnd\.ms-fontobject|x-font))/i;
function ttlOf(res) {
  const cc = (res.headers.get("cache-control") || "").toLowerCase();
  if (/no-store|no-cache|private/.test(cc)) return 0;
  const m = /(?:s-maxage|max-age)=(\d+)/.exec(cc); if (m) return Math.min(+m[1] * 1000, MAX_TTL);
  const ex = Date.parse(res.headers.get("expires") || ""); if (ex) return Math.max(0, Math.min(ex - Date.now(), MAX_TTL));
  return DEFAULT_TTL;
}
let puts = 0;
async function trim(cache) { if (++puts % 40) return; const keys = await cache.keys(); for (let i = 0; i < keys.length - MAX_ENTRIES; i++) await cache.delete(keys[i]); }
async function handle(event) {
  await scramjet.loadConfig();
  if (!scramjet.route(event)) return fetch(event.request);
  const req = event.request;
  if (req.method !== "GET" || req.mode === "navigate" || req.headers.has("range") || req.cache === "no-store" || req.cache === "reload") return scramjet.fetch(event);
  let cache = null; try { cache = await caches.open(CACHE); } catch (e) { return scramjet.fetch(event); }
  const hit = await cache.match(req.url);
  if (hit && Date.now() - Number(hit.headers.get("x-ax-at")) < Number(hit.headers.get("x-ax-ttl"))) return hit;
  const res = await scramjet.fetch(event);
  try {
    const ttl = res.status === 200 ? ttlOf(res) : 0, type = res.headers.get("content-type") || "", len = Number(res.headers.get("content-length") || 0);
    if (ttl > 0 && STATIC_TYPE.test(type) && len < MAX_BODY && !res.headers.has("set-cookie")) {
      const copy = res.clone();
      event.waitUntil((async () => {
        const body = await copy.arrayBuffer(); if (body.byteLength > MAX_BODY) return;
        const h = new Headers(copy.headers); h.set("x-ax-at", String(Date.now())); h.set("x-ax-ttl", String(ttl));
        await cache.put(req.url, new Response(body, { status: 200, statusText: copy.statusText, headers: h })); await trim(cache);
      })().catch(() => { }));
    }
  } catch (e) { }
  return res;
}
self.addEventListener("fetch", event => {
  const u = new URL(event.request.url);
  if (u.origin !== self.location.origin || !(u.pathname.startsWith(PREFIX) || u.pathname === WASM)) return;
  event.respondWith(handle(event));
});
