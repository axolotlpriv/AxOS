#!/usr/bin/env python3
"""AxOS Companion: lets AxOS Remote Desktop stream and control this PC.

The easy way is the packaged app (AxOS-Companion for Windows, macOS or Linux):
open it and sign in with your AxOS Cloud account. Every AxOS device signed in
to the same account then lists this PC in Remote Desktop automatically.

Running from source:

    pip install aiortc mss pynput numpy websockets
    python axos-companion.py --login     # sign in with your AxOS account
    python axos-companion.py --gui       # or use the app window

Without an account, enter the PC ID and secret key it prints into
AxOS > Remote Desktop > Add a PC.

Optional extras: pyperclip (clipboard sync), soundcard (PC audio),
vgamepad (controller support, Windows), dxcam (faster capture, Windows).

Signaling goes through public WebTorrent trackers. Every signaling message is
encrypted and authenticated with the secret key, so the trackers only relay
ciphertext. The stream itself is a direct, DTLS-encrypted WebRTC connection.
"""
import argparse
import asyncio
import base64
import fractions
import hashlib
import json
import os
import platform
import queue
import secrets
import shlex
import ssl
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from xml.sax.saxutils import escape as xml_escape

VERSION = "1.1.0"
FROZEN = getattr(sys, "frozen", False)
TRACKERS = ["wss://tracker.openwebtorrent.com", "wss://tracker.webtorrent.dev"]
STUN = ["stun:stun.l.google.com:19302", "stun:stun.cloudflare.com:3478"]
CONFIG_PATH = os.path.join(os.path.expanduser("~"), ".axos-companion.json")
B32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O/1/I

MISSING = []
try:
    import numpy as np
    import av
    from aiortc import RTCPeerConnection, RTCSessionDescription, RTCConfiguration, RTCIceServer, MediaStreamTrack
    from aiortc.mediastreams import VideoStreamTrack
    import aiortc.codecs.h264 as h264mod
    import aiortc.rtcrtpsender as sendermod
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import hashes
    import websockets
except ImportError as e:  # pragma: no cover - friendly message for first-time setup
    MISSING.append(e.name)
try:
    import mss
    MSS = getattr(mss, "MSS", None) or mss.mss
except ImportError:
    mss = MSS = None
OPTIONAL = {}
for _name in ("pyperclip", "soundcard", "vgamepad", "dxcam"):
    try:
        OPTIONAL[_name] = __import__(_name)
    except Exception:
        OPTIONAL[_name] = None

IS_WIN = platform.system() == "Windows"
IS_MAC = platform.system() == "Darwin"
try:  # packaged builds carry their own CA bundle (the system one may be missing)
    import certifi
    os.environ.setdefault("SSL_CERT_FILE", certifi.where())
except ImportError:
    certifi = None
LOG_SINKS = []
STATUS = {"trackers": 0, "sessions": {}, "account": "", "account_error": ""}


def log(*a):
    line = time.strftime("%H:%M:%S") + " " + " ".join(str(x) for x in a)
    try:
        print(line, flush=True)
    except Exception:  # no console (windowed app)
        pass
    for sink in LOG_SINKS:
        sink(line)


# ---------------------------------------------------------------------------
# Identity and encryption
# ---------------------------------------------------------------------------
def gen_code(n):
    return "".join(secrets.choice(B32) for _ in range(n))


def load_config():
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH) as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def save_config(cfg):
    with open(CONFIG_PATH, "w") as f:
        json.dump(cfg, f)
    try:
        os.chmod(CONFIG_PATH, 0o600)
    except Exception:
        pass


def load_identity(args):
    cfg = load_config()
    if args.new_key or "id" not in cfg:
        cfg["id"] = cfg.get("id") if not args.new_key and cfg.get("id") else gen_code(4) + "-" + gen_code(4)
        cfg["key"] = gen_code(4) + "-" + gen_code(4) + "-" + gen_code(4)
    if args.name:
        cfg["name"] = args.name
    cfg.setdefault("name", platform.node() or "My PC")
    save_config(cfg)
    return cfg


def norm_id(pc_id):
    return pc_id.upper().replace("-", "").replace(" ", "")


def derive_key(pc_id, key):
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=("axos-rd:" + norm_id(pc_id)).encode(), iterations=150000)
    return AESGCM(kdf.derive(key.upper().replace("-", "").replace(" ", "").encode()))


def info_hash(pc_id):
    return hashlib.sha1(("axos-rd:" + norm_id(pc_id)).encode()).digest().decode("latin1")


def seal(aes, obj):
    iv = os.urandom(12)
    return base64.b64encode(iv + aes.encrypt(iv, json.dumps(obj).encode(), None)).decode()


def unseal(aes, text):
    raw = base64.b64decode(text)
    return json.loads(aes.decrypt(raw[:12], raw[12:], None))


# ---------------------------------------------------------------------------
# AxOS Cloud account: sign in once and this PC appears on every AxOS device
# signed in to the same account. Same key derivation as AxOS: the password
# never leaves this PC, only a derived auth key does. The PC's connection
# details are encrypted with the account's encryption key before upload.
# ---------------------------------------------------------------------------
class AccountError(Exception):
    pass


def account_keys(user, password):
    u, pw = user.lower().strip(), password.encode()
    auth = hashlib.pbkdf2_hmac("sha256", pw, ("axos-auth:" + u).encode(), 200000, 32).hex()
    enc = hashlib.pbkdf2_hmac("sha256", pw, ("axos-enc:" + u).encode(), 200000, 32)
    return auth, enc


class Account:
    def __init__(self, cfg):
        self.cfg = cfg
        self.dirty = True

    @property
    def data(self):
        return self.cfg.get("account") or {}

    @property
    def signed_in(self):
        return bool(self.data.get("user") and self.data.get("enc"))

    def _http(self, server, path, body=None, method="GET", token=None):
        req = urllib.request.Request(server.rstrip("/") + "/cloud/" + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                                     headers={"content-type": "application/json", "user-agent": "AxOS-Companion/" + VERSION, **({"authorization": "Bearer " + token} if token else {})})
        ctx = ssl.create_default_context(cafile=certifi.where()) if certifi else None
        try:
            with urllib.request.urlopen(req, timeout=20, context=ctx) as r:
                return json.loads(r.read() or b"{}")
        except urllib.error.HTTPError as e:
            try:
                msg = json.loads(e.read()).get("error")
            except Exception:
                msg = None
            err = AccountError(msg or f"Cloud server error (HTTP {e.code}).")
            err.status = e.code
            raise err
        except (urllib.error.URLError, OSError, ValueError) as e:
            raise AccountError("Couldn't reach the cloud server. Check the address and your connection.") from e

    def sign_in(self, server, user, password):
        server = server.strip().rstrip("/")
        if not server.startswith(("http://", "https://")):
            server = ("http://" if server.startswith(("localhost", "127.", "192.168.", "10.")) else "https://") + server
        auth, enc = account_keys(user, password)
        r = self._http(server, "login", {"user": user.strip(), "auth": auth}, "POST")
        self.cfg["account"] = {"server": server, "user": r["user"], "auth": auth, "enc": enc.hex(), "token": r["token"]}
        save_config(self.cfg)
        self.dirty = True
        return r["user"]

    def api(self, path, body=None, method="GET"):
        a = self.data
        try:
            return self._http(a["server"], path, body, method, a.get("token"))
        except AccountError as e:
            if getattr(e, "status", 0) != 401:
                raise
        # Session expired: sign in again with the stored auth key (not the password)
        r = self._http(a["server"], "login", {"user": a["user"], "auth": a["auth"]}, "POST")
        a["token"] = r["token"]
        save_config(self.cfg)
        return self._http(a["server"], path, body, method, a["token"])

    def publish(self, ident, online=True):
        blob = seal(AESGCM(bytes.fromhex(self.data["enc"])), {"id": ident["id"], "key": ident["key"], "name": ident["name"], "os": f"{platform.system()} {platform.release()}", "v": VERSION})
        try:
            self.api("devices", {"id": norm_id(ident["id"]), "blob": blob, "off": not online}, "PUT")
        except AccountError as e:
            if getattr(e, "status", 0) == 404:
                raise AccountError("Your gateway needs updating for PC sign-in. In AxOS, open Settings → Network & Browser, copy the gateway code again and redeploy the worker.") from e
            raise

    def sign_out(self, ident):
        try:
            self.api("devices/remove", {"id": norm_id(ident["id"])}, "POST")
            self.api("logout", {}, "POST")
        except AccountError:
            pass
        self.cfg.pop("account", None)
        save_config(self.cfg)


