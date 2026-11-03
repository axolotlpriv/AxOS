# AxOS personal relay

AxCore (the service-worker engine in AxBrowser) sends its traffic through a **Wisp relay**. Public relays come and go,
so you can run your own. This is a ~25 line Node server around [wisp-js](https://github.com/MercuryWorkshop/wisp-js)
(LGPL-3.0). The relay only ever sees TLS-encrypted bytes, because encryption ends inside your browser.

> Status: this server is a thin wrapper around wisp-js's documented server API. It hasn't been run end to end by the AxOS author yet, so test it once before relying on it.

## Run it

```sh
cd relay
npm install
RELAY_SECRET=pick-a-long-random-word npm start      # listens on :8080
```

Then, in AxBrowser: **Settings > Wisp relays**, add `wss://your-host/wisp/pick-a-long-random-word/`.
`RELAY_SECRET` keeps strangers from using your relay (and your bandwidth). Leave it unset for an open relay at `/wisp/`.

## Deploy for free

- **Render:** New > Blueprint, point it at this repository (copy `relay/render.yaml` to the repository root first). It generates the secret for you.
  A free instance sleeps when idle, so the first request after a while can take about a minute.
- **Fly.io / Railway / Koyeb:** deploy the `relay` folder (a `Dockerfile` is included).
- **Your own PC:** run it as above and expose it with a tunnel.

The server refuses connections to loopback and private addresses, so it can't be used to reach services on the machine it runs on.
