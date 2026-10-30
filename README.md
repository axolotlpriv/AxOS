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
| **Store extras** | Serpent, Tic-Tac-Toe, Memory Match, Sticky Notes. Install them from the Store or with `apt install` |

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
