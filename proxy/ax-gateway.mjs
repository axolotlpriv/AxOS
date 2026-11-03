/* AxGateway: a bare-mux transport that sends AxCore's requests through your personal AxOS gateway
   (the Cloudflare Worker from Settings > Network & Browser) instead of a Wisp relay.
   It uses plain HTTPS to the worker, so it works where WebSocket relays are blocked. Cookies are kept by
   Scramjet and travel in x-ax-cookie; redirects come back as raw 3xx wrapped in a 200 (x-ax-redirect: manual).
   Source: first-party AxOS code. */
const SKIP_REQ = /^(host|content-length|connection|accept-encoding|transfer-encoding|upgrade)$/i;
const SKIP_RES = /^(x-ax-|content-encoding|content-length|transfer-encoding|access-control-)/i;
export default class AxGatewayTransport {
  ready = false;
  constructor(opts = {}) {
    this.base = String(opts.url || "").replace(/\?.*$/, "").replace(/\/*$/, "/"); this.key = opts.key || "";
    if (!/^https?:\/\//i.test(this.base)) throw new TypeError("AxGateway needs the https address of your gateway");
  }
  async init() { this.ready = true; }
  async meta() { }
  async request(remote, method, body, headers, signal) {
    const h = new Headers(); h.set("x-ax-redirect", "manual"); if (this.key) h.set("x-ax-key", this.key);
    for (const [k, v] of Object.entries(headers || {})) {
      const val = Array.isArray(v) ? v.join(", ") : String(v); if (SKIP_REQ.test(k)) continue;
      if (/^cookie$/i.test(k)) h.set("x-ax-cookie", val); else h.set("x-ax-h-" + k.toLowerCase(), val);
    }
    if (body && typeof ReadableStream !== "undefined" && body instanceof ReadableStream) body = await new Response(body).arrayBuffer();
    const res = await fetch(this.base + "?url=" + encodeURIComponent(remote.href), { method, headers: h, body: method === "GET" || method === "HEAD" ? undefined : body, signal, cache: "no-store", credentials: "omit" });
    if (res.status === 403 && !res.headers.get("x-ax-status")) throw new Error("The gateway rejected the key");
    if (res.status >= 500 && !res.headers.get("x-ax-status")) throw new Error("The gateway failed (" + res.status + "): " + (await res.text().catch(() => "")).slice(0, 120));
    const out = {}; res.headers.forEach((v, k) => { if (!SKIP_RES.test(k)) (out[k] = out[k] || []).push(v); });
    const sc = res.headers.get("x-ax-set-cookie"); if (sc) { try { out["set-cookie"] = JSON.parse(sc); } catch (e) { } }
    const status = Number(res.headers.get("x-ax-status")) || res.status;
    return { body: [204, 205, 304].includes(status) ? null : res.body, headers: out, status, statusText: res.statusText };
  }
  connect(url, protocols, requestHeaders, onopen, onmessage, onclose, onerror) {
    const extra = {}; for (const [k, v] of Object.entries(requestHeaders || {})) if (/^(cookie|origin|user-agent)$/i.test(k)) extra[k] = String(v);
    const u = new URL(this.base); u.protocol = u.protocol === "https:" ? "wss:" : "ws:"; u.searchParams.set("url", url.toString()); if (this.key) u.searchParams.set("key", this.key); u.searchParams.set("h", btoa(JSON.stringify(extra)));
    const ws = new WebSocket(u.href, protocols); ws.binaryType = "arraybuffer";
    ws.onopen = () => onopen(ws.protocol || ""); ws.onclose = e => onclose(e.code, e.reason); ws.onerror = () => onerror(""); ws.onmessage = e => onmessage(e.data);
    return [data => ws.send(data), (code, reason) => ws.close(code, reason)];
  }
}
