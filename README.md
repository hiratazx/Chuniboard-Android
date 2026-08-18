# Chuniboard

Chuniboard is an enhanced Android-based controller for rhythm games, based on the [Brokenithm-Android](https://github.com/tindy2013/Brokenithm-Android) project and inspired by [Brokenithm-iOS](https://github.com/esterTion/Brokenithm-iOS). 

This project aims to provide a more robust and feature-rich experience for Android users.

**Note**: This project is unfortunately Android only.

## New Features
* **Grid Overlay Mode**: Visualizes the touch and air note zones directly on your screen.
* **Expanded Air Sensors**:
    * **Camera Air**: Uses the front camera to track your hand height for precise air notes.
    * **Light Air**: Triggers Air notes by blocking the ambient light sensor (usually near the earpiece).
    * **Proximity Air**: Uses the phone's proximity sensor to detect hand movement above the device.
    * **Auto Air**: Automatically triggers Air height whenever any key is touched.
* **Custom Air Threshold**: Adjust the height of the "Air Line" boundary in settings to suit your hand size and device.
* **Immersive Borderless UI**: Fullscreen, edge-to-edge experience for maximum play area.
* **Quick Toggle System**: Click the connection mode (UDP/TCP) or Air mode labels on the main screen to switch them instantly.
* **And more stuff in development** 🤫

## Connection
Supports UDP and TCP connection to host.
* **UDP**: Recommended for wireless connections.
* **TCP**: Recommended for `adb reverse` port forwarding over USB for the lowest possible latency.

---

## ⚠️ Must Read — Xiaomi / HyperOS Users

### Multitouch not working? (Xiaomi Pad, HyperOS)

If some touch inputs don't register when placing multiple fingers simultaneously, you need to add **Chuniboard to Game Turbo**:

1. Open **Game Turbo** (Xiaomi's gaming mode app)
2. Tap **Add Games** and add **Chuniboard** to the list
3. Launch Chuniboard **from Game Turbo** (or it will be launched automatically when you open the app after adding it)

Game Turbo disables HyperOS's aggressive touch rejection / gesture interception for the app, which is what causes simultaneous touches to be dropped. This is a HyperOS system-level limitation — no app-side fix can fully replace this workaround.

> Tested on: **Xiaomi Pad 7 Pro / HyperOS 3 (Android 16)**

---

## Credits
Based on [Brokenithm-Android](https://github.com/tindy2013/Brokenithm-Android) by tindy2013.
The Windows server can be found in the [Brokenithm-Android-Server](https://github.com/tindy2013/Brokenithm-Android-Server) repository.
