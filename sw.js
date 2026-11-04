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
self.addEventListener("message", e => { const d = e.data || {}; if (d.axping) e.source && e.source.postMessage({ axpong: 1, scope: SCOPE }); if (d.axcfg && typeof d.axcfg.adblock === "boolean") { adblock = d.axcfg.adblock; caches.open("ax-cfg").then(c => c.put("/__axcfg", new Response(JSON.stringify({ adblock })))).catch(() => { }); } });
const STATIC_TYPE = /^(image\/|font\/|text\/css|(application|text)\/(x-)?(java|ecma)script|application\/(font|vnd\.ms-fontobject|x-font))/i;
function ttlOf(res) {
  const cc = (res.headers.get("cache-control") || "").toLowerCase();
  if (/no-store|no-cache|private/.test(cc)) return 0;
  const m = /(?:s-maxage|max-age)=(\d+)/.exec(cc); if (m) return Math.min(+m[1] * 1000, MAX_TTL);
  const ex = Date.parse(res.headers.get("expires") || ""); if (ex) return Math.max(0, Math.min(ex - Date.now(), MAX_TTL));
  return DEFAULT_TTL;
}
/* ad and tracker blocking: requests (never page navigations) to these hosts get an empty answer.
   Fewer requests also means less traffic through the relay, so pages load faster. Toggle: axos://settings. */
const AD_HOSTS = ["doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com", "googletagmanager.com", "googletagservices.com", "adservice.google.com", "imasdk.googleapis.com", "amazon-adsystem.com", "adnxs.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "scorecardresearch.com", "quantserve.com", "quantcount.com", "moatads.com", "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com", "adsrvr.org", "hotjar.com", "mouseflow.com", "adform.net", "smartadserver.com", "yieldmo.com", "3lift.com", "sharethrough.com", "teads.tv", "media.net", "bidswitch.net", "agkn.com", "demdex.net", "everesttech.net", "krxd.net", "bluekai.com", "adsafeprotected.com", "doubleverify.com", "chartbeat.com", "chartbeat.net", "nr-data.net", "mixpanel.com", "popads.net", "propellerads.com", "exoclick.com", "adsterra.com", "mgid.com", "revcontent.com", "zergnet.com", "advertising.com", "serving-sys.com", "flashtalking.com", "springserve.com", "spotxchange.com", "contextweb.com", "lijit.com", "sovrn.com", "33across.com", "gumgum.com", "indexww.com", "adroll.com", "ads-twitter.com", "ads.linkedin.com", "analytics.tiktok.com", "bat.bing.com", "clarity.ms", "pixel.facebook.com", "an.facebook.com", "googleoptimize.com", "branch.io", "app-measurement.com", "yieldlove.com", "permutive.com", "id5-sync.com", "omnitagjs.com", "adlightning.com", "confiant-integrations.net", "liadm.com", "optable.co", "panxo-sys.com", "panxo.ai", "rlcdn.com", "pippio.com", "tapad.com", "crwdcntrl.net", "adsymptotic.com", "adition.com", "seedtag.com", "onetag-sys.com", "richaudience.com", "connatix.com", "primis.tech", "vidazoo.com", "minutemedia-prebid.com", "sonobi.com", "kargo.com", "undertone.com", "emxdgt.com", "lkqd.net", "tremorhub.com", "unrulymedia.com", "inmobi.com", "adcolony.com", "applovin.com", "unityads.unity3d.com"];
const AD_SET = new Set(AD_HOSTS);
let adblock = true;
const cfgReady = caches.open("ax-cfg").then(c => c.match("/__axcfg")).then(r => r ? r.json() : null).then(c => { if (c && typeof c.adblock === "boolean") adblock = c.adblock; }).catch(() => { });
const isAd = host => { host = String(host || "").toLowerCase(); while (host.includes(".")) { if (AD_SET.has(host)) return true; host = host.slice(host.indexOf(".") + 1); } return false; };
const realURL = u => { try { return new URL(decodeURIComponent(u.pathname.slice(PREFIX.length))); } catch (e) { return null; } };
const report = (event, host) => { const ref = event.request.referrer; if (ref && ref.startsWith(self.location.origin)) { adChan.postMessage({ blocked: host, url: ref }); return; } self.clients.get(event.clientId || event.resultingClientId || "").then(c => { adChan.postMessage({ blocked: host, url: c ? c.url : "" }); }).catch(() => { }); };
const adChan = new BroadcastChannel("axcore");
let puts = 0;
const MAX_BYTES = 200e6;
/* keep the cache under MAX_ENTRIES files and MAX_BYTES in total; oldest first */
async function trim(cache) {
  if (++puts % 40) return; const keys = await cache.keys(); let total = 0; const sizes = [];
  for (const k of keys) { const r = await cache.match(k); const n = Number(r && r.headers.get("x-ax-size")) || 0; sizes.push(n); total += n; }
  for (let i = 0; i < keys.length && (keys.length - i > MAX_ENTRIES || total > MAX_BYTES); i++) { await cache.delete(keys[i]); total -= sizes[i]; }
}
async function handle(event) {
  await scramjet.loadConfig();
  if (!scramjet.route(event)) return fetch(event.request);
  const req = event.request;
  await cfgReady;
  /* subresources, and iframes nested inside a proxied page (ad slots); never the tab's own page */
  const nested = req.mode !== "navigate" || (req.referrer || "").startsWith(self.location.origin + PREFIX);
  if (adblock && nested) { const real = realURL(new URL(req.url)); if (real && isAd(real.hostname)) { report(event, real.hostname); const d = req.destination; return new Response(d === "script" ? "/* blocked by AxBrowser */" : "", { status: 200, headers: { "content-type": d === "script" ? "text/javascript" : d === "style" ? "text/css" : d === "iframe" || d === "document" ? "text/html" : "text/plain" } }); } }
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
        const h = new Headers(copy.headers); h.set("x-ax-at", String(Date.now())); h.set("x-ax-ttl", String(ttl)); h.set("x-ax-size", String(body.byteLength));
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
