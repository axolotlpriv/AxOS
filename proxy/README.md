# AxCore proxy files

These files power **AxCore**, the service-worker engine in AxBrowser. They are copies of
published npm packages, pinned to exact versions:

| File | Package | Version | License |
| --- | --- | --- | --- |
| `scramjet.all.js`, `scramjet.sync.js`, `scramjet.wasm.wasm` | [@mercuryworkshop/scramjet](https://github.com/MercuryWorkshop/scramjet) | 1.1.0 | AGPL-3.0 |
| `bare-mux.js`, `bm-worker.js` | [@mercuryworkshop/bare-mux](https://github.com/MercuryWorkshop/bare-mux) | 2.1.9 | AGPL-3.0 |
| `libcurl-transport.mjs` | [@mercuryworkshop/libcurl-transport](https://github.com/MercuryWorkshop/libcurl-transport) | 1.5.2 | AGPL-3.0 |

Source code for each lives at the repositories above. AxOS changes one line: in `scramjet.all.js`, WebSocket `onopen`/`onmessage`/`onclose`/`onerror` handlers are called with the socket as `this` (Scramjet 1.1.0 called them on an internal object, which broke games such as slither.io that call `this.close()`).
Open `index.html` over `http://` or `https://` (GitHub Pages, any static host, or `localhost`) with `sw.js`
and this folder beside it. When it's opened as a local file, AxBrowser falls back to the in-page AxProxy engine.
