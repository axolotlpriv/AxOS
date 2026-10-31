# AxOS Companion

AxOS Companion lets AxOS's **Remote Desktop** app stream and control a PC: Windows, macOS, or Linux on X11.

## Setup

1. Download the app for the PC from the [latest release](https://github.com/axolotlpriv/AxOS/releases/latest):

   | PC | File |
   | --- | --- |
   | Windows 10 or 11 | `AxOS-Companion-Windows.exe` |
   | macOS 12 or newer (Apple silicon) | `AxOS-Companion-macOS.zip` |
   | Linux (X11, x86-64) | `AxOS-Companion-Linux` |

2. Open it and **sign in with your AxOS Cloud account**: your gateway address, username and password. The gateway address is in AxOS under Settings → Network & Browser.
3. In AxOS, signed in to the same account, open **Remote Desktop**. Your PC is already listed. Pick **Gaming** or **Work**.

**First launch:**
- **Windows:** if SmartScreen warns about an unknown app, choose **More info → Run anyway**. To use controllers, click **Install driver** next to Controllers. This installs ViGEmBus, which provides the virtual Xbox controller.
- **macOS:** right-click the app and choose **Open**. Then allow **Screen Recording** and **Accessibility** in System Settings → Privacy & Security.
- **Linux:** run `chmod +x AxOS-Companion-Linux` first. It needs an X11 session; Wayland isn't supported.

Check **Start AxOS Companion when I sign in** so the PC is always reachable.

### Without an account

Choose **Pair with an ID and key instead**. In AxOS, open **Remote Desktop → Add with a code** and enter the PC ID and secret key the app shows.

## Modes

| | Gaming | Work |
| --- | --- | --- |
| Frame rate | 60 fps (up to 120) | 30 fps |
| Resolution | Up to 1080p | Full resolution, up to 4K |
| Bitrate cap | 20 Mbps | 15 Mbps |
| Mouse | Locked raw mouse; relative input that games read | Absolute cursor |
| Extras | Controller passthrough, minimal buffering | Clipboard sync, sharper text encoding |

You can switch modes during a session, and change resolution, frame rate and bitrate from the toolbar.

**Video encoding:** the companion uses your graphics card's hardware encoder when available: NVENC on NVIDIA, Quick Sync on Intel, AMF on AMD, VideoToolbox on macOS. Otherwise it falls back to tuned x264 on the CPU. The app window shows which one it picked.

**Input on Windows:** keys and mouse are sent with `SendInput` hardware scancodes and raw relative mouse motion, so games pick them up.

## How it connects

1. **Finding your PC:** when you're signed in, the companion lists the PC on your account through your AxOS gateway. Its ID and key are encrypted with your account's key first, so the gateway can't read them. It refreshes every 10 minutes and marks itself offline when it quits.
2. **Signaling:** AxOS reaches the PC through public WebTorrent trackers (`tracker.openwebtorrent.com` and `tracker.webtorrent.dev`). Every connection request and reply is encrypted and authenticated with AES-GCM, using a key derived from the PC's secret key. The trackers only ever see ciphertext.
3. **The stream:** a direct WebRTC connection, encrypted with DTLS.
4. **Strict networks:** if a network blocks direct connections, add a TURN relay server under Remote Desktop → Connection settings.

Your password never leaves the PC. The app stores derived keys, the PC ID and the secret key in `~/.axos-companion.json`, so keep that file private.

## Running from Python

```
pip install -r requirements.txt
python axos-companion.py --gui      # app window
python axos-companion.py --login    # or sign in from the terminal
python axos-companion.py            # terminal only; prints the ID and key
```

**Options:**
- **`--name "Gaming PC"`:** the name shown in AxOS.
- **`--new-key`:** generate a new secret key. Devices that added the PC with a code stop working; account sign-in updates automatically.
- **`--sign-out`:** sign out and remove this PC from your account.
- **`--test-pattern --dry-input`:** stream a test pattern and log input instead of controlling the PC.

## Building the apps

`python build.py` packages the app for the current OS with PyInstaller; the output goes in `dist/`. GitHub Actions (`.github/workflows/companion.yml`) does this on Windows, macOS and Linux for every change to this folder, self-tests each build, and publishes them as the `companion` release.
