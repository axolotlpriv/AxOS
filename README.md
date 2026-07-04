# NetTools

A Wi-Fi network toolkit for Android. Connect to your network and get a full
picture of what's on it — every device, signal quality, open ports, latency,
and more. Built with Kotlin + Jetpack Compose and packaged by GitHub Actions.

## Features

1. **Connection dashboard** — SSID, your IP, gateway, subnet, netmask, DNS, AP
   BSSID, link speed, signal (dBm), and frequency at a glance.
2. **Network scanner** — parallel sweep of your `/24` subnet + ARP-table read to
   list every reachable device with IP, MAC, hostname, and vendor.
3. **Device fingerprinting** — MAC-to-vendor lookup from a bundled OUI table,
   reverse-DNS hostnames, and gateway/self detection.
4. **Intruder alerts** — mark devices as trusted; anything new on the network is
   flagged as **New** so you can spot a device you don't recognize.
5. **Port scanner** — TCP connect scan of 35 common service ports per device,
   with friendly service names (SSH, HTTP, RTSP, Plex, printers, cameras…).
6. **Wi-Fi analyzer** — nearby access points sorted by signal, with band,
   channel, security, and a per-AP signal bar to find the least crowded channel.
7. **Internet speed test** — measured download throughput.
8. **Latency / ping monitor** — live latency graph to any host or your router,
   with min/avg/max/last stats.
9. **Wake-on-LAN** — send a magic packet to power on your own machines.
10. **Router controls with auto-detection** — the app fingerprints your gateway
    (MAC OUI + HTTP probe of the admin page) to identify the vendor and model —
    e.g. a TP-Link Archer / TL-… / Deco — then gives you **Block** and
    **Throttle** buttons per device that jump straight to the right admin screen
    with model-specific steps and the target MAC ready to paste, all inside an
    in-app browser.

## Blocking & throttling devices

Tap any device in the scanner and choose **Block** or **Throttle**. NetTools
detects your router (TP-Link is recognised down to the firmware family) and
opens its admin page with tailored instructions:

- **Block** → the router's Access Control / MAC filter drops the device from the
  network (this is the legitimate "kick it off" — enforced by the router you own).
- **Throttle** → the router's QoS / Bandwidth Control caps that device's speed,
  which is a real, repeatable way to "lag" a device for testing.

Because these run through the router you administer, they only affect *your*
network. NetTools deliberately does **not** ship ARP-spoofing or 802.11 deauth
"cut" attacks: those forge packets against every device on whatever network
you're attached to (including ones you don't own), need root / injection-capable
hardware, and are the reason such apps get pulled from the Play Store.

## Building

### GitHub Actions (recommended)
Every push builds a debug APK. Grab it from the **Actions** run's artifacts
(`NetTools-debug-apk`), or from the rolling `latest-debug` GitHub Release.

### Locally
Requires JDK 17 and the Android SDK (platform 35, build-tools 34).

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Installing

The APK is a **debug** build (unsigned for release). On your phone, enable
"Install unknown apps" for your browser/file manager, then open the APK. Grant
Location when asked — Android requires it to scan Wi-Fi and read network info.

## Notes & limitations

- Everything works on a **non-rooted** phone. Some vendors throttle background
  Wi-Fi scans to a few per minute (Android's scan-throttling), so AP lists may
  refresh slowly.
- `isReachable` (ICMP) is restricted on Android, so the scanner also uses TCP
  probes; a very quiet device with no open ports and no ARP entry may not show.
- Reverse-DNS hostnames depend on your router publishing them.
