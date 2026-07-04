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
10. **Router controls** — an in-app browser to your router's admin page, plus
    quick address hints, so you can block a device, set MAC filters, or cap a
    device's bandwidth (QoS) using the controls the router already provides.

## Managing / blocking devices

Kicking a device off the network or throttling ("lagging") it for testing is
done through the **Router controls** tab: log into your own router and use its
block-list / QoS features. Those controls run on the hardware you own and apply
to the network you administer — which is both the reliable way to do it and the
right one. NetTools deliberately does not ship ARP-spoofing "cut" attacks:
those forge packets against every device on whatever network you're attached to
(including ones you don't own), need root, and are the reason such apps get
pulled from the Play Store.

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
