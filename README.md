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

## Credits
Based on [Brokenithm-Android](https://github.com/tindy2013/Brokenithm-Android) by tindy2013.
The Windows server can be found in the [Brokenithm-Android-Server](https://github.com/tindy2013/Brokenithm-Android-Server) repository.
