# Smart SMS Spam Blocker (Complete Android App)

A production-grade, feature-complete Android SMS application designed with a clean **two-tab layout** ("Inbox" vs "Spam & Filter") and a built-in, on-device **Naive Bayes Active Learning Engine** customized for Philippine spam patterns.

---

## Complete Feature Matrix

### 1. Clean Modern Interface & Customization
* **Large Bold Header:** Prominent `Messages` header with real-time search, spam simulator, and customization settings.
* **Two-Tab System:** Clean switcher between **[ Inbox ]** and **[ Spam & Filter ]** with dynamic badge counters.
* **Theme Synchronization:** Seamlessly syncs with your phone's Light and Dark system settings, plus an optional AMOLED Pure Black mode.
* **Personalization Hub:**
  * 🎨 **6 Accent Themes:** Electric Amber, Cyber Blue, Emerald Green, Royal Violet, Crimson Red, and Minimalist Slate.
  * 💬 **Chat Bubble Styles:** Rounded, Soft Square, and Classic Message Tail.
  * 🔤 **Adjustable Font Sizing:** Compact, Standard, Large, and Extra Large typography.

### 2. Interactive Swipe Gestures
* **Swipe Left:** Instantly toggles the message as **Unread** (adds a vibrant unread badge & increments tab unread counter).
* **Swipe Right:** Slides open to reveal two action buttons:
  * 🗑️ **Delete:** Deletes the thread from the phone.
  * 🛡️ **Delete & Spam:** Deletes the thread AND trains the on-device AI model to permanently block future texts with similar patterns.

### 3. Multi-Select / Batch Operations
* **Long-press any message** in the list to enter **Selection Mode**.
* Shows selection checkboxes on cards and a dedicated top bar:
  * `N selected` counter.
  * **Select All** button.
  * **Batch Delete** (delete all selected threads at once).
  * **Batch Mark as Spam** (trains AI on all selected messages and moves them).

### 4. 1-on-1 Chat Bubbles & Context Menu
* Tapping any conversation opens the full chat history.
* **Speech Bubbles:** Received messages on the left; sent messages on the right in stylized accent bubbles with delivery checkmarks.
* **Long-press on any chat bubble:** Opens a context menu to:
  * 📋 **Copy text** to clipboard.
  * ↗️ **Forward / Share** via Android share sheet.
  * 🗑️ **Delete single message**.
* **Direct Dialing:** Phone call button in the header opens phone dialer.

### 5. Notification Quick Reply & Actions
* When a text arrives:
  * **Inline Quick Reply:** Type and send your reply directly from the notification shade without opening the app (`RemoteInput`).
  * **Mark as Read Action:** Dismisses notification and marks the thread as read in background.
  * **Privacy Mode:** Option to hide sensitive message previews on the lock screen.

### 6. Contact Picker
* In the Compose screen, tap the 👤 **Contacts icon** to open your phone's address book and select a recipient with one touch.

### 7. Dual-SIM Carrier Switching (Globe / Smart / DITO)
* Automatically detects active SIM slots via `SubscriptionManager`.
* Toggle pill in the chat bar allows you to select which SIM card to transmit from before pressing Send.

### 8. Manual Number Blacklist & Rule Management
* Tapping `⋮` opens **Filter & Security Settings**:
  * **Sensitivity Slider:** Switch between *Aggressive (60%)*, *Balanced (70%)*, or *Relaxed (85%)*.
  * **Blocked Numbers Tab:** Manually add specific numbers to permanently block, or unblock existing numbers.
  * **AI Learning Dashboard:** Live counters showing total spam words vs. ham words learned.
  * **Re-scan SMS Button:** Re-indexes and re-sorts phone message history on demand.

### 9. System SMS Ingestion & Sync
* **Zero Blank Inbox:** Automatically imports your existing SMS history from Android's `Telephony.Sms` provider on first launch.
* **Full OS Sync:** Legitimate messages (incoming Ham and outgoing Sent) are written to Android's system provider so smartwatches, Android Auto, and Google Cloud SMS Backup stay synchronized.
* **Spam Isolation:** Flagged spam messages are strictly withheld from the system provider so they never disturb other apps.