# ---------------------------------------------------------------------------
# Video: screen capture track + tuned H.264 encoder (hardware when available)
# ---------------------------------------------------------------------------
MODES = {
    "gaming": {"fps": 60, "max_height": 1080, "min_br": 3_000_000, "max_br": 20_000_000, "start_br": 10_000_000, "preset": "ultrafast"},
    "work": {"fps": 30, "max_height": 2160, "min_br": 2_000_000, "max_br": 15_000_000, "start_br": 8_000_000, "preset": "veryfast"},
}
STATE = {"mode": "work", "fps": 30, "max_height": 2160, "max_br": 15_000_000, "monitor": 1, "encoder": "?"}


def apply_mode(mode, fps=None, max_height=None, max_br=None):
    m = MODES.get(mode, MODES["work"])
    STATE.update(mode=mode, fps=int(fps or m["fps"]), max_height=int(max_height or m["max_height"]), max_br=int(max_br or m["max_br"]))
    h264mod.MIN_BITRATE = m["min_br"]
    h264mod.MAX_BITRATE = STATE["max_br"]
    h264mod.DEFAULT_BITRATE = min(m["start_br"], STATE["max_br"])
    h264mod.MAX_FRAME_RATE = max(30, STATE["fps"])


def encoder_order():
    names = []
    s = platform.system()
    if s == "Windows":
        names = ["h264_nvenc", "h264_qsv", "h264_amf"]
    elif s == "Darwin":
        names = ["h264_videotoolbox"]
    else:
        names = ["h264_nvenc", "h264_vaapi", "h264_qsv"]
    return [n for n in names if n in av.codecs_available] + ["libx264"]


ENCODER_OPTIONS = {
    "h264_nvenc": lambda m: {"preset": "p1" if m == "gaming" else "p3", "tune": "ull", "zerolatency": "1", "rc": "cbr", "delay": "0", "bf": "0"},
    "h264_qsv": lambda m: {"preset": "veryfast", "async_depth": "1", "look_ahead": "0"},
    "h264_amf": lambda m: {"usage": "ultralowlatency" if m == "gaming" else "lowlatency", "quality": "speed" if m == "gaming" else "balanced", "rc": "cbr"},
    "h264_videotoolbox": lambda m: {"realtime": "1", "allow_sw": "0"},
    "h264_vaapi": lambda m: {},
    "libx264": lambda m: {"preset": MODES[m]["preset"], "tune": "zerolatency", "level": "42", "rc-lookahead": "0", "crf": "23" if m == "gaming" else "19"},
}
BAD_ENCODERS = set()


