# MorpsSMS 📱🛡️

> **A fast, privacy-focused Android SMS messenger with realme UI / ColorOS aesthetics and 100% on-device AI Spam & Scam Protection.**

---

## 🌟 Key Highlights

- **🛡️ 100% On-Device AI Spam Classifier**: Powered by an incremental Naive Bayes machine learning engine. Zero cloud data transmission — your SMS messages and privacy never leave your phone.
- **🇵🇭 Philippine Scam Shield**: Specialized heuristics detecting GCash, Maya, Casino, unauthorized job offers, and predatory online lending scam patterns.
- **🎨 realme UI / ColorOS Design**: Sleek typography, fluid animations, customizable chat bubbles, and dynamic accent color palettes (*realme Blue, realme Yellow, Emerald Green, Sunset Orange, Cyberpunk Purple, and Flamingo Pink*).
- **🌓 Adaptive Theme Engine**: Native Light Mode, Dark Mode, and true AMOLED pitch-black mode with synchronized system bars.
- **📂 Smart Category Filters**: Instantly sort and view chats by **All**, **Unread**, **Personal**, **Transactions**, **Promotions**, and **Spam**.
- **📌 Pin & Star Messages**: Pin critical conversations to the top of the inbox and star important messages inside any thread.
- **🖼️ Contact Photo Integration**: Automatically links and displays contact photos directly from your phone's address book with fallback initials avatars.
- **📶 Multi-SIM Support**: Effortlessly switch SIM slots for incoming and outgoing SMS.
- **⚡ Quick Actions**: Mark as read or reply directly from heads-up notification cards.
- **💾 Local Backup & Restore**: Export your message archive and AI vocabulary to local JSON with instant sharing support.

---

## 📲 Download & Installation

Grab the latest APK directly from the [Releases](https://github.com/cmorpe7-ui/MorpsSMS/releases) page:

1. Download **`MorpsSMS-v1.0.0.apk`**.
2. Tap the downloaded file on your Android device to install.
3. Open **MorpsSMS** and set it as your default SMS application to enable full spam blocking and delivery features.

---

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Database:** Room (SQLite) with Reactive Kotlin StateFlow
- **Architecture:** Clean Architecture + MVVM (Model-View-ViewModel)
- **Classification Engine:** On-device Multinomial Naive Bayes with Laplace smoothing and active learning feedback loop

---

## 🧑‍💻 Building from Source

To build the APK locally using Gradle:

```bash
cd android_sms_app
./gradlew assembleDebug
```

The output APK will be generated at:
```
android_sms_app/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the MIT License.