### 10. On-Device Bayesian Active Learning (100% Offline)
* **Zero Cloud / Privacy-First:** All classification runs locally in Room (SQLite). No text ever leaves the phone.
* **Philippine Scam Heuristics:**
  * Currencies: `₱`, `PHP`, `Php`
  * Links: `bit.ly`, `tinyurl`, `t.me`, `cutt.ly`, `wa.me`
  * Keywords: `gcash`, `maya`, `jili`, `casino`, `slot`, `pautang`, `nanalo`, `telegram`, `salary`, `task`, `rebate`.
* **Contacts Whitelist:** Saved contacts automatically bypass the spam filter.

---

## Project Structure

```
android_sms_app/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       │   ├── drawable/                          # Vector SMS icons & branding
│       │   ├── values/themes.xml                  # Day theme configuration
│       │   └── values-night/themes.xml            # Night theme configuration
│       └── java/com/spamblocker/sms/
│           ├── SpamBlockerApp.kt                  # App initialization
│           ├── data/
│           │   ├── importer/SmsSyncManager.kt     # System SMS history sync
│           │   ├── local/
│           │   │   ├── AppDatabase.kt             # Room DB (v3)
│           │   │   ├── dao/
│           │   │   │   ├── MessageDao.kt          # Pinned, batch & thread queries
│           │   │   │   ├── WordStatDao.kt         # Vocabulary frequencies
│           │   │   │   └── BlockedNumberDao.kt    # Manual blacklist DAO
│           │   │   └── entity/
│           │   │       ├── MessageEntity.kt       # SMS model with isPinned & subId
│           │   │       ├── WordStatEntity.kt      # Word counts
│           │   │       └── BlockedNumberEntity.kt # Blacklist model
│           │   ├── preferences/
│           │   │   └── AppSettingsManager.kt      # Themes, colors, bubble shapes & fonts
│           │   └── repository/SmsRepository.kt    # Unified data broker
│           ├── ml/BayesClassifier.kt              # On-device Bayes math & PH rules
│           ├── receiver/
│           │   ├── SmsDeliverReceiver.kt          # Incoming SMS filter & ingestion
│           │   ├── MmsDeliverReceiver.kt          # Android Default SMS requirement
│           │   └── NotificationActionReceiver.kt  # Quick reply & mark read handler
│           ├── service/RespondViaMessageService.kt# Default SMS requirement
│           ├── ui/
│           │   ├── MainActivity.kt                # Default SMS role launcher
│           │   ├── ComposeActivity.kt             # Compose new SMS + Contact picker
│           │   ├── screens/
│           │   │   ├── HomeScreen.kt              # Two-tab list, search, batch mode
│           │   │   └── ConversationActivity.kt    # Chat bubbles + context menu
│           │   ├── components/
│           │   │   ├── MessageCard.kt             # Swipeable message card
│           │   │   ├── TabPill.kt                 # Two-tab segment selector
│           │   │   ├── BlockSettingsDialog.kt     # Blacklist & sensitivity settings
│           │   │   └── CustomizationDialog.kt     # Themes, bubble styles & font sizing
│           │   ├── theme/
│           │   │   ├── Color.kt                   # Accent palettes & Dark/Light colors
│           │   │   └── Theme.kt                   # Dynamic Material 3 & System sync
│           │   └── viewmodel/MessagesViewModel.kt # StateFlows & batch actions
│           └── util/
│               ├── ContactUtils.kt                # Contacts whitelist & lookup
│               ├── NotificationUtils.kt           # Heads-up notifications + RemoteInput
│               ├── SimUtils.kt                    # Dual-SIM carrier detector
│               └── SmsSender.kt                   # Outgoing SMS & system sync
├── gradle/wrapper/gradle-wrapper.properties
└── gradlew.bat
```

---

## How to Build & Run

1. Open **Android Studio** and click **Open**, selecting the `android_sms_app/` folder.
2. Build and run on an Android device (Android 8.0+ / API 26+).
3. Accept the prompt: **"Set as Default SMS App"**.
4. Test the AI immediately by tapping the **🧪 (test/science)** icon in the top header.