class AxEncoder(h264mod.H264Encoder):
    """aiortc's H.264 encoder with higher bitrates, 60+ fps and hardware encoders."""
    _fps = None

    def _open(self, frame):
        for name in encoder_order():
            if name in BAD_ENCODERS:
                continue
            try:
                c = av.CodecContext.create(name, "w")
                c.width, c.height = frame.width, frame.height
                c.bit_rate = self.target_bitrate
                c.pix_fmt = "nv12" if name in ("h264_nvenc", "h264_qsv", "h264_amf", "h264_videotoolbox") else "yuv420p"
                c.framerate = fractions.Fraction(STATE["fps"], 1)
                c.time_base = fractions.Fraction(1, STATE["fps"])
                c.gop_size = STATE["fps"] * 10
                c.max_b_frames = 0
                opts = ENCODER_OPTIONS[name](STATE["mode"])
                if name == "libx264":
                    # Constant quality, capped by the network's bitrate: sharp static text, bounded motion
                    kbps = max(500, self.target_bitrate // 1000)
                    opts.update({"maxrate": f"{kbps}k", "bufsize": f"{max(250, kbps * 2 // STATE['fps'])}k"})
                c.options = opts
                try:
                    c.profile = "Constrained Baseline" if name != "libx264" else "Baseline"
                except Exception:
                    pass
                c.open()
                if STATE["encoder"] != name:
                    log(f"Video encoder: {name} ({'hardware' if name != 'libx264' else 'CPU'})")
                STATE["encoder"] = name
                return c
            except Exception as e:
                BAD_ENCODERS.add(name)
                if name != "libx264":
                    log(f"{name} not usable here ({str(e)[:60]}); trying the next encoder")
        raise RuntimeError("No H.264 encoder available")

    def _encode_frame(self, frame, force_keyframe):
        if self.codec and (frame.width != self.codec.width or frame.height != self.codec.height or abs(self.target_bitrate - self.codec.bit_rate) / max(1, self.codec.bit_rate) > 0.35 or self._fps != STATE["fps"]):
            self.buffer_data, self.buffer_pts, self.codec = b"", None, None
        frame.pict_type = av.video.frame.PictureType.I if force_keyframe else av.video.frame.PictureType.NONE
        if self.codec is None:
            self.codec = self._open(frame)
            self._fps = STATE["fps"]
        try:
            data = b"".join(bytes(p) for p in self.codec.encode(frame))
        except Exception as e:
            if STATE["encoder"] != "libx264":
                log(f"{STATE['encoder']} failed while encoding ({str(e)[:60]}); falling back")
                BAD_ENCODERS.add(STATE["encoder"])
                self.codec = None
                return
            raise
        if data:
            yield from self._split_bitstream(data)


def probe_encoder():
    """Find the best encoder that actually works on this PC (hardware ones can be listed but unusable)."""
    for name in encoder_order():
        if name in BAD_ENCODERS:
            continue
        try:
            c = av.CodecContext.create(name, "w")
            c.width, c.height, c.pix_fmt = 256, 256, "yuv420p" if name == "libx264" else "nv12"
            c.time_base = fractions.Fraction(1, 30)
            c.open()
            c.encode(av.VideoFrame(256, 256, c.pix_fmt))
            return name
        except Exception:
            BAD_ENCODERS.add(name)
    return None


_orig_get_encoder = sendermod.get_encoder if not MISSING else None


def _get_encoder(codec):
    if codec.mimeType.lower() == "video/h264":
        return AxEncoder()
    return _orig_get_encoder(codec)


class ScreenTrack(VideoStreamTrack if not MISSING else object):
    kind = "video"

    def __init__(self, test_pattern=False):
        super().__init__()
        self.test = test_pattern
        self._local = threading.local()
        self._start = None
        self._count = 0
        self._dx = None
        if IS_WIN and OPTIONAL["dxcam"] and not test_pattern:
            try:
                self._dx = OPTIONAL["dxcam"].create(output_color="BGRA")
                log("Capture: DXGI desktop duplication (dxcam)")
            except Exception:
                self._dx = None

    def monitors(self):
        if self.test or mss is None:
            return [{"i": 1, "w": 1920, "h": 1080, "primary": True}]
        with MSS() as s:
            return [{"i": i, "w": m["width"], "h": m["height"], "primary": i == 1} for i, m in enumerate(s.monitors) if i > 0]

    def _sct(self):
        if not hasattr(self._local, "sct"):
            self._local.sct = MSS()
        return self._local.sct

    def _grab(self):
        if self.test:
            w, h = 1280, 720
            t = time.time()
            img = np.zeros((h, w, 4), np.uint8)
            x = int((t * 300) % w)
            img[:, :, 0] = np.linspace(80, 200, w, dtype=np.uint8)[None, :]
            img[:, :, 2] = np.linspace(200, 60, h, dtype=np.uint8)[:, None]
            img[h // 2 - 60:h // 2 + 60, max(0, x - 60):x + 60, :3] = 255
            return img, w, h
        if self._dx is not None:
            arr = self._dx.grab()
            if arr is None:
                arr = getattr(self, "_last", None)
                if arr is None:
                    time.sleep(0.005)
                    return self._grab()
            self._last = arr
            return arr, arr.shape[1], arr.shape[0]
        sct = self._sct()
        mons = sct.monitors
        mon = mons[STATE["monitor"]] if STATE["monitor"] < len(mons) else mons[1]
        shot = sct.grab(mon)
        return np.frombuffer(shot.bgra, np.uint8).reshape(shot.height, shot.width, 4), shot.width, shot.height

    async def recv(self):
        fps = max(5, STATE["fps"])
        if self._start is None or fps != getattr(self, "_fps", fps):
            self._start, self._count = time.time(), 0
        self._fps = fps
        self._count += 1
        target = self._start + self._count / fps
        delay = target - time.time()
        if delay > 0:
            await asyncio.sleep(delay)
        elif delay < -0.5:
            self._start, self._count = time.time(), 0
        arr, w, h = await asyncio.get_running_loop().run_in_executor(None, self._grab)
        frame = av.VideoFrame.from_ndarray(arr, format="bgra")
        mh = STATE["max_height"]
        if h > mh:
            nw = int(w * mh / h) // 2 * 2
            frame = frame.reformat(width=nw, height=mh)
        elif w % 2 or h % 2:
            frame = frame.reformat(width=w // 2 * 2, height=h // 2 * 2)
        frame.pts = int((time.time() - self._start) * 90000)
        frame.time_base = fractions.Fraction(1, 90000)
        return frame


class LoopbackAudio(MediaStreamTrack if not MISSING else object):
    """PC audio through the soundcard library's loopback recorder (Windows / Linux PulseAudio)."""
    kind = "audio"

    def __init__(self, test_tone=False):
        super().__init__()
        self.q = queue.Queue(maxsize=50)
        self.pts = 0
        self.test = test_tone
        self.running = True
        threading.Thread(target=self._run, daemon=True).start()

    def _run(self):
        rate, n = 48000, 960
        if self.test:
            t = 0
            while self.running:
                s = (np.sin(2 * np.pi * 440 * (np.arange(n) + t) / rate) * 3000).astype(np.int16)
                t += n
                self._put(np.stack([s, s], axis=1))
                time.sleep(n / rate)
            return
        sc = OPTIONAL["soundcard"]
        try:
            mic = sc.get_microphone(id=str(sc.default_speaker().name), include_loopback=True)
            with mic.recorder(samplerate=rate, channels=2, blocksize=n) as rec:
                while self.running:
                    data = rec.record(numframes=n)
                    self._put((np.clip(data, -1, 1) * 32767).astype(np.int16))
        except Exception as e:
            log("Audio capture stopped:", str(e)[:80])

    def _put(self, pcm):
        try:
            self.q.put_nowait(pcm)
        except queue.Full:
            try:
                self.q.get_nowait()
            except queue.Empty:
                pass

    async def recv(self):
        pcm = await asyncio.get_running_loop().run_in_executor(None, self.q.get)
        frame = av.AudioFrame.from_ndarray(pcm.reshape(1, -1), format="s16", layout="stereo")
        frame.sample_rate = 48000
        frame.pts = self.pts
        frame.time_base = fractions.Fraction(1, 48000)
        self.pts += pcm.shape[0]
        return frame

    def stop(self):
        self.running = False
        super().stop()


# ---------------------------------------------------------------------------
# Input injection
# ---------------------------------------------------------------------------
# KeyboardEvent.code -> (PC/AT set-1 scancode, extended) for Windows SendInput
SCAN = {
    "Escape": (0x01, 0), "Digit1": (0x02, 0), "Digit2": (0x03, 0), "Digit3": (0x04, 0), "Digit4": (0x05, 0), "Digit5": (0x06, 0), "Digit6": (0x07, 0), "Digit7": (0x08, 0), "Digit8": (0x09, 0), "Digit9": (0x0A, 0), "Digit0": (0x0B, 0),
    "Minus": (0x0C, 0), "Equal": (0x0D, 0), "Backspace": (0x0E, 0), "Tab": (0x0F, 0), "KeyQ": (0x10, 0), "KeyW": (0x11, 0), "KeyE": (0x12, 0), "KeyR": (0x13, 0), "KeyT": (0x14, 0), "KeyY": (0x15, 0), "KeyU": (0x16, 0), "KeyI": (0x17, 0),
    "KeyO": (0x18, 0), "KeyP": (0x19, 0), "BracketLeft": (0x1A, 0), "BracketRight": (0x1B, 0), "Enter": (0x1C, 0), "ControlLeft": (0x1D, 0), "KeyA": (0x1E, 0), "KeyS": (0x1F, 0), "KeyD": (0x20, 0), "KeyF": (0x21, 0), "KeyG": (0x22, 0),
    "KeyH": (0x23, 0), "KeyJ": (0x24, 0), "KeyK": (0x25, 0), "KeyL": (0x26, 0), "Semicolon": (0x27, 0), "Quote": (0x28, 0), "Backquote": (0x29, 0), "ShiftLeft": (0x2A, 0), "Backslash": (0x2B, 0), "KeyZ": (0x2C, 0), "KeyX": (0x2D, 0),
    "KeyC": (0x2E, 0), "KeyV": (0x2F, 0), "KeyB": (0x30, 0), "KeyN": (0x31, 0), "KeyM": (0x32, 0), "Comma": (0x33, 0), "Period": (0x34, 0), "Slash": (0x35, 0), "ShiftRight": (0x36, 0), "NumpadMultiply": (0x37, 0), "AltLeft": (0x38, 0),
    "Space": (0x39, 0), "CapsLock": (0x3A, 0), "F1": (0x3B, 0), "F2": (0x3C, 0), "F3": (0x3D, 0), "F4": (0x3E, 0), "F5": (0x3F, 0), "F6": (0x40, 0), "F7": (0x41, 0), "F8": (0x42, 0), "F9": (0x43, 0), "F10": (0x44, 0),
    "NumLock": (0x45, 0), "ScrollLock": (0x46, 0), "Numpad7": (0x47, 0), "Numpad8": (0x48, 0), "Numpad9": (0x49, 0), "NumpadSubtract": (0x4A, 0), "Numpad4": (0x4B, 0), "Numpad5": (0x4C, 0), "Numpad6": (0x4D, 0), "NumpadAdd": (0x4E, 0),
    "Numpad1": (0x4F, 0), "Numpad2": (0x50, 0), "Numpad3": (0x51, 0), "Numpad0": (0x52, 0), "NumpadDecimal": (0x53, 0), "IntlBackslash": (0x56, 0), "F11": (0x57, 0), "F12": (0x58, 0),
    "NumpadEnter": (0x1C, 1), "ControlRight": (0x1D, 1), "NumpadDivide": (0x35, 1), "PrintScreen": (0x37, 1), "AltRight": (0x38, 1), "Home": (0x47, 1), "ArrowUp": (0x48, 1), "PageUp": (0x49, 1), "ArrowLeft": (0x4B, 1),
    "ArrowRight": (0x4D, 1), "End": (0x4F, 1), "ArrowDown": (0x50, 1), "PageDown": (0x51, 1), "Insert": (0x52, 1), "Delete": (0x53, 1), "MetaLeft": (0x5B, 1), "MetaRight": (0x5C, 1), "ContextMenu": (0x5D, 1),
}


class WinInput:
    """SendInput with hardware scancodes and raw relative mouse motion, which games read."""

    def __init__(self):
        import ctypes
        from ctypes import wintypes
        self.ct = ctypes
        u = ctypes.WinDLL("user32", use_last_error=True)
        self.u = u
        ULONG_PTR = ctypes.c_size_t

        class MOUSEINPUT(ctypes.Structure):
            _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG), ("mouseData", wintypes.DWORD), ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]

        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]

        class HARDWAREINPUT(ctypes.Structure):
            _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD), ("wParamH", wintypes.WORD)]

        class _U(ctypes.Union):
            _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]

        class INPUT(ctypes.Structure):
            _anonymous_ = ("u",)
            _fields_ = [("type", wintypes.DWORD), ("u", _U)]

        self.INPUT, self.MOUSEINPUT, self.KEYBDINPUT = INPUT, MOUSEINPUT, KEYBDINPUT
        u.SendInput.argtypes = (wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int)
        try:
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except Exception:
            pass

    def _send(self, inp):
        self.u.SendInput(1, self.ct.byref(inp), self.ct.sizeof(inp))

    def _mouse(self, dx=0, dy=0, data=0, flags=0):
        inp = self.INPUT(type=0)
        inp.mi = self.MOUSEINPUT(dx, dy, data & 0xFFFFFFFF, flags, 0, 0)
        self._send(inp)

    def key(self, code, down):
        sc = SCAN.get(code)
        if not sc:
            return
        flags = 0x0008 | (0 if down else 0x0002) | (0x0001 if sc[1] else 0)
        inp = self.INPUT(type=1)
        inp.ki = self.KEYBDINPUT(0, sc[0], flags, 0, 0)
        self._send(inp)

    def move_abs(self, nx, ny, mon):
        # Map onto the virtual desktop in 0..65535 coordinates
        vx, vy = self.u.GetSystemMetrics(76), self.u.GetSystemMetrics(77)
        vw, vh = self.u.GetSystemMetrics(78), self.u.GetSystemMetrics(79)
        px = mon["left"] + nx * mon["width"]
        py = mon["top"] + ny * mon["height"]
        self._mouse(int((px - vx) * 65535 / max(1, vw - 1)), int((py - vy) * 65535 / max(1, vh - 1)), 0, 0x0001 | 0x8000 | 0x4000)

    def move_rel(self, dx, dy):
        self._mouse(int(dx), int(dy), 0, 0x0001)

    def button(self, b, down):
        flags = {0: (0x0002, 0x0004), 1: (0x0020, 0x0040), 2: (0x0008, 0x0010)}.get(b)
        if flags:
            self._mouse(0, 0, 0, flags[0] if down else flags[1])
        elif b in (3, 4):
            self._mouse(0, 0, 1 if b == 3 else 2, 0x0080 if down else 0x0100)

    def wheel(self, dx, dy):
        if dy:
            self._mouse(0, 0, int(-dy), 0x0800)
        if dx:
            self._mouse(0, 0, int(dx), 0x01000)


