# AxOS Companion

AxOS Companion lets AxOS's **Remote Desktop** app stream and control a PC: Windows, macOS, or Linux on X11.

## Setup

1. Install Python 3.10 or newer.
2. Install the packages:

   ```
   pip install aiortc mss pynput numpy websockets pyperclip soundcard
   ```

   On Windows, you can also add `vgamepad dxcam` for controller support and faster screen capture.
3. Run the companion:

   ```
   python axos-companion.py
   ```

4. It prints a **PC ID** and a **secret key**. In AxOS, open **Remote Desktop → Add a PC** and enter both.

You can also download this file from inside AxOS (Remote Desktop → Download axos-companion.py).

## Modes

| | Gaming | Work |
| --- | --- | --- |
| Frame rate | 60 fps (up to 120) | 30 fps |
| Resolution | Up to 1080p | Full resolution, up to 4K |
| Bitrate cap | 20 Mbps | 15 Mbps |
| Mouse | Locked raw mouse; relative input that games read | Absolute cursor |
| Extras | Controller passthrough, minimal buffering | Clipboard sync, sharper text encoding |

You can switch modes during a session, and change resolution, frame rate and bitrate from the toolbar.

**Video encoding:** the companion uses your graphics card's hardware encoder when available: NVENC on NVIDIA, Quick Sync on Intel, AMF on AMD, VideoToolbox on macOS. Otherwise it falls back to tuned x264 on the CPU.

**Input on Windows:** keys and mouse are sent with `SendInput` hardware scancodes and raw relative mouse motion, so games pick them up.

## How it connects

1. AxOS finds the PC through public WebTorrent trackers (`tracker.openwebtorrent.com` and `tracker.webtorrent.dev`).
2. Every connection request and reply is encrypted and authenticated with AES-GCM, using a key derived from the secret key. The trackers only ever see ciphertext.
3. The stream itself is a direct WebRTC connection, encrypted with DTLS.
4. If a network blocks direct connections, add a TURN relay server under Remote Desktop → Connection settings.

## Options

- **`--name "Gaming PC"`:** the name shown in AxOS.
- **`--new-key`:** generate a new secret key. Previously added devices stop working.
- **`--test-pattern --dry-input`:** stream a test pattern and log input instead of controlling the PC.

Your ID and key are stored in `~/.axos-companion.json`. Anyone with the key can control the PC, so keep it private.
