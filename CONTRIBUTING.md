# AxOS

A desktop operating system that runs in your browser from **one file**: `index.html`. You don't need a server or a build step. Open the file and it boots.

## Running it

- **Hosted (recommended):** put `index.html`, `sw.js` and the `proxy/` folder on any static host: Cloudflare Pages, GitHub Pages, Netlify, or `localhost`. Hosted over `http(s)`, AxBrowser uses AxCore, which runs almost any website.
- **Locally:** double-click `index.html`, or drag it into Chrome, Edge or Firefox. Everything works, but AxBrowser falls back to the simpler AxProxy engine, because browsers don't run service workers for local files.

**Cloudflare Pages:** Workers & Pages → Create → Pages → Connect to Git, pick this repository, leave the build command empty and set the output directory to `/`. **GitHub Pages:** Settings → Pages → deploy from `main`, `/ (root)`. Either way, nothing else is needed.

Your files and settings are saved in the browser (IndexedDB and localStorage), so they're still there after a reload. To move AxOS to another browser, use **Settings → System → Storage → Back up AxOS**.

## What's included

| Area | Features |
| --- | --- |
| **Shell** | Boot screen, first-run setup, lock screen with optional password, desktop icons (drag, rubber-band select, rename, drop files from your computer), right-click menus, taskbar with window previews and pinning, Start menu with search, quick settings, calendar and notification center, Task View, window switcher, Run dialog |
| **Windows** | Drag, resize from all edges, maximize, minimize, snap to halves, thirds or quarters (drag to an edge, press Alt+Arrow, or hover the maximize button for snap layouts), always-on-top |
| **Phones & tablets** | Windows open full-screen on small screens, the taskbar compacts itself to fit, apps adapt to their window size, long-press opens context menus, and a single tap opens icons |
| **File Explorer** | Breadcrumb address bar, search, grid and details views, preview pane, cut/copy/paste, drag and drop, Recycle Bin, upload and download, properties |
| **AxBrowser** | A full tabbed browser: pinned and draggable tabs, smart address bar, find in page, zoom per site, developer tools, downloads, history, bookmarks, and four connection engines (see below) |
| **Terminal** | Bash-like shell with pipes, redirects, `&&`/`||`, `$(...)`, variables, tab completion and 100+ commands. Type `cmd` for a Command Prompt that runs `.bat` files |
| **AxCompat** | Opens Windows `.exe` (PE32/PE32+) and Linux ELF programs (see below) |
| **Remote Desktop** | Streams and controls your PC through the AxOS Companion app (`companion/`). Gaming mode and work mode, hardware video encoding, controller passthrough, clipboard sync |
| **AxOS Cloud** | Accounts and end-to-end encrypted cloud saves of your files, settings, games and saved PCs |
| **Settings** | Wallpapers, accent color, dark/light/auto theme, night light, brightness, scale, taskbar, accounts, default apps, network/proxy, accessibility, backup and restore, factory reset |
| **Apps** | Notepad (with Markdown preview), Calculator, Paint, Photos, Media Player, Task Manager, Clock, Weather, AxOS Store |
| **AxPlay** | Steam-style game launcher: store, library, downloads, play time, Big Picture mode with gamepad support, Proton-style compatibility layer for Windows games, native Linux games |
| **Geometry Dash** | Your [Geometry-Dash-webport](https://github.com/axolotlpriv/Geometry-Dash-webport), loaded live inside a window |
| **Skyline Coaster Studio** | Your [Skyline](https://github.com/axolotlpriv/Skyline) roller-coaster builder. It opens the latest `index.html` from the repo each time (cached for offline use), and its saves are kept in AxOS |
| **AxFlix** | Browse about 600 popular movies and series, search any title, and see where to watch it in your country (streaming, free, rent, buy), with trailers |
| **AxTube** | A YouTube client with search, trending, channels, subscriptions, history, watch-later and comments. No Wisp |
| **AxTok** | A vertical short-video feed with like, save and pasted links. No Wisp |
| **Store extras** | Sticky Notes. Install it from the Store or with `apt install` |

## Personalization

AxOS ships with a soft plum-and-pink **AxOS Default** theme. The AxOS logo is three rounded squares and a tilted diamond, and it takes on your accent color everywhere it appears, including the browser-tab icon. The old blue look is available as the **Classic Blue** theme.

**Settings → Personalization** has:
- 11 one-click themes (Midnight Neon, Sakura, Retro Terminal, Ocean Glass and more)
- 23 picture wallpapers, 8 live animated wallpapers, a time-of-day sky, and a slideshow from any folder
- Accent colors, glass opacity, blur strength and color filters
- Window corner radius, shadows, title-bar buttons on the left or right, accent title bars, open animations and speed
- A full-width taskbar or a floating dock, in three sizes
- Icon shape and style, 9 fonts, 6 mouse pointers and 5 sound schemes
- Lock screen background and clock style
- Desktop widgets: clock, calendar, weather, system monitor, notes, photo frame, quote, battery
- Screen savers
- Emoji avatars
- Custom CSS
- Exporting and importing `.axtheme` files

You can also right-click the desktop to change the wallpaper or theme, or to add a widget.

## Motion

AxOS uses spring physics for its animations. It simulates a spring and turns the result into CSS `linear()` easing curves, falling back to cubic-bézier curves in older browsers.

- **Windows:**
  - Open by zooming out of wherever you clicked, with a blur-in.
  - Minimize into their taskbar icon and restore back out of it.
  - Maximizing, restoring and snapping smoothly morph the window between sizes.
- **Menus and panels:**
  - The Start menu, flyouts, menus, dialogs, toasts and tooltips spring in.
  - List items ripple in one after another.
- **Screen changes:**
  - Theme and wallpaper changes crossfade the whole screen.
  - Unlocking zooms the desktop into focus as the taskbar glides up.

Animations only move, fade or blur things, so they run on the GPU without making the page recalculate layout. **Settings → Personalization → Windows** sets the animation speed. Turning off **Animations**, or setting reduced motion in your operating system, disables them.

## AxPlay and games

AxPlay is an original Steam-style launcher. It isn't Steam, and it doesn't connect to Steam.

**Launching games:**
- **Windows `.exe` games** start through AxCompat's Proton-style layer. You can choose the compatibility tool for each game under Properties.
- **Linux ELF and AppImage games** start through the "AxPlay Linux Runtime".
- **Web and AxOS games** run directly.

As with AxCompat, only games that carry an AxOS payload actually run. Two examples are in the store: **Neon Drift**, a native Linux ELF game, and **Orbital**, a Windows PE game. Other native binaries run in simulation mode.

**Adding your own games:** in the library, choose **Add a game** to add an `.exe`, a Linux binary, an `.html` game or a web URL. You can also drop games into `~/Games` and scan for them.

**Games only open from AxPlay.** They don't appear in Start, All apps, search, Run, the terminal or on the taskbar, so everything playable lives in one place. (An app marked `gameOnly` in the source gets this treatment.)

**Real artwork:** Geometry Dash uses its official Steam art (a square crop of the store capsule for the icon and the library banner for the hero). Minecraft Classic uses Mojang's official Creeper-face icon, embedded as a tiny image, and the game's wordmark on its banner. Artwork is loaded from the publishers' own servers at runtime, apart from that one small icon, and falls back to a plain icon if offline.

**Web games:** the store lists about 1,475 free web games. 102 are hand-picked, each embedded from its creator's or publisher's official site:

| Category | Games |
| --- | --- |
| Action & arcade | PolyTrack, HexGL, Q1K3, Underrun, Xibalba, X-Type, Biolab Disaster, Z-Type, Radius Raid, Space Huggers, Swooop |
| Multiplayer | Starblast.io, Survev.io, Suroi.io, Wormate.io, Taming.io, EvoWorld.io, Gats.io, Kour.io, Arras.io, Krunker, Shell Shockers, Smash Karts, Kirka, Deadshot, EV.IO, Venge, Repuls, Rocket Bot Royale, Slither.io, Diep.io, Mope.io, Wings.io, Paper.io 2, Hexanaut, Defly, Zombs, Tribals, YoHoHo, Lordz, Tanx, generals.io, Territorial.io, skribbl.io, Papergames |
| Sandbox & creative | Minecraft Classic, Bloxd.io, shapez, Sandspiel, Sandboxels, Line Rider, Orb.Farm, Townscaper |
| Puzzle | Astray, Hextris, Untrusted, Minesweeper Online, Little Alchemy 2, Solitaire, and Simon Tatham's Net, Bridges, Loopy, Pattern, Galaxies, Untangle, Keen, Light Up, Signpost, Towers and Inertia |
| Word & daily | Worldle, Nerdle, Globle, Framed, Semantle |
| Idle | A Dark Room, Universal Paperclips, Candy Box 2, Particle Clicker, Bitburner, Gridland, Kittens Game, Trimps, Antimatter Dimensions, Swarm Simulator, Evolve, Progress Knight, The Prestige Tree |
| Music | Patatap, Chrome Music Lab, Incredibox (demo) |
| Play & learn | The Evolution of Trust, Adventures with Anxiety, Elevator Saga, Flexbox Froggy, Grid Garden, CSS Diner, Learn Git Branching, SQL Murder Mystery, NandGame, CodeCombat, Keybr |

**1,369 more games** come from [GameMonetize](https://gamemonetize.com/)'s free catalog, which is published for sites to embed. They're bundled compressed into `index.html` (about 145 KB) and unpacked the first time you open the store. Gambling, dating, adult and similar themes, plus duplicates, are filtered out. In the store you can narrow everything by:

- **Genre:** Action, Adventure, Arcade, Puzzle, Racing, Shooting, Sports, Strategy, Simulation, Platformer, Fighting, Survival, Horror, RPG, Board & Card, Clicker & Idle, Casual, Educational, Cooking, Dress-up, Music, Tower Defense
- **Type:** Single player, 2 players, Multiplayer, .io, 3D, 2D, Mobile-friendly, Kids & family, Endless
- **Source:** hand-picked or GameMonetize

There's also a search box, sorting (A to Z, Z to A, surprise me), a "See all" link on every genre shelf, and "Show more" paging.

**Ads:** GameMonetize games load an ad SDK. AxOS fetches the game page, removes the ad scripts and swaps in a stand-in SDK that reports "ad finished" immediately, then runs the page in a frame with **no access to AxOS** (no same-origin, no popups, no top navigation). The frame has no storage of its own, so its stand-in storage sends saves back to AxOS: progress is kept per game (up to 2 MB) and removed when you uninstall the game. Games built from module scripts (Construct 3 exports, about a third of the catalog) can't load in a frame with no origin, so when AxOS is hosted they run through AxCore instead, where the service worker's ad blocking stops the ad network (still no popups or top navigation); opened as a local file, they use the normal embed. A few games won't start without the real SDK: once a game has stopped downloading for 10 seconds without starting (or after a minute), AxOS reloads it normally, and the shield button in each game window switches between blocked and allowed. The hand-picked games are the creators' own sites in a normal frame, so any ads they show themselves can't be removed from inside AxOS.

Store pages and library tiles show each game's real logo and preview art, loaded from its official site. If a site doesn't publish one, AxOS shows a symbol instead.

Web games need an internet connection. Each game window has buttons to reload, go full screen, or open the game in AxBrowser. **Add all web games to library** puts them all in your library at once.

**Steam:** the **STEAM** tab connects AxPlay to your real Steam account. A browser can't run Steam games, so AxPlay hands them off to the Steam app on your computer.

- **Connecting:** enter your profile link or name.
  - Without a key, AxPlay reads your public profile and recently played games.
  - Adding a free Steam Web API key imports your whole library. Steam no longer shows game lists to logged-out visitors, so the key is required for that.
- **Store:** search the Steam store. Each game page shows its description, screenshots, price, Metacritic score, platforms, and its ProtonDB rating or native Linux support.
- **Buttons:**
  - **Play** and **Install** open the Steam app on this computer with `steam://` links.
  - **Store page** opens in AxBrowser.
  - **Add to AxPlay library** puts the game next to your other games, including in Big Picture.
- **Privacy:** requests go through AxProxy. Set up a personal gateway so your API key doesn't pass through public gateways.

**Big Picture mode** works with the arrow keys, Enter and Esc, or with a gamepad (D-pad, A and B).

**Geometry Dash** isn't bundled into `index.html`. The app loads your webport at runtime: by default from https://gd-webport.pages.dev/, or from your GitHub fork through jsDelivr (switch this with the layers button in the title bar). It always runs the latest version of your repo, but it needs an internet connection.

## AxOS Cloud

Sign in under **Settings → Accounts**, or choose **Restore from AxOS Cloud** during first-time setup.

**Where it runs:** on your personal gateway worker. Add a KV namespace binding named `AXCLOUD` to the worker in Cloudflare.

**Encryption:** your password never leaves the device. It's turned into two separate keys:
- **A sign-in key**, which the server stores only as a salted hash.
- **An encryption key**, which stays on the device.

Saves are gzip-compressed and AES-GCM encrypted before upload, so the server can't read them. If you forget your password, the cloud copy can't be recovered.

**What's saved:** files, settings, AxPlay library, saved PCs and other AxOS data.

**Your PCs:** AxOS Companion signs in to the same account and lists its PC under the account. The PC's connection details are encrypted with your account key first. Update your gateway worker to get this: copy the code again from Settings → Network & Browser and redeploy it.

**When it syncs:** every 10 minutes when something changes, and before shutdown, restart or sign out. If another device saved in the meantime, AxOS asks which copy to keep.

## Remote Desktop

1. On the PC, download **AxOS Companion** from the [latest release](https://github.com/axolotlpriv/AxOS/releases/latest) (Windows `.exe`, macOS `.zip`, or Linux), or from the buttons in AxOS's Remote Desktop app.
2. Open it and sign in with your AxOS Cloud account.
3. In AxOS, signed in to the same account, open **Remote Desktop**. The PC is listed with an online indicator. Pick **Gaming** or **Work**.

No account? Choose **Pair with an ID and key** in the companion, then use **Add with a code** in AxOS. See `companion/README.md` for details and for running from Python.

**Builds:** `.github/workflows/companion.yml` packages the companion with PyInstaller on Windows, macOS and Linux. It self-tests each build and publishes them as the `companion` release. It runs on every push that changes `companion/`.

**Modes:**
- **Gaming:** 60+ fps and controller passthrough. The mouse locks with raw input only when a game hides the cursor, and stays free on the desktop.
- **Work:** full resolution, sharp text, clipboard sync.

**Cursor and displays:** the PC's real cursor (position, shape, hidden or not) is streamed and drawn by AxOS, since screen capture leaves it out. With several displays, pick one or **All displays** from the toolbar.

**During a session:** a stats overlay shows resolution, frame rate, bitrate, latency and route.

**How it connects:** AxOS and the PC find each other through public WebTorrent trackers, using encrypted messages, then connect directly with WebRTC.

## Using AxBrowser

- **Tabs:** links that open a new tab appear right after the tab that opened them and switch to it. Ctrl+click or middle-click opens a background tab. Drag tabs to reorder, double-click to pin, right-click for more (duplicate, mute, close others, reopen closed). Drop a link on the tab bar to open it. With many tabs they shrink, the current one stays readable, and the strip scrolls.
- **Address bar:** suggestions from history, bookmarks, open tabs and the search engine, with inline completion. Shortcuts: `yt cats`, `w axolotl`, `gh scramjet`, `r askreddit`, `g`, `ddg`, `mdn`, `so`, `a`. Ctrl+Enter adds `www.` and `.com`; Alt+Enter opens in a new tab. A page that focuses its own field can't steal the address bar while you type.
- **Pages:** find in page with a match count, per-site zoom (remembered), a right-click menu for links, images, selected text and videos, print, save page, view source, reader mode, and developer tools (Eruda) on F12.
- **Downloads:** files a page downloads go to `~/Downloads`, with progress in the downloads button. Nothing is downloaded by your real browser.
- **Privacy:** ads and trackers are blocked (AxCore), pop-ups need a click, and a page can open at most 4 tabs in 10 seconds. Clear history, cookies and the cache from the menu.
- **Memory saver:** every page runs in the same process as AxOS, so tabs you haven't opened for 20 minutes are unloaded, and at most 8 pages stay loaded (never the current tab or one playing sound). Unloaded tabs look faded and reload when you click them. Turn it off in `axos://settings`.
- **Sessions:** your tabs (and pins) come back next time. Tabs you haven't opened yet don't load until you click them.
- **Internal pages:** `axos://newtab`, `history`, `bookmarks`, `downloads`, `settings`, `about`.
- **Small screens:** the toolbar and new tab page compact themselves in narrow windows and on phones.

| Shortcut | Action |
| --- | --- |
| Ctrl+T / Ctrl+W / Ctrl+Shift+T | New tab / close tab / reopen closed tab |
| Ctrl+L, Alt+D or F6 | Address bar |
| Ctrl+Tab, Ctrl+PgUp/PgDn, Ctrl+1…9 | Switch tabs |
| Alt+← / Alt+→ | Back / forward (hold the buttons for history) |
| Ctrl+R or F5 | Reload |
| Ctrl+F | Find in page |
| Ctrl+D / Ctrl+J / Ctrl+H | Bookmark / downloads / history |
| Ctrl+= / Ctrl+− / Ctrl+0, Ctrl+wheel | Zoom |
| Ctrl+P / Ctrl+S / Ctrl+U | Print / save page / view source |
| F12 / F11 | Developer tools / full screen |
| Ctrl+Shift+A | Search tabs |

## How the browser loads websites

Most sites refuse to be shown inside an iframe, so AxBrowser loads them through a connection engine. **Automatic** (the default) uses AxCore when it can run and falls back to AxProxy on its own.

### AxCore (service-worker engine)

AxCore is the most complete engine: logins, video sites, web apps, WebSockets, workers, cookies and storage all work. [Scramjet](https://github.com/MercuryWorkshop/scramjet) runs in a service worker (`sw.js`) and rewrites pages the way a real browser would see them. Its network traffic leaves through [bare-mux](https://github.com/MercuryWorkshop/bare-mux) and libcurl.js (real TLS compiled to WebAssembly) over a [Wisp](#wisp-the-real-proxy-backend) relay, so the relay only sees encrypted bytes.

- **Needs:** AxOS opened over `http://` or `https://` (GitHub Pages, any static host, or `localhost`) with `sw.js` and the `proxy/` folder beside `index.html`. Opened as a local file, AxBrowser uses AxProxy instead and says why on the new tab page.
- **Relay choice:** it ranks relays by a quick handshake, then proves the best one with a real HTTPS request before the first page, trying up to three. If a page fails it checks the relay with another real request: if the relay is at fault it switches to the next one and retries by itself, otherwise it shows the site's error.
- **Relay speed:** besides the handshake time, AxBrowser measures each relay's real throughput in the background (it pulls part of a large CDN file through a throwaway session, only while no page is loading) and ranks relays by the estimated time to move 1 MB. A throttled relay loses even if it answers quickly, and AxCore quietly moves to a clearly faster relay when it's idle. Results show next to each relay in `axos://settings`.
- **Personal gateway path:** if you've set up the personal gateway (a free Cloudflare Worker, Settings > Network & Browser), AxCore can use it instead of a Wisp relay: `proxy/ax-gateway.mjs` is a bare-mux transport that speaks plain HTTPS to the Worker, with real redirects, cookies and WebSockets. Pick **Automatic** (Wisp first, gateway if every relay fails), **Wisp only** or **Gateway only** in `axos://settings`. Redeploy the updated Worker from Settings to get redirect and WebSocket support. Cloudflare Workers can only make HTTP requests, so this path is for plain browsing; it is not a Wisp relay.
- **Ad and tracker blocking:** `sw.js` answers requests to about 140 known ad and tracking servers (DoubleClick, Google Tag Manager, Amazon ads, Criteo, Taboola and others) with nothing, including ad iframes inside a page. It never blocks the page you opened. Fewer requests also means less traffic through the relay. The site-info panel (the padlock) shows how many were blocked on the page, and `axos://settings` turns it off.
- **HTTP cache:** `sw.js` keeps scripts, styles, images and fonts in a cache (up to 900 files and 200 MB, each file under 6 MB) and respects each site's `max-age`, so a second visit to a site skips most of the relay. In testing, a Wikipedia article loaded in about 2.5 s the second time against 12 s the first. "Clear cookies and site data" empties it.
- **New tabs:** links that open a new tab (`target="_blank"`, `window.open`) open right after the tab that opened them and switch to it. Ctrl+click and middle-click open a background tab. Scripts can't open tabs without a click (pop-ups are blocked), and a page can open at most 4 tabs in 10 seconds.
- **Developer tools:** F12 loads [Eruda](https://github.com/liriliri/eruda) into the page (console, elements, network, storage).
- **Isolation:** pages in AxCore run on the same web address as AxOS. Scramjet hides AxOS from them and the frame has no pop-up or top-navigation permission, but a service-worker proxy can't be a hard security boundary. Avoid opening untrusted sites while you're signed in to AxOS Cloud.
- **Third-party code:** `proxy/` holds unmodified, version-pinned copies of Scramjet 1.1.0, bare-mux 2.1.9 and libcurl-transport 1.5.2 (all AGPL-3.0). See `proxy/README.md`.
- **Relay list without a rebuild:** a hosted AxOS also reads `relays.json` next to `index.html`. Add a relay there (`{"id", "name", "url", "note"}`) or mark a dead built-in one with `"off": true`, commit, and every AxOS that loads the site picks it up.
- **Your own relay:** `relay/` is a small Node Wisp server you can deploy for free (see `relay/README.md`).


### AxProxy (in-page rewriting engine)

AxProxy fetches each page through a gateway, rewrites it so links, forms and pop-ups stay inside AxBrowser, and renders it in a sandbox.

- **Wisp backend (first choice):** see [Wisp](#wisp-the-real-proxy-backend) below. It tunnels real encrypted connections through public relays, so it handles any site, POST forms, redirects with cookies and downloads.
- **Racing:** it starts the fastest working backend (Wisp first, then your personal gateway, then the public CORS gateways), adds another one if the first is slow, and uses whichever answers first. It remembers each gateway's speed and failures, so dead ones get skipped.
- **Caching:** recently visited pages are cached, so going back or forward is instant. **Reload** always fetches a fresh copy.
- **Compatibility layer:** sandboxed pages normally crash because `localStorage`, cookies, `history.pushState` and IndexedDB all throw security errors there. AxProxy injects replacements:
  - Storage and cookies are saved for each site.
  - The address bar follows the page's own URL changes.
  - `fetch` and `XMLHttpRequest` calls that fail because of cross-site restrictions are retried through AxProxy.
- **Personal gateway (recommended):** the free public gateways are shared and rate-limited. **Settings → Network & Browser** has a copy-and-paste Cloudflare Worker you can deploy in a minute. With it you get no rate limits, real POST forms, and cookies that make logins work.
  - Pages can only send cookies to their own site through the gateway.
  - You can protect your gateway with a key.

### Direct

A plain iframe, for sites that allow embedding.

### Ultraviolet / Scramjet

Ultraviolet and Scramjet need a service worker and a backend server, so they can't be packed into a single HTML file.

- **Saving servers:** **Settings → Network & Browser** saves Ultraviolet and Scramjet servers as presets.
- **Templates:** there are starting points for self-hosted Ultraviolet-App and Scramjet, on the internet or on your own computer.
- **Using a server:** pick one there, or from the connection-mode button in the address bar.
- **Trust:** only use servers you trust, because whoever runs one can see everything you type through it.

AxOS no longer lists public proxy *sites* such as InvisiProxy or Nebula. They only link to someone else's front end, which can't run inside AxOS. What they all share is the Wisp relay behind them, and AxOS uses that directly.

### Wisp: the real proxy backend

Wisp is a protocol that carries many TCP connections over one WebSocket. InvisiProxy, Anura, Terbium and every Scramjet or Ultraviolet site that uses it run a Wisp relay.

- **How AxOS uses it:** AxProxy loads [libcurl.js](https://github.com/ading2210/libcurl.js) (curl and mbedTLS in WebAssembly) and makes the HTTPS connections from inside the page. Only encrypted bytes go through the relay, so the operator sees which sites you connect to but not what you read or type.
- **Pinned engine:** libcurl.js 0.7.5 is downloaded from a CDN and its SHA-256 is checked before it runs. If a CDN has been tampered with, the load is refused.
- **What works:** any HTTPS site, POST forms, cookies on every redirect hop, gzip and Brotli pages, and binary downloads.
- **Servers:** **Settings → Network & Browser → Wisp servers** lists 10 public relays (Terbium, Anura, FreeWisp, WebMC, PGIS, Reeyuki, Lunaar, DaydreamX, PeteZah and the Mercury Workshop demo server). Each gets a live status check. You can pin one, switch any off, or add your own `wss://` address.
- **Automatic failover:** AxProxy uses the fastest relay. If a request fails it tries up to two others, and a relay is only marked as down if another one succeeded where it failed.
- **Trust:** relays are run by strangers and can disappear. Don't sign in to anything important through them. For private browsing, run your own [wisp-server-python](https://github.com/MercuryWorkshop/wisp-server-python) or similar and add it under **Add a Wisp server**.

## Movies, YouTube and short videos

AxFlix, AxTube and AxTok live in the Start menu. Like everything else, none of them can take the real browser tab anywhere: outside links open in AxBrowser, and players run in frames without popup or top-navigation permission.

### AxFlix

- **Catalog:** about 600 popular movies and series, picked by rating and votes from [IMDb's non-commercial datasets](https://developer.imdb.com/non-commercial-datasets/) and bundled into `index.html` with their posters. IMDb's own pages block scripts, so the data comes from the official dataset files.
- **Search:** instant over the catalog. Anything else comes from IMDb's title search, which has no CORS support, so it goes through AxProxy (Wisp or your gateway).
- **Where to watch:** looked up live from JustWatch for the country you pick, with streaming, free, rent and buy options, prices and provider logos, plus a description, age rating and a trailer. JustWatch also has no CORS support, so this goes through Wisp or your personal gateway. Results are cached for a day.
- **Links:** provider buttons open the service inside AxBrowser. AxFlix doesn't stream or link to pirate sites. Most streaming services need a login and DRM, so use their official app to watch.
- **My List** is kept on this device.
- **Credit:** ratings and titles from IMDb, availability from JustWatch.

### AxTube

- **No Wisp:** it connects straight to public [Piped](https://github.com/TeamPiped/Piped) servers, which allow requests from web pages. It tries several at once, remembers the fastest and skips ones that stop answering. A server that answers with an error for one video isn't marked as down. **Settings** in the app lists the servers, tests them and lets you add your own.
- **YouTube's own data through AxCore:** when AxCore is running (AxOS opened over http(s)), search, channels, playlists, video details and related videos come straight from YouTube's InnerTube API through the relay, so AxTube keeps working when every public Piped server is down. YouTube retired its Trending page, so the home page shows the most-viewed videos of the week instead. Piped stays as the fallback, and for direct streams when AxOS runs from a local file.
- **Optional YouTube API key:** a free key from Google Cloud makes search and trending use YouTube's own API.
- **Playback:** YouTube's official embed player when AxOS is served from a web address, or a direct 360p stream otherwise. YouTube's player shows **Error 153** when the page that embeds it sends no referrer, which is the case when AxOS is opened from a local file. Automatic mode handles this, and the watch page has a switch between the two.
- **Limits:** the public servers are sometimes blocked by YouTube for certain videos (live streams especially). The video still plays through the embed, but details and comments may be missing.
- **Playlists:** search has a Playlists filter, and pasting a playlist link opens it. **Play all** starts a queue with Previous and Next. Public servers sometimes return playlists with no videos (YouTube blocks it), and AxTube says so and links to YouTube.
- **Watch page extras:** theater mode (**T**), **Autoplay next** and a **Next** button (related videos, or the next one in a playlist), chapters you can click to jump to, and subtitles in the Direct player's CC menu. Shortcuts: **/** search, **Shift+N** and **Shift+P** next and previous, and with the Direct player **J/L** ±10 s, **K** or space pause, **←/→** ±5 s, **M** mute, **F** full screen, **<** and **>** speed, **0–9** jump.
- **Quality:** the Direct player is 360p because the public servers only offer that. HD plays in YouTube's own player. Autoplay next only works with the Direct player, because YouTube's player doesn't report when a video ends.
- **Subscriptions, history, watch-later** stay on this device.

### AxTok

- **No Wisp:** the feed comes from the public trending data at tikwm.com, with open CORS. Videos are plain files in a `<video>` element.
- **Controls:** scroll or use ↑/↓, **L** to like, **S** to save, **M** for sound, **Space** to pause, double-click to like. **←/→** seek 5 s and the progress bar at the bottom of each video is a scrubber you can click or drag. **[** and **]** (or the Speed button) change speed from 0.5× to 2×. **Auto-next** in the side bar moves on when a video ends.
- **Comments:** the comment button (or **C**) opens a panel with the video's real comments, 20 at a time. Ads in the feed are skipped.
- **Open link:** paste a TikTok link to play it, with TikTok's own embed player as a fallback.
- **Limits:** TikTok's search, creator pages and profile endpoints sit behind a bot check that AxOS doesn't try to get around, so there is no search or creator page. The data service allows about one request a second and is sometimes unavailable. The trending feed is public and unfiltered.

## Staying inside AxOS

AxOS never navigates away from itself and never opens a real browser window on its own. `NavGuard` (the `04a` source file) covers each way this could happen:

| Risk | What stops it |
| --- | --- |
| A page, game or app in an iframe sets `top.location`, calls `window.open`, uses `target="_top"` or `_blank`, or submits a form to another window | Every iframe's sandbox leaves out `allow-top-navigation` and `allow-popups`. Links, image maps and forms in proxied pages are caught and opened in AxBrowser |
| A frame reads or rewrites AxOS itself | Proxied pages and local files have an opaque origin. Other frames only get `allow-same-origin` when their origin differs from AxOS's (`axSandbox`) |
| A link in AxOS's own UI, such as a Markdown preview | Clicks are routed to AxBrowser. `javascript:` and `file:` links are dropped, and Markdown only keeps `http(s)`, `mailto:` and `#` links |
| A file or link dropped on the page, which browsers open by default | `dragover` and `drop` are cancelled everywhere except in text fields |
| The Back button, a trackpad swipe or Alt+Left | After your first click, a history entry is added and re-added on every Back |
| `window.open` called from AxOS code | Goes to AxBrowser. The two deliberate "open in a real tab" buttons (the companion download and the Steam API key page) use `axExternal` |
| Anything else | The browser's "Leave site?" prompt. Turn it off in **Settings → Privacy & security → Ask before leaving AxOS**. AxOS's own restarts use `axReload()`, so they never show it |

The hostile-page test loads a page that tries 15 escapes (top navigation, `window.open` with every target, forms, image maps, DOM access) into every frame type, and checks that the tab never moves and no window opens.

## How .exe and Linux programs work

AxCompat reads the real structure of each executable:

- **Windows:** headers, sections, imported DLLs and functions, version info, and the icon (shown in Explorer and on the taskbar). It also detects installers and .NET programs.
- **Linux:** the interpreter, shared libraries, glibc version and GUI toolkit.

A browser tab can't run native x86 or ARM machine code. Here is what happens when you open each kind of program:

| Program | What happens |
| --- | --- |
| Executables with an embedded **AxOS payload** | Run for real. Try `~/Projects/AxDemo.exe` (a valid Win32 PE) and `~/Projects/axhello` (a valid x86-64 ELF) |
| Windows installers | Run through a simulated setup wizard that installs onto AxCompat's virtual `C:` drive |
| Windows GUI programs | Open in a simulated Win32-style window showing their real metadata |
| Windows console programs and Linux CLI programs | Run in the Terminal in simulation mode |

## Keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| Ctrl+Esc or Win | Start menu |
| Alt+R | Run dialog |
| Alt+\` | Switch windows |
| Ctrl+Alt+Tab | Task View |
| Alt+W | Close window |
| Alt+Arrows | Snap window |
| Ctrl+Alt+T / E / B / S / N | Terminal / Explorer / Browser / Settings / Notepad |
| Ctrl+Alt+L | Lock |
| Ctrl+Alt+D | Show desktop |
| Ctrl+Shift+Esc | Task Manager |