class PynputInput:
    def __init__(self):
        from pynput import keyboard, mouse
        self.k, self.m = keyboard.Controller(), mouse.Controller()
        self.Key, self.KeyCode, self.Button = keyboard.Key, keyboard.KeyCode, mouse.Button
        K = keyboard.Key
        self.special = {"Escape": K.esc, "Enter": K.enter, "NumpadEnter": K.enter, "Backspace": K.backspace, "Tab": K.tab, "Space": K.space, "ShiftLeft": K.shift_l, "ShiftRight": K.shift_r,
                        "ControlLeft": K.ctrl_l, "ControlRight": K.ctrl_r, "AltLeft": K.alt_l, "AltRight": K.alt_r, "MetaLeft": K.cmd, "MetaRight": K.cmd, "CapsLock": K.caps_lock,
                        "ArrowUp": K.up, "ArrowDown": K.down, "ArrowLeft": K.left, "ArrowRight": K.right, "Home": K.home, "End": K.end, "PageUp": K.page_up, "PageDown": K.page_down,
                        "Delete": K.delete, "Insert": getattr(K, "insert", K.delete), "ContextMenu": getattr(K, "menu", K.cmd), "PrintScreen": getattr(K, "print_screen", K.f12)}
        for i in range(1, 21):
            if hasattr(K, f"f{i}"):
                self.special[f"F{i}"] = getattr(K, f"f{i}")
        self.chars = {"Minus": "-", "Equal": "=", "BracketLeft": "[", "BracketRight": "]", "Backslash": "\\", "Semicolon": ";", "Quote": "'", "Backquote": "`", "Comma": ",", "Period": ".", "Slash": "/",
                      "NumpadAdd": "+", "NumpadSubtract": "-", "NumpadMultiply": "*", "NumpadDivide": "/", "NumpadDecimal": "."}

    def _resolve(self, code):
        if code in self.special:
            return self.special[code]
        if code.startswith("Key") and len(code) == 4:
            return self.KeyCode.from_char(code[3].lower())
        if code.startswith("Digit"):
            return self.KeyCode.from_char(code[5:])
        if code.startswith("Numpad") and code[6:].isdigit():
            return self.KeyCode.from_char(code[6:])
        if code in self.chars:
            return self.KeyCode.from_char(self.chars[code])
        return None

    def key(self, code, down):
        k = self._resolve(code)
        if k is not None:
            (self.k.press if down else self.k.release)(k)

    def move_abs(self, nx, ny, mon):
        self.m.position = (int(mon["left"] + nx * mon["width"]), int(mon["top"] + ny * mon["height"]))

    def move_rel(self, dx, dy):
        self.m.move(int(dx), int(dy))

    def button(self, b, down):
        btn = {0: self.Button.left, 1: self.Button.middle, 2: self.Button.right}.get(b)
        if btn:
            (self.m.press if down else self.m.release)(btn)

    def wheel(self, dx, dy):
        self.m.scroll(int(dx / 100) if dx else 0, int(-dy / 100) or (-1 if dy > 0 else 1 if dy < 0 else 0))


class Gamepad:
    """Virtual Xbox 360 controller (Windows, needs vgamepad + ViGEmBus)."""

    def __init__(self):
        self.pad = None
        vg = OPTIONAL["vgamepad"]
        if vg:
            try:
                self.pad = vg.VX360Gamepad()
                self.vg = vg
                log("Controller support: virtual Xbox 360 pad ready")
            except Exception as e:
                log("Controller support unavailable:", str(e)[:60])
        self.prev = 0

    def update(self, st):
        if not self.pad:
            return
        B = self.vg.XUSB_BUTTON
        order = [B.XUSB_GAMEPAD_A, B.XUSB_GAMEPAD_B, B.XUSB_GAMEPAD_X, B.XUSB_GAMEPAD_Y, B.XUSB_GAMEPAD_LEFT_SHOULDER, B.XUSB_GAMEPAD_RIGHT_SHOULDER, None, None,
                 B.XUSB_GAMEPAD_BACK, B.XUSB_GAMEPAD_START, B.XUSB_GAMEPAD_LEFT_THUMB, B.XUSB_GAMEPAD_RIGHT_THUMB, B.XUSB_GAMEPAD_DPAD_UP, B.XUSB_GAMEPAD_DPAD_DOWN, B.XUSB_GAMEPAD_DPAD_LEFT, B.XUSB_GAMEPAD_DPAD_RIGHT, B.XUSB_GAMEPAD_GUIDE]
        bits = st.get("b", 0)
        for i, btn in enumerate(order):
            if btn is None:
                continue
            if bits & (1 << i):
                self.pad.press_button(btn)
            else:
                self.pad.release_button(btn)
        a = st.get("a", [0, 0, 0, 0])
        self.pad.left_joystick_float(float(a[0]), float(-a[1]))
        self.pad.right_joystick_float(float(a[2]), float(-a[3]))
        tr = st.get("tr", [0, 0])
        self.pad.left_trigger_float(float(tr[0]))
        self.pad.right_trigger_float(float(tr[1]))
        self.pad.update()


class Input:
    def __init__(self, dry=False):
        self.dry = dry
        self.impl = None
        if not dry:
            try:
                self.impl = WinInput() if IS_WIN else PynputInput()
            except Exception as e:
                log("Input control unavailable:", str(e)[:80], "(view-only mode)")
        self.pad = Gamepad() if not dry else None
        self.held_keys, self.held_btns = set(), set()

    def monitor(self):
        if mss is None:
            return {"left": 0, "top": 0, "width": 1920, "height": 1080}
        with MSS() as s:
            mons = s.monitors
            return mons[STATE["monitor"]] if STATE["monitor"] < len(mons) else mons[1]

    def handle(self, m):
        t = m.get("t")
        if self.dry:
            if t not in ("pos", "rel", "pad"):
                log("input", m)
            self.last = m
            return
        if not self.impl:
            return
        try:
            if t == "key":
                (self.held_keys.add if m["d"] else self.held_keys.discard)(m["c"])
                self.impl.key(m["c"], bool(m["d"]))
            elif t == "pos":
                self.impl.move_abs(min(1, max(0, m["x"])), min(1, max(0, m["y"])), self._mon())
            elif t == "rel":
                self.impl.move_rel(m["x"], m["y"])
            elif t == "btn":
                (self.held_btns.add if m["d"] else self.held_btns.discard)(m["b"])
                self.impl.button(int(m["b"]), bool(m["d"]))
            elif t == "wheel":
                self.impl.wheel(m.get("x", 0), m.get("y", 0))
            elif t == "pad" and self.pad:
                self.pad.update(m)
        except Exception as e:
            log("input error:", str(e)[:80])

    def _mon(self):
        if not hasattr(self, "_mcache") or time.time() - self._mtime > 5:
            self._mcache, self._mtime = self.monitor(), time.time()
        return self._mcache

    def release_all(self):
        for c in list(self.held_keys):
            self.handle({"t": "key", "c": c, "d": 0})
        for b in list(self.held_btns):
            self.handle({"t": "btn", "b": b, "d": 0})


