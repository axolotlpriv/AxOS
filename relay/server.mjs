// AxOS personal relay: a small Wisp server. AxBrowser's AxCore engine tunnels its (already encrypted)
// connections through it. Deploy it anywhere that runs Node 18+ (Render, Fly.io, Railway, a VPS, your PC).
import http from "node:http";
import { server as wisp } from "@mercuryworkshop/wisp-js/server";

const PORT = Number(process.env.PORT || 8080);
const SECRET = (process.env.RELAY_SECRET || "").replace(/[^\w-]/g, "");   // optional: only accept wss://host/wisp/<secret>/
const BASE = SECRET ? `/wisp/${SECRET}/` : "/wisp/";
Object.assign(wisp.options, {
  allow_udp_streams: false,                     // browsers only need TCP
  allow_loopback_ips: false,                    // never let strangers reach this machine's own services
  allow_private_ips: false,
  stream_limit_per_host: 120, stream_limit_total: 600,
  wisp_motd: "AxOS relay",
});

const server = http.createServer((req, res) => {
  res.writeHead(200, { "content-type": "text/plain; charset=utf-8", "access-control-allow-origin": "*" });
  res.end(`AxOS relay is running.\nIn AxBrowser open Settings > Wisp relays > Add, and paste:\n  wss://<this-host>${BASE}\n`);
});
server.on("upgrade", (req, socket, head) => {
  if (!req.url.startsWith(BASE)) { socket.destroy(); return; }
  wisp.routeRequest(req, socket, head);
});
server.listen(PORT, () => console.log(`AxOS relay listening on :${PORT}, path ${BASE}`));
