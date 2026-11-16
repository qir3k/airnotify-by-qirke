# AirNotify by qirke ⚡

**Wrist alerts and smart notifications for Google Fitbit Air.**

AirNotify is a modern Android application engineered specifically for the Google Fitbit Air band. While Google Health and the standard Fitbit app only trigger the vibration motor for morning wakeup alarms and heart-rate warnings, AirNotify unlocks the hardware vibration motor for everyday notifications, phone calls, favorite person filtering, and hourly wrist chimes.

---

## 🌟 Key Features

### 🔔 Smart App Notifications & Favorite Person Filtering
- **Any Installed App:** Intercept and route notifications from Facebook Messenger, WhatsApp, Telegram, Google Messages (SMS), Instagram, Discord, Gmail, and any custom installed app.
- **Favorite Persons Only Filter:** Choose to only vibrate when an alert comes from specific people (e.g. only vibrate Messenger when your girlfriend messages, or family/important contacts).
- **Exclude Filter Mode:** Mute specific noisy chats or persons while letting all other messages vibrate.
- **Custom Patterns per App:** Assign Single Pulse, Double Pulse, Triple Pulse, or Urgent Burst to distinguish apps by feel.

### 📞 Phone Calls & Favorite Callers
- **Continuous Ringing:** Pulses the band continuously while your phone is ringing until you answer, reject, or tap the band to silence it.
- **Caller Filter:** Vibrate for all incoming calls, or only for favorite/starred contacts (with 1-tap starred contacts import).
- **Call Disconnect & Tap-to-Silence:** Automatically stops vibrating as soon as the call is answered or rejected.

### ⏰ Hourly Chime
- **Subtle Wrist Tap at :00:** Get a gentle wrist tap at the top of every hour so you stay on schedule without looking at your watch or phone.
- **Configurable Active Hours:** Choose the active time window (e.g., 08:00 to 22:00) so the chime only runs while you are awake.
- **Gentle Pattern:** Uses a discrete single or double tap designed specifically for hourly reminders.

### 🌙 Night Quiet Hours (Do Not Disturb)
- Set custom sleep and wake hours (e.g. 23:00 to 07:00) to automatically mute all vibrations during the night.

### 🔄 In-App Self-Updater (SonixLink-style)
- **Direct GitHub Releases Integration:** Checks the repository releases for newer APK builds.
- **In-App Download & Install:** Displays release notes, downloads the APK with live progress, and launches Android's Package Installer directly within the app.
- **Auto-check on Launch:** Optionally checks for updates when you open the app.

### 🎨 Modern Material Design 3 (Material You)
- Built with **Jetpack Compose** and **Material 3**.
- Supports **Dynamic Color** (adapts to your Android 12+ wallpaper color palette).
- Clean dashboard with band connection status badge, live activity log, and instant vibration test buttons.

---

## 🛠️ How It Works (Protocol & BLE Engine)

1. **Bluetooth Low Energy (BLE) & GoldenGate:**
   AirNotify embeds the native Fitbit GoldenGate (`libgg.so`, `libxp.so`) and BitGatt stack (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
2. **Gattlink & DTLS 1.2 Session:**
   Communicates directly with the Fitbit Air over BLE Gattlink with a secure DTLS 1.2 handshake.
3. **CoAP DirectSettings Protocol:**
   Writes protobuf-encoded settings to the `/settings` endpoint (`PUT` to inspect, `POST` to toggle haptic intensity between High/Low/Restore). The hardware band responds to the setting change with a physical vibration confirmation.
4. **Safety Checkpoint & Recovery (`PendingHapticRecovery`):**
   Before pulsing, AirNotify checkpoints the band's current haptics intensity in persistent storage. After pulsing, it verifies restoration. If a Bluetooth disconnect occurs mid-pulse, recovery runs automatically on the next connection.

---

## 📱 Permissions Required

- **Nearby Devices / Bluetooth (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`):** To discover, bond, and exchange data with the Fitbit Air band.
- **Notification Access (`BIND_NOTIFICATION_LISTENER_SERVICE`):** Required to detect incoming notifications and filter by sender name.
- **Phone State (`READ_PHONE_STATE`, `READ_CALL_LOG`):** To detect incoming calls and continuous ringing.
- **Contacts (`READ_CONTACTS`):** To allow selecting favorite contacts and matching incoming phone numbers/senders against your contact list.
- **Exact Alarms (`SCHEDULE_EXACT_ALARM`):** To wake the phone precisely at `:00` for the Hourly Chime.
- **Battery Optimization Exemption:** To prevent Android from sleeping the background listener service.
- **Install Packages (`REQUEST_INSTALL_PACKAGES`):** For the in-app self-updater.

---

## 🔨 Building from Source

```bash
# Clone the repository
git clone https://github.com/qirke/airnotify-by-qirke.git
cd airnotify-by-qirke

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License & Credits
- Built with reference to [AirBridge](https://github.com/ashu-sa/AirBridge) and [fitbit-air](https://github.com/ThoreKoritzius/fitbit-air).
- Developed by **qirke**.