# ---------------------------------------------------------------------------
# Session handling
# ---------------------------------------------------------------------------
class Companion:
    def __init__(self, args, ident):
        self.args, self.ident = args, ident
        self.aes = derive_key(ident["id"], ident["key"])
        self.ih = info_hash(ident["id"])
        self.peer_id = secrets.token_bytes(20).decode("latin1")
        self.sessions = {}
        self.seen = {}
        self.fails = []
        self.input = Input(dry=args.dry_input)
        self.trackers = {}
        self.account = Account(ident)

    async def run(self):
        sendermod.get_encoder = _get_encoder
        apply_mode("work")
        await asyncio.gather(self.presence_loop(), *(self.tracker_loop(url) for url in TRACKERS))

    async def presence_loop(self):
        """Keep this PC listed (and its details current) on the AxOS account."""
        last = 0
        while True:
            acct = self.account
            STATUS["account"] = acct.data.get("user", "") if acct.signed_in else ""
            if acct.signed_in and (acct.dirty or time.time() - last > 600):
                acct.dirty = False
                try:
                    await asyncio.to_thread(acct.publish, self.ident)
                    if STATUS["account_error"] or not last:
                        log(f"Listed on AxOS account '{acct.data['user']}' as {self.ident['name']}")
                    STATUS["account_error"] = ""
                    last = time.time()
                except AccountError as e:
                    STATUS["account_error"] = str(e)
                    log("Account:", e)
                    last = time.time() - 480  # retry in ~2 minutes
            await asyncio.sleep(3)

    async def tracker_loop(self, url):
        delay = 2
        while True:
            try:
                async with websockets.connect(url, open_timeout=15, ping_interval=20, max_size=2 ** 22) as ws:
                    self.trackers[url] = ws
                    STATUS["trackers"] = len(self.trackers)
                    delay = 2
                    log(f"Waiting for AxOS on {url.split('//')[1]}")
                    await self.announce(ws)
                    keep = asyncio.create_task(self.reannounce(ws))
                    try:
                        async for raw in ws:
                            try:
                                msg = json.loads(raw)
                            except ValueError:
                                continue
                            if msg.get("offer") and msg.get("peer_id") != self.peer_id:
                                asyncio.create_task(self.on_offer(ws, msg))
                    finally:
                        keep.cancel()
            except Exception as e:
                log(f"Signaling server {url.split('//')[1]} unavailable ({type(e).__name__}); retrying")
            self.trackers.pop(url, None)
            STATUS["trackers"] = len(self.trackers)
            await asyncio.sleep(delay)
            delay = min(60, delay * 2)

    async def announce(self, ws, event="started"):
        msg = {"action": "announce", "info_hash": self.ih, "peer_id": self.peer_id, "numwant": 0, "uploaded": 0, "downloaded": 0, "left": 0, "offers": []}
        if event:  # periodic re-announces omit the event: some trackers disconnect on an empty or unknown one
            msg["event"] = event
        await ws.send(json.dumps(msg))

    async def reannounce(self, ws):
        while True:
            await asyncio.sleep(30)
            await self.announce(ws, None)

    async def reply(self, ws, msg, payload):
        """Send the answer on the tracker the offer came from, or any other one if it dropped meanwhile.
        AxOS announces on every tracker, so any connected tracker can deliver it."""
        data = json.dumps({"action": "announce", "info_hash": self.ih, "peer_id": self.peer_id, "to_peer_id": msg["peer_id"], "offer_id": msg["offer_id"],
                           "answer": {"type": "answer", "sdp": payload}})
        for _ in range(20):
            for sock in [ws] + [w for w in self.trackers.values() if w is not ws]:
                try:
                    await sock.send(data)
                    return
                except Exception:
                    continue
            await asyncio.sleep(0.5)  # every tracker is reconnecting: wait for one
        log("Couldn't deliver the answer to AxOS (signaling servers unavailable)")

    async def on_offer(self, ws, msg):
        oid = msg.get("offer_id")
        if oid in self.seen:
            return
        self.seen[oid] = time.time()
        now = time.time()
        self.fails = [t for t in self.fails if now - t < 60]
        try:
            req = unseal(self.aes, msg["offer"]["sdp"])
        except Exception:
            self.fails.append(now)
            if len(self.fails) <= 10:
                await self.reply(ws, msg, "ERR:key")
            log("Rejected a connection attempt with the wrong key")
            return
        if abs(now - req.get("ts", 0) / 1000) > 300:
            log("Rejected a stale connection request")
            return
        log(f"Connection request from AxOS ({req.get('mode', 'work')} mode)")
        apply_mode(req.get("mode", "work"), req.get("fps"), req.get("height"), req.get("bitrate"))
        STATE["monitor"] = int(req.get("monitor") or 1)
        ice = [RTCIceServer(urls=u) for u in STUN] + [RTCIceServer(**t) for t in req.get("turn", []) if t.get("urls")]
        pc = RTCPeerConnection(RTCConfiguration(iceServers=ice))
        sid = secrets.token_hex(4)
        sess = {"pc": pc, "tracks": [], "channels": {}}
        self.sessions[sid] = sess
        STATUS["sessions"][sid] = req.get("mode", "work")

        @pc.on("datachannel")
        def on_dc(ch):
            sess["channels"][ch.label] = ch

            @ch.on("message")
            def on_msg(raw):
                try:
                    m = json.loads(raw)
                except ValueError:
                    return
                self.on_control(sess, ch, m)
            if ch.label == "ctl":
                ch.send(json.dumps({"t": "hello", "name": self.ident["name"], "os": f"{platform.system()} {platform.release()}", "version": VERSION, "monitors": screen.monitors(),
                                    "encoder": STATE["encoder"], "features": {"audio": bool(sess.get("audio")), "clipboard": bool(OPTIONAL["pyperclip"]), "gamepad": bool(self.input.pad and self.input.pad.pad), "input": bool(self.input.impl) or self.input.dry}}))
                asyncio.ensure_future(self.clipboard_loop(sess, ch))

        @pc.on("connectionstatechange")
        async def on_state():
            log("Connection:", pc.connectionState)
            if pc.connectionState in ("failed", "closed"):
                await self.close(sid)

        await pc.setRemoteDescription(RTCSessionDescription(sdp=req["sdp"], type="offer"))
        screen = ScreenTrack(test_pattern=self.args.test_pattern)
        sess["tracks"].append(screen)
        for t in pc.getTransceivers():
            if t.kind == "video":
                pc.addTrack(screen)
                break
        want_audio = req.get("audio", True) and (OPTIONAL["soundcard"] or self.args.test_pattern)
        if want_audio and any(t.kind == "audio" for t in pc.getTransceivers()):
            audio = LoopbackAudio(test_tone=self.args.test_pattern)
            sess["tracks"].append(audio)
            sess["audio"] = True
            pc.addTrack(audio)
        answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        await self.reply(ws, msg, seal(self.aes, {"sdp": pc.localDescription.sdp, "ts": int(time.time() * 1000), "name": self.ident["name"]}))

    def on_control(self, sess, ch, m):
        t = m.get("t")
        if t == "ping":
            ch.send(json.dumps({"t": "pong", "ts": m.get("ts"), "enc": STATE["encoder"], "fps": STATE["fps"]}))
        elif t == "mode":
            apply_mode(m.get("mode", "work"), m.get("fps"), m.get("height"), m.get("bitrate"))
            for k in STATUS["sessions"]:
                STATUS["sessions"][k] = STATE["mode"]
            log(f"Switched to {STATE['mode']} mode: {STATE['fps']} fps, up to {STATE['max_height']}p, {STATE['max_br'] // 1_000_000} Mbps")
        elif t == "monitor":
            STATE["monitor"] = int(m.get("i", 1))
        elif t == "clip" and OPTIONAL["pyperclip"]:
            try:
                OPTIONAL["pyperclip"].copy(str(m.get("text", ""))[:1_000_000])
                sess["clip"] = m.get("text")
            except Exception:
                pass
        elif t == "release":
            self.input.release_all()
        else:
            self.input.handle(m)

    async def clipboard_loop(self, sess, ch):
        pc = OPTIONAL["pyperclip"]
        if not pc:
            return
        while sess["pc"].connectionState not in ("closed", "failed"):
            await asyncio.sleep(1)
            if STATE["mode"] != "work":
                continue
            try:
                text = pc.paste()
            except Exception:
                continue
            if text and text != sess.get("clip") and ch.readyState == "open":
                sess["clip"] = text
                ch.send(json.dumps({"t": "clip", "text": text[:100_000]}))

    async def close(self, sid):
        sess = self.sessions.pop(sid, None)
        STATUS["sessions"].pop(sid, None)
        if not sess:
            return
        self.input.release_all()
        for t in sess["tracks"]:
            try:
                t.stop()
            except Exception:
                pass
        await sess["pc"].close()
        log("Session ended")


# ---------------------------------------------------------------------------
# Start at sign-in, controller driver, resources
# ---------------------------------------------------------------------------
AUTOSTART_NAME = "AxOS Companion"
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"


def resource(name):
    return os.path.join(getattr(sys, "_MEIPASS", None) or os.path.dirname(os.path.abspath(__file__)), name)


