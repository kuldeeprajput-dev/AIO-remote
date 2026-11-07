# AIO Remote — Desktop Companion Receiver & Testing Guide

## 1. Wi-Fi Desktop Protocol Specification (`ws://<host>:8765/ws`)

### Discovery
- Service Type: `_slateremote._tcp.` via mDNS / Android `NsdManager`.
- TXT record `os`: `WINDOWS`, `MACOS`, or `LINUX`.

### Security & Pairing Handshake
1. Server sends `AUTH_CHALLENGE` containing `{ serverId, serverName, os, nonce }`.
2. **First-time Wi-Fi connection:** The current app sends `APPROVAL_REQUEST`. On Linux with Zenity, click **Allow** on the computer. The receiver returns a token, which the app stores in Android Keystore. Older clients can still use the legacy code protocol.
3. **Subsequent sessions:** The app proves it holds the saved token by sending `AUTH_VERIFY` with an HMAC of the server nonce. The receiver accepts remote input only after approval or token verification.

### Shared Event Messages
All control events include monotonic `seq` and `ts` (epoch ms):
- `POINTER_MOVE` (`dx`, `dy`)
- `POINTER_CLICK` (`button`, `count`)
- `POINTER_DOWN` / `POINTER_UP` (`button`)
- `POINTER_SCROLL` (`dx`, `dy`)
- `KEY_DOWN` / `KEY_UP` / `KEY_TAP` (`key`, `modifiers`)
- `TEXT_INPUT` (`text` — full Unicode)
- `MEDIA_CONTROL` (`action`, optional `volumePercent`)
- `PRESENTATION_COMMAND` (`action`, optional `laserX`, `laserY`)
- `PING` / `PONG` (RTT latency measurement)
- `RELEASE_ALL` (automatically triggered on disconnect or app pause so no key/button stays stuck)

---

## 2. Running the Companion Receiver

```bash
pip install websockets pynput zeroconf dbus-next
python3 receiver/slate_receiver.py
```

### Linux X11 vs Wayland Notes
- **X11**: `pynput` injects mouse, scroll, keyboard, and media events directly.
- **Wayland**: The Wi-Fi touchpad uses the Remote Desktop portal in the active desktop session. Move a finger on the phone’s touchpad and approve pointer control on the laptop when prompted. The receiver closes the pointer session when the phone disconnects. Separate Wayland keyboard support is required for typing.

---

## 3. Manual Device-Testing Instructions

### A. Bluetooth HID (Phone -> Laptop/Desktop/TV)
1. Install the APK on a physical Android 9+ device that supports `BluetoothHidDevice`.
2. Open **Devices** -> grant Bluetooth permissions when prompted.
3. Open your laptop's Bluetooth settings and make it discoverable, then tap **Add Device -> Nearby Bluetooth** and select your computer.
4. Confirm the OS Bluetooth pairing prompt on both screens.
5. Switch to **Remote -> Touchpad** and test one-finger cursor movement, single tap, two-finger tap (right click), two-finger scroll, and **Media** keys.

### B. Wi-Fi Desktop Control
1. Ensure phone and computer are on the same local Wi-Fi subnet.
2. Start `python3 receiver/slate_receiver.py` on the computer. On Linux, have Zenity installed and run the receiver from your desktop session.
3. In AIO Remote, tap **Add Device -> Devices on Wi-Fi** (or **Enter IP**) and select the computer. Click **Allow** on the computer if prompted.
4. Test **Touchpad**, **Keyboard** (including Unicode), **Presentation** (including Laser Pad), and **Shortcuts**.

### C. Smart TV Remote
1. For **Android TV / Google TV**, pair your phone via Bluetooth in TV Settings (`Remotes & Accessories`) and connect via **Bluetooth HID** for reliable D-Pad, Back, Home, Volume, and Play/Pause control.
2. Wi-Fi mDNS discovery (`_androidtvremote2._tcp.`) will detect compatible TVs on your LAN and report protocol status honestly without using unsecured ADB.
