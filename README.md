<img src="assets/icon.svg" width="64" height="64" alt="">

# iPerfClient-Android

An [iperf3](https://github.com/esnet/iperf) client for Android with a speed-test style dial, styled after Samsung One UI 9 and built for foldables like the Galaxy Z Fold8.

## Features

- **iperf3 in pure Kotlin.** The app speaks the iperf3 protocol itself, with no bundled binary: TCP and UDP, upload, download (reverse) and bidirectional tests, 1–16 parallel streams. It works with any iperf3 server (3.x).
- **Test modes.** *Full* runs ping, download and upload in a row; *Download*, *Upload* and *Both* test one direction or both at once; *Ping* measures latency, jitter and packet loss on its own.
- **Continuous mode.** Keeps measuring until you tap Stop. Single-direction tests run as one open-ended iperf3 session (`-t 0`), so the measurement never drops out between rounds; Full mode repeats whole rounds. Useful for walking around to find weak Wi-Fi spots.
- **Speed-test dial.** A springy pointer, a gradient arc that switches to a multi-gigabit scale when needed, a latency scale for ping, and a live throughput chart.
- **Latency under load.** Ping keeps running during download and upload, so you can see how much the link's buffers add when it is busy.
- **History.** Every result is kept with its chart, retransmits, UDP jitter and loss, and the connection it ran over (Wi-Fi generation, band, link speed).
- **Servers.** Add your own server by IP or host name. Four public servers are included; their port ranges are tried in turn when one instance is busy.
- **Made for foldables.** The cover screen gets one pane with a floating tab bar. Unfolded, the dial and the controls sit on either side of the hinge; in tabletop posture the dial moves above the fold.
- **Light and dark**, accent colour from the system palette, English and German.

## Permissions

| Permission | What it's for |
|---|---|
| `INTERNET` | The iperf3 control and data connections |
| `ACCESS_LOCAL_NETWORK` ("Nearby devices") | Reaching servers on your own network. Android 17 blocks LAN connections without it; the app asks when you start a test against a LAN address. |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Showing the Wi-Fi generation, band and link speed |

## Run your own server

Start iperf3 on a computer in the same network and add its IP address in the app:

```sh
iperf3 -s
```

## Build

You'll need the Android SDK (API 37) and JDK 17 or newer. Android Studio's bundled JBR works.

```sh
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest        # unit tests
./gradlew lintDebug
adb install -r app/build/outputs/apk/release/app-release.apk
```

Use the release build on a phone; debug builds run Compose unoptimised. Release builds are signed with the key named in a gitignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or `SIGNING_*` environment variables, and fall back to the debug key without one.

The engine tests can also run against a real server:

```sh
IPERF_SERVER=192.168.1.10 ./gradlew testDebugUnitTest            # TCP, UDP, bidirectional, back-to-back
IPERF_RANGE=ping.online.net:5200-5209 ./gradlew testDebugUnitTest # walks a public server's ports
```

---

Created in [T3 Code](https://t3.codes).