def launch_command():
    if FROZEN:
        exe = sys.executable
        if IS_MAC and ".app/Contents/MacOS/" in exe:
            return ["open", "-a", exe.split(".app/")[0] + ".app", "--args", "--startup"]
        return [exe, "--startup"]
    py = os.path.abspath(sys.executable)
    if IS_WIN and os.path.exists(py.replace("python.exe", "pythonw.exe")):
        py = py.replace("python.exe", "pythonw.exe")
    return [py, os.path.abspath(__file__), "--gui", "--startup"]


def _autostart_path():
    if IS_MAC:
        return os.path.expanduser("~/Library/LaunchAgents/com.axos.companion.plist")
    return os.path.join(os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config"), "autostart", "axos-companion.desktop")


def autostart_enabled():
    if IS_WIN:
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
                winreg.QueryValueEx(k, AUTOSTART_NAME)
            return True
        except OSError:
            return False
    return os.path.exists(_autostart_path())


def set_autostart(on):
    cmd = launch_command()
    if IS_WIN:
        import winreg
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            if on:
                winreg.SetValueEx(k, AUTOSTART_NAME, 0, winreg.REG_SZ, subprocess.list2cmdline(cmd))
            else:
                try:
                    winreg.DeleteValue(k, AUTOSTART_NAME)
                except OSError:
                    pass
        return
    path = _autostart_path()
    if not on:
        if os.path.exists(path):
            os.remove(path)
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if IS_MAC:
        args = "".join(f"<string>{xml_escape(a)}</string>" for a in cmd)
        body = ('<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n'
                f'<plist version="1.0"><dict><key>Label</key><string>com.axos.companion</string><key>ProgramArguments</key><array>{args}</array><key>RunAtLoad</key><true/></dict></plist>\n')
    else:
        body = f"[Desktop Entry]\nType=Application\nName=AxOS Companion\nExec={shlex.join(cmd)}\nX-GNOME-Autostart-enabled=true\n"
    with open(path, "w") as f:
        f.write(body)


def vigem_installer():
    """The ViGEmBus driver installer bundled with vgamepad (needed for virtual controllers on Windows)."""
    if not IS_WIN:
        return None
    roots = [os.path.dirname(OPTIONAL["vgamepad"].__file__)] if OPTIONAL["vgamepad"] else []
    roots.append(resource("vgamepad"))
    arch = "x64" if platform.machine().endswith("64") else "x86"
    for r in roots:
        p = os.path.join(r, "win", "vigem", "install", arch, f"ViGEmBusSetup_{arch}.msi")
        if os.path.exists(p):
            return p
    return None


def gui_available():
    if not IS_WIN and not IS_MAC and not (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")):
        return False
    try:
        import tkinter  # noqa: F401
        return True
    except ImportError:
        return False


def self_test():
    """Used by the build pipeline: check that the packaged app has everything it needs."""
    lines, ok = [f"AxOS Companion {VERSION} self-test ({platform.system()} {platform.machine()}, frozen={FROZEN})"], True
    if MISSING or mss is None:
        ok = False
        lines.append("MISSING: " + ", ".join(MISSING + ([] if mss else ["mss"])))
    lines.append("optional: " + ", ".join(f"{k}={'yes' if v else 'no'}" for k, v in OPTIONAL.items()))
    try:
        import tkinter  # noqa: F401
        lines.append("gui: ok")
    except ImportError as e:
        ok = False
        lines.append(f"gui: FAILED ({e})")
    if not MISSING:
        try:
            ctx = av.CodecContext.create("libx264", "w")
            ctx.width, ctx.height, ctx.pix_fmt = 64, 64, "yuv420p"
            ctx.encode(av.VideoFrame(64, 64, "yuv420p"))
            lines.append("encoders: " + ", ".join(encoder_order()))
        except Exception as e:
            ok = False
            lines.append(f"encoder: FAILED ({e})")
        try:
            aes = AESGCM(account_keys("test", "password")[1])
            assert unseal(aes, seal(aes, {"x": 1})) == {"x": 1}
            RTCPeerConnection()
            lines.append("crypto + webrtc: ok")
        except Exception as e:
            ok = False
            lines.append(f"crypto/webrtc: FAILED ({e})")
    if IS_WIN:
        lines.append("vigembus installer: " + ("bundled" if vigem_installer() else "missing"))
    lines.append("RESULT: " + ("PASS" if ok else "FAIL"))
    report = "\n".join(lines)
    try:
        print(report, flush=True)
    except Exception:
        pass
    with open("axos-selftest.txt", "w") as f:
        f.write(report + "\n")
    sys.exit(0 if ok else 1)


def go_offline(comp, timeout=4):
    """Mark this PC offline on the account (best effort, bounded)."""
    if not comp.account.signed_in:
        return
    t = threading.Thread(target=lambda: _quiet(comp.account.publish, comp.ident, False), daemon=True)
    t.start()
    t.join(timeout)


def offline_on_terminate(comp):
    """Being stopped (logout, shutdown, kill) still marks this PC offline on the account."""
    import signal

    def handler(*_):
        go_offline(comp, 3)
        os._exit(0)
    for name in ("SIGTERM", "SIGHUP"):
        if hasattr(signal, name):
            try:
                signal.signal(getattr(signal, name), handler)
            except (ValueError, OSError):
                pass


def _quiet(fn, *a):
    try:
        fn(*a)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# App window
# ---------------------------------------------------------------------------
class App:
    BG, CARD, LINE, FG, DIM = "#161218", "#221b26", "#33293b", "#f7f0f8", "#a898b0"
    ACC, ACC2, OK, WARN, BAD = "#e8679f", "#a78bfa", "#4ade80", "#fbbf24", "#f87171"

    def __init__(self, args, ident):
        import tkinter as tk
        self.tk, self.args, self.ident = tk, args, ident
        self.q, self.logbuf, self.page, self.busy = queue.Queue(), [], None, False
        LOG_SINKS.append(lambda line: self.q.put(("log", line)))
        if IS_WIN:
            try:
                import ctypes
                ctypes.windll.shcore.SetProcessDpiAwareness(2)
            except Exception:
                pass
        self.root = r = tk.Tk()
        r.title("AxOS Companion")
        r.configure(bg=self.BG)
        r.geometry("480x660")
        r.minsize(430, 580)
        fam = "Segoe UI" if IS_WIN else "Helvetica Neue" if IS_MAC else "DejaVu Sans"
        self.mono = "Consolas" if IS_WIN else "Menlo" if IS_MAC else "DejaVu Sans Mono"
        self.font = lambda size=10, weight="normal": (fam, size, weight)
        try:
            self.icon = tk.PhotoImage(file=resource("icon.png"))
            r.iconphoto(True, self.icon)
        except Exception:
            pass
        r.protocol("WM_DELETE_WINDOW", self.quit)
        self.comp = Companion(args, ident)
        offline_on_terminate(self.comp)
        self.best_enc = "checking…"

        def probe():
            best = probe_encoder()
            self.best_enc = f"{best} ({'hardware' if best != 'libx264' else 'CPU'})" if best else "none found"
        threading.Thread(target=probe, daemon=True).start()
        head = tk.Frame(r, bg=self.BG)
        head.pack(fill="x", padx=22, pady=(20, 6))
        self.logo(head).pack(side="left", padx=(0, 12))
        t = tk.Frame(head, bg=self.BG)
        t.pack(side="left")
        self.label(t, "AxOS Companion", 16, "bold").pack(anchor="w")
        self.label(t, f"Remote Desktop host · v{VERSION}", 9, fg=self.DIM).pack(anchor="w")
        self.body = tk.Frame(r, bg=self.BG)
        self.body.pack(fill="both", expand=True, padx=22, pady=(6, 18))
        if self.comp.account.signed_in or ident.get("manual"):
            self.show_status()
        else:
            self.show_signin()
        threading.Thread(target=self._loop, daemon=True).start()
        r.after(150, self.poll)
        if args.startup:
            r.iconify()

    def _loop(self):
        try:
            asyncio.run(self.comp.run())
        except Exception as e:
            log("Stopped:", e)

    def run(self):
        self.root.mainloop()

    # ---- widgets ----
    def label(self, parent, text, size=10, weight="normal", fg=None, bg=None, font=None, **kw):
        return self.tk.Label(parent, text=text, font=font or self.font(size, weight), fg=fg or self.FG, bg=bg or parent["bg"], justify="left", anchor="w", **kw)

    def button(self, parent, text, cmd, primary=False, small=False):
        bg, hover = (self.ACC, "#f08ab6") if primary else (self.LINE, "#44384f")
        b = self.tk.Label(parent, text=text, font=self.font(9 if small else 10, "bold"), fg="#1d0f17" if primary else self.FG, bg=bg,
                          padx=10 if small else 18, pady=3 if small else 8, cursor="hand2")
        b.bind("<Button-1>", lambda e: cmd())
        b.bind("<Enter>", lambda e: b.configure(bg=hover))
        b.bind("<Leave>", lambda e: b.configure(bg=bg))
        return b

    def entry(self, parent, value="", show=None):
        e = self.tk.Entry(parent, font=self.font(11), bg=self.CARD, fg=self.FG, insertbackground=self.FG, relief="flat",
                          highlightthickness=1, highlightbackground=self.LINE, highlightcolor=self.ACC, show=show or "")
        e.insert(0, value)
        return e

    def logo(self, parent, size=44):
        c = self.tk.Canvas(parent, width=size, height=size, bg=parent["bg"], highlightthickness=0)
        k = size / 64

        def rr(x, y, w, h, r, color):
            pts = [x + r, y, x + w - r, y, x + w, y, x + w, y + r, x + w, y + h - r, x + w, y + h, x + w - r, y + h, x + r, y + h, x, y + h, x, y + h - r, x, y + r, x, y]
            c.create_polygon([v * k for v in pts], smooth=True, fill=color, outline="")
        rr(4, 4, 26, 26, 7.5, self.ACC)
        rr(34, 4, 26, 26, 7.5, "#cf72c4")
        rr(4, 34, 26, 26, 7.5, "#cf72c4")
        c.create_polygon([v * k for v in (47, 35, 59, 47, 47, 59, 35, 47)], fill=self.ACC2, outline="")
        return c

    def clear(self):
        for w in self.body.winfo_children():
            w.destroy()

    # ---- sign in ----
    def show_signin(self):
        tk, B = self.tk, self.body
        self.clear()
        self.page = "signin"
        self.label(B, "Sign in with your AxOS account", 14, "bold").pack(anchor="w", pady=(10, 4))
        self.label(B, "Use the same account as AxOS (Settings → Accounts). This PC then shows up in Remote Desktop on every AxOS device signed in to it. No codes to type.",
                   10, fg=self.DIM, wraplength=430).pack(anchor="w", pady=(0, 10))
        a = self.comp.account.data or self.ident.get("last_account") or {}
        fields = {}
        for key, title, value, show in (("server", "Cloud server (your AxOS gateway address)", a.get("server", ""), None),
                                        ("user", "Username", a.get("user", ""), None), ("pw", "Password", "", "•")):
            self.label(B, title, 9, fg=self.DIM).pack(anchor="w", pady=(8, 3))
            e = self.entry(B, value, show)
            e.pack(fill="x", ipady=7)
            e.bind("<Return>", lambda ev: self.do_signin(fields))
            fields[key] = e
        self.err = self.label(B, "", 9, fg=self.BAD, wraplength=430)
        self.err.pack(anchor="w", pady=(8, 0))
        row = tk.Frame(B, bg=self.BG)
        row.pack(fill="x", pady=(8, 0))
        self.signin_btn = self.button(row, "Sign in", lambda: self.do_signin(fields), primary=True)
        self.signin_btn.pack(side="left")
        link = self.label(row, "Pair with an ID and key instead", 9, fg=self.ACC2, cursor="hand2")
        link.pack(side="right")
        link.bind("<Button-1>", lambda e: self.use_manual())
        self.label(B, "Your gateway address is in AxOS under Settings → Network & Browser → Personal gateway. "
                   "No account yet? Create one in AxOS under Settings → Accounts. Your password never leaves this PC.",
                   9, fg=self.DIM, wraplength=430).pack(anchor="w", side="bottom")
        (fields["server"] if not a.get("server") else fields["user"] if not a.get("user") else fields["pw"]).focus_set()

    def do_signin(self, fields):
        if self.busy:
            return
        server, user, pw = fields["server"].get().strip(), fields["user"].get().strip(), fields["pw"].get()
        if not server or not user or not pw:
            self.err.configure(text="Enter your gateway address, username and password.")
            return
        self.busy = True
        self.signin_btn.configure(text="Signing in…")
        self.err.configure(text="")

        def work():
            try:
                u = self.comp.account.sign_in(server, user, pw)
                self.q.put(("call", lambda: self.signed_in(u)))
            except AccountError as e:
                msg = str(e)
                self.q.put(("call", lambda: self.signin_failed(msg)))
        threading.Thread(target=work, daemon=True).start()

    def signed_in(self, user):
        self.busy = False
        self.ident.pop("manual", None)
        save_config(self.ident)
        log(f"Signed in as {user}")
        self.show_status()

    def signin_failed(self, msg):
        self.busy = False
        if self.page == "signin":
            self.err.configure(text=msg)
            self.signin_btn.configure(text="Sign in")

    def use_manual(self):
        self.ident["manual"] = True
        save_config(self.ident)
        self.show_status()

    # ---- status ----
    def show_status(self):
        tk, B = self.tk, self.body
        self.clear()
        self.page = "status"
        top = tk.Frame(B, bg=self.BG)
        top.pack(fill="x", pady=(8, 2))
        self.dot = tk.Canvas(top, width=14, height=14, bg=self.BG, highlightthickness=0)
        self.dot.pack(side="left", padx=(0, 8))
        self.dot_id = self.dot.create_oval(2, 2, 12, 12, fill=self.WARN, outline="")
        self.headline = self.label(top, "Starting…", 14, "bold")
        self.headline.pack(side="left")
        self.sub = self.label(B, "", 10, fg=self.DIM, wraplength=430)
        self.sub.pack(anchor="w", pady=(2, 10))
        card = tk.Frame(B, bg=self.CARD, padx=14, pady=8)
        card.pack(fill="x")
        self.rows = {}

        def row(key, title, action=None):
            f = tk.Frame(card, bg=self.CARD)
            f.pack(fill="x", pady=4)
            self.label(f, title, 9, fg=self.DIM, bg=self.CARD, width=13).pack(side="left")
            v = self.label(f, "", 10, bg=self.CARD)
            v.pack(side="left", fill="x", expand=True)
            if action:
                self.button(f, action[0], action[1], small=True).pack(side="right")
            self.rows[key] = v
        acct = self.comp.account
        row("name", "This PC", ("Rename", self.rename))
        row("account", "AxOS account", ("Sign out", self.sign_out) if acct.signed_in else ("Sign in", self.show_signin))
        row("signal", "Signaling")
        row("encoder", "Video encoder")
        pad = self.comp.input.pad
        row("pad", "Controllers", ("Install driver", self.install_vigem) if IS_WIN and not (pad and pad.pad) and vigem_installer() else None)
        self.auto = tk.BooleanVar(value=autostart_enabled())
        tk.Checkbutton(B, text="Start AxOS Companion when I sign in to this computer", variable=self.auto, command=self.toggle_auto, font=self.font(10),
                       bg=self.BG, fg=self.FG, selectcolor=self.CARD, activebackground=self.BG, activeforeground=self.FG, highlightthickness=0, bd=0, anchor="w").pack(anchor="w", pady=(10, 0))
        man = tk.Frame(B, bg=self.BG)
        man.pack(fill="x", pady=(10, 0))
        self.label(man, "Pair without an account", 9, "bold", fg=self.DIM).pack(anchor="w")
        self.label(man, f"PC ID       {self.ident['id']}", font=(self.mono, 10)).pack(anchor="w", pady=(3, 0))
        kv = tk.Frame(man, bg=self.BG)
        kv.pack(fill="x", pady=(2, 0))
        self.keylab = self.label(kv, "Secret key  ••••-••••-••••", font=(self.mono, 10), fg=self.DIM)
        self.keylab.pack(side="left")
        self.button(kv, "Copy key", lambda: self.copy(self.ident["key"]), small=True).pack(side="right")
        self.button(kv, "Show", lambda: self.keylab.configure(text="Secret key  " + self.ident["key"], fg=self.FG), small=True).pack(side="right", padx=(0, 6))
        self.label(B, "Activity", 9, "bold", fg=self.DIM).pack(anchor="w", pady=(12, 3))
        self.logbox = tk.Text(B, height=7, bg=self.CARD, fg=self.DIM, font=(self.mono, 9), relief="flat", bd=0, padx=8, pady=6, wrap="word", highlightthickness=0)
        self.logbox.pack(fill="both", expand=True)
        self.logbox.insert("end", "\n".join(self.logbuf[-200:]))
        self.logbox.configure(state="disabled")
        self.logbox.see("end")
        self.refresh()

    def refresh(self):
        acct, sessions = self.comp.account, list(STATUS["sessions"].values())
        if sessions:
            color, head = self.ACC, f"Streaming to AxOS · {sessions[0].title()} mode" + (f" (+{len(sessions) - 1})" if len(sessions) > 1 else "")
        elif STATUS["trackers"]:
            color, head = self.OK, "Ready"
        else:
            color, head = self.WARN, "Connecting…"
        self.dot.itemconfigure(self.dot_id, fill=color)
        self.headline.configure(text=head)
        err = STATUS["account_error"] if acct.signed_in else ""
        if err:
            sub = err
        elif acct.signed_in:
            sub = f"Open Remote Desktop in AxOS on any device signed in as {acct.data['user']} and pick this PC."
        else:
            sub = "Not signed in. In AxOS, open Remote Desktop → Add a PC and enter the PC ID and key below, or sign in to skip that."
        self.sub.configure(text=sub, fg=self.BAD if err else self.DIM)
        pad = self.comp.input.pad
        vals = {"name": self.ident["name"], "account": acct.data.get("user", "Not signed in") if acct.signed_in else "Not signed in",
                "signal": f"{STATUS['trackers']} of {len(TRACKERS)} servers connected",
                "encoder": STATE["encoder"] if STATE["encoder"] != "?" else self.best_enc,
                "pad": "Virtual Xbox controller ready" if pad and pad.pad else "Needs the ViGEmBus driver" if IS_WIN else "Keyboard and mouse only"}
        for k, v in vals.items():
            if self.rows[k].cget("text") != v:
                self.rows[k].configure(text=v)

    def poll(self):
        try:
            while True:
                kind, val = self.q.get_nowait()
                if kind == "log":
                    self.logbuf = (self.logbuf + [val])[-300:]
                    if self.page == "status":
                        self.logbox.configure(state="normal")
                        self.logbox.insert("end", ("\n" if self.logbox.index("end-1c") != "1.0" else "") + val)
                        self.logbox.configure(state="disabled")
                        self.logbox.see("end")
                else:
                    val()
        except queue.Empty:
            pass
        if self.page == "status":
            self.refresh()
        self.root.after(300, self.poll)

    # ---- actions ----
    def copy(self, text):
        self.root.clipboard_clear()
        self.root.clipboard_append(text)
        log("Copied to the clipboard")

    def rename(self):
        from tkinter import simpledialog
        name = simpledialog.askstring("Rename this PC", "Name shown in AxOS:", initialvalue=self.ident["name"], parent=self.root)
        if name and name.strip():
            self.ident["name"] = name.strip()[:40]
            save_config(self.ident)
            self.comp.account.dirty = True

    def sign_out(self):
        from tkinter import messagebox
        if not messagebox.askyesno("Sign out?", "This PC will disappear from Remote Desktop on your AxOS devices.", parent=self.root):
            return
        a = self.comp.account.data
        self.ident["last_account"] = {"server": a.get("server", ""), "user": a.get("user", "")}

        def work():
            self.comp.account.sign_out(self.ident)
            log("Signed out")
            self.q.put(("call", self.show_signin))
        threading.Thread(target=work, daemon=True).start()

    def toggle_auto(self):
        from tkinter import messagebox
        try:
            set_autostart(self.auto.get())
        except Exception as e:
            self.auto.set(not self.auto.get())
            messagebox.showerror("Couldn't change that", str(e), parent=self.root)

    def install_vigem(self):
        path = vigem_installer()
        if not path:
            return
        log("Opening the controller driver installer…")

        def work():
            subprocess.call(["msiexec", "/i", path])
            if not OPTIONAL["vgamepad"]:  # vgamepad can't load until the driver exists
                try:
                    OPTIONAL["vgamepad"] = __import__("vgamepad")
                except Exception as e:
                    log("Controller support still unavailable:", str(e)[:80])
            self.comp.input.pad = Gamepad()
            self.q.put(("call", self.show_status))
        threading.Thread(target=work, daemon=True).start()

    def quit(self):
        from tkinter import messagebox
        if self.args.startup or messagebox.askyesno("Quit AxOS Companion?", "This PC won't be reachable from AxOS until you open AxOS Companion again.", parent=self.root):
            self.root.withdraw()
            go_offline(self.comp)
            os._exit(0)


def cli_login(ident):
    import getpass
    acct, last = Account(ident), ident.get("account") or ident.get("last_account") or {}
    print("\n  Sign in with your AxOS account (the one from AxOS → Settings → Accounts).")
    print("  Your gateway address is in AxOS under Settings → Network & Browser.\n")
    for _ in range(3):
        server = input(f"  Cloud server [{last.get('server', '')}]: ").strip() or last.get("server", "")
        user = input(f"  Username [{last.get('user', '')}]: ").strip() or last.get("user", "")
        pw = getpass.getpass("  Password: ")
        try:
            print("  Signing in…")
            u = acct.sign_in(server, user, pw)
            ident.pop("manual", None)
            save_config(ident)
            print(f"  Signed in as {u}. This PC now shows up in Remote Desktop on your AxOS devices.\n")
            return True
        except AccountError as e:
            print(f"  {e}\n")
            last = {"server": server, "user": user}
    return False


def main():
    ap = argparse.ArgumentParser(description="AxOS Companion: remote desktop host for AxOS")
    ap.add_argument("--name", help="Name shown in AxOS")
    ap.add_argument("--new-key", action="store_true", help="Generate a new secret key (disconnects saved AxOS devices)")
    ap.add_argument("--login", action="store_true", help="Sign in with your AxOS account in the terminal")
    ap.add_argument("--sign-out", action="store_true", help="Sign out of your AxOS account and remove this PC from it")
    ap.add_argument("--gui", action="store_true", help="Open the app window (the default for the packaged app)")
    ap.add_argument("--no-gui", action="store_true", help="Run in the terminal only")
    ap.add_argument("--startup", action="store_true", help=argparse.SUPPRESS)
    ap.add_argument("--self-test", action="store_true", help=argparse.SUPPRESS)
    ap.add_argument("--test-pattern", action="store_true", help="Stream a test pattern instead of the screen")
    ap.add_argument("--dry-input", action="store_true", help="Log input instead of controlling this PC")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    use_gui = (args.gui or (FROZEN and not args.no_gui and not args.login)) and gui_available()
    problem = None
    if MISSING:
        problem = ("AxOS Companion needs a few Python packages. Install them with:\n\n  " + ("py" if IS_WIN else "python3") +
                   " -m pip install aiortc mss pynput numpy websockets\n\nMissing: " + ", ".join(sorted(set(MISSING))))
    elif mss is None and not args.test_pattern:
        problem = "Screen capture needs the 'mss' package: pip install mss"
    if problem:
        print(problem)
        if use_gui:
            import tkinter
            from tkinter import messagebox
            tkinter.Tk().withdraw()
            messagebox.showerror("AxOS Companion", problem)
        sys.exit(1)
    ident = load_identity(args)
    if args.sign_out:
        Account(ident).sign_out(ident)
        print("Signed out. This PC was removed from your AxOS account.")
        return
    if use_gui:
        return App(args, ident).run()
    acct = Account(ident)
    if args.login or (FROZEN and not acct.signed_in and not ident.get("manual") and sys.stdin and sys.stdin.isatty()):
        if not cli_login(ident) and args.login:
            sys.exit(1)
    extras = [k for k in ("pyperclip", "soundcard", "vgamepad", "dxcam") if OPTIONAL[k]]
    print("\n  AxOS Companion " + VERSION)
    print("  ─────────────────────────────────────────")
    print(f"  PC name     {ident['name']}")
    if acct.signed_in:
        print(f"  Account     {acct.data['user']} (this PC appears in Remote Desktop automatically)")
    print(f"  PC ID       {ident['id']}")
    print(f"  Secret key  {ident['key']}")
    print("  ─────────────────────────────────────────")
    if not acct.signed_in:
        print("  In AxOS, open Remote Desktop → Add a PC and enter the ID and key,")
        print("  or run with --login to sign in with your AxOS account instead.")
    print("  Anyone with the key can control this PC. Run with --new-key to change it.")
    print(f"  Extras: {', '.join(extras) if extras else 'none (pip install pyperclip soundcard for clipboard and audio)'}\n")
    comp = Companion(args, ident)
    offline_on_terminate(comp)
    try:
        asyncio.run(comp.run())
    except KeyboardInterrupt:
        go_offline(comp)
        print("\nStopped.")


if __name__ == "__main__":
    main()
