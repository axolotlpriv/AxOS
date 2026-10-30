# AxOS

A desktop operating system that runs in your browser from **one file**: `index.html`. You don't need a server or a build step. Open the file and it boots.

## Running it

- **Locally:** double-click `index.html`, or drag it into Chrome, Edge or Firefox.
- **Hosted:** put `index.html` on any static host, such as GitHub Pages.

Your files and settings are saved in the browser (IndexedDB and localStorage), so they're still there after a reload. To move AxOS to another browser, use **Settings → System → Storage → Back up AxOS**.

## What's included

| Area | Features |
| --- | --- |
| **Shell** | Boot screen, first-run setup, lock screen with optional password, desktop icons (drag, rubber-band select, rename, drop files from your computer), right-click menus, taskbar with window previews and pinning, Start menu with search, quick settings, calendar and notification center, Task View, window switcher, Run dialog |
| **Windows** | Drag, resize from all edges, maximize, minimize, snap to halves or quarters (drag to an edge or press Alt+Arrow), always-on-top, animations |
| **File Explorer** | Breadcrumb address bar, search, grid and details views, preview pane, cut/copy/paste, drag and drop, Recycle Bin, upload and download, properties |
| **AxBrowser** | Tabs, bookmarks, history, downloads saved to `~/Downloads`, reader mode, zoom, and three connection modes (see below) |
| **Terminal** | Bash-like shell with pipes, redirects, `&&`/`||`, `$(...)`, variables, tab completion and 100+ commands. Type `cmd` for a Command Prompt that runs `.bat` files |
| **AxCompat** | Opens Windows `.exe` (PE32/PE32+) and Linux ELF programs (see below) |
| **Settings** | Wallpapers, accent color, dark/light/auto theme, night light, brightness, scale, taskbar, accounts, default apps, network/proxy, accessibility, backup and restore, factory reset |
| **Apps** | Notepad (with Markdown preview), Calculator, Paint, Photos, Media Player, Task Manager, Clock, Weather, AxOS Store |
| **AxPlay** | Steam-style game launcher: store, library, downloads, play time, Big Picture mode with gamepad support, Proton-style compatibility layer for Windows games, native Linux games |
| **Geometry Dash** | Your [Geometry-Dash-webport](https://github.com/axolotlpriv/Geometry-Dash-webport), loaded live inside a window |
| **Store extras** | Serpent, Tic-Tac-Toe, Memory Match, Sticky Notes. Install them from the Store or with `apt install` |

## Personalization

**Settings → Personalization** has:
- 10 one-click themes (Midnight Neon, Sakura, Retro Terminal, Ocean Glass and more)
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

## AxPlay and games

AxPlay is an original Steam-style launcher. It isn't Steam, and it doesn't connect to Steam.

**Launching games:**
- **Windows `.exe` games** start through AxCompat's Proton-style layer. You can choose the compatibility tool for each game under Properties.
- **Linux ELF and AppImage games** start through the "AxPlay Linux Runtime".
- **Web and AxOS games** run directly.

As with AxCompat, only games that carry an AxOS payload actually run. Two examples are in the store: **Neon Drift**, a native Linux ELF game, and **Orbital**, a Windows PE game. Other native binaries run in simulation mode.

**Adding your own games:** in the library, choose **Add a game** to add an `.exe`, a Linux binary, an `.html` game or a web URL. You can also drop games into `~/Games` and scan for them.

**Web games:** the store lists 36 free web games, each embedded from its creator's or publisher's official site:

| Category | Games |
| --- | --- |
| Action & arcade | HexGL, Q1K3, Underrun, X-Type, Biolab Disaster, Z-Type, Radius Raid, Space Huggers |
| Multiplayer | Krunker, Shell Shockers, Smash Karts, Slither.io, Diep.io, Territorial.io, skribbl.io |
| Puzzle | Astray, Hextris, Untrusted, Minesweeper Online, Little Alchemy 2, Worldle, Solitaire |
| Idle | A Dark Room, Universal Paperclips, Candy Box 2, Particle Clicker, Bitburner, Gridland |
| Sandbox | Minecraft Classic, Sandspiel, Townscaper |
| Play & learn | The Evolution of Trust, Elevator Saga, Flexbox Froggy, Grid Garden, Keybr |

Web games need an internet connection. Each game window has buttons to reload, go full screen, or open the game in AxBrowser. **Add all web games to library** puts them all in your library at once.

**Big Picture mode** works with the arrow keys, Enter and Esc, or with a gamepad (D-pad, A and B).

**Geometry Dash** isn't bundled into `index.html`. The app loads your webport at runtime: by default from https://gd-webport.pages.dev/, or from your GitHub fork through jsDelivr (switch this with the layers button in the title bar). It always runs the latest version of your repo, but it needs an internet connection.

## How the browser loads websites

Most sites refuse to be shown inside an iframe, so AxBrowser has three connection modes:

- **AxProxy (default):** fetches each page through a public CORS gateway (Jina Reader, AllOrigins, CodeTabs, cors.lol), rewrites links, forms and pop-ups so they stay inside AxBrowser, and renders the page in a sandbox. It works well for most content sites. Logins and heavy web apps may not work.
- **Direct:** a plain iframe, for sites that allow embedding.
- **Ultraviolet / Scramjet:** Ultraviolet needs a service worker and a Bare/Wisp backend running on a real server, so it can't be packed into a single HTML file. If you host one, enter its prefix (for example `https://your-host/service/`) under **Settings → Network & Browser**. AxBrowser encodes URLs with UV's XOR codec, or with Base64 or plain URL encoding.

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

