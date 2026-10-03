# StudyLock — Android Study & Screen-Time Accountability App

StudyLock turns distracting social-media screen time into a reward earned through active studying.

```
       SCAN PHYSICAL STUDY MATERIAL
                  ↓
          EXTRACT OCR TEXT
                  ↓
       GENERATE ACTIVE QUIZ / CARDS
                  ↓
           DEMONSTRATE MASTERY
                  ↓
        EARN BANKED SCREEN TIME
                  ↓
        USE TIME IN PROTECTED APPS
                  ↓
     TIME EXHAUSTED → SCREEN TIME LOCKED
```

---

## Key Features

1. **Camera Scanner & ML Kit Text Recognition**
   - Live CameraX preview with `PreviewView` (COMPATIBLE mode) and target reticle.
   - Text detection via Google ML Kit Text Recognition (`com.google.android.gms:play-services-mlkit-text-recognition`).
   - Interactive bounding block selection: Tap blocks to include/exclude, Select All, and Clear Selection.
   - Non-destructive OCR text normalization preserving paragraphs and headings.
   - Zero-permission photo picker fallback (`PickVisualMedia`) for scanning screenshots or existing textbook photos.

2. **AI & Deterministic Study Generation**
   - Online study generation powered by Gemini API when configured.
   - Robust offline heuristic NLP engine that analyzes grammar, sentence structure, and terminology to generate plausible 4-option multiple-choice questions with explanations and flashcards.

3. **Study Modes**
   - **Multiple Choice Quiz**: Short (3 Qs, max 5m reward), Standard (5 Qs, max 15m reward), or Deep Dive (10 Qs, max 30m reward) with immediate correctness feedback, detailed explanations, and subtle haptic feedback.
   - **Flashcards**: Interactive 3D flip animation with self-grading (`Retry`, `Hard`, `Easy`).

4. **Screen-Time Bank & Authoritative Ledger**
   - All balance changes are stored as immutable `ScreenTimeTransactionEntity` rows (`STUDY_REWARD`, `SCREEN_TIME_USED`, `MANUAL_ADJUSTMENT`, etc.).
   - Authoritative balance is derived from transactions, surviving process death, reboots, and configuration changes.
   - Duplicate reward prevention ensures sessions can only award time once.

5. **High-Precision App Timer & Screen-Time Blocker**
   - Dual-engine tracking architecture combining real-time Accessibility Service events with authoritative `UsageStatsManager` system stats.
   - **Monotonic Elapsed-Time Engine:** Tracks active foreground app usage with millisecond precision using `SystemClock.elapsedRealtime()`.
   - **Transient Overlay Immunity:** Soft keyboards, notification shade, volume sliders, and system UI overlays no longer cancel active session tracking.
   - **System-Level Ledger Reconciliation:** Automatically synchronizes with Android's `UsageStatsManager` on app resume and every 15-60 seconds, ensuring not a single second of usage is lost or undercounted.
   - **Per-App Usage Transparency:** Shows exact daily foreground time per protected app (e.g. TikTok, Instagram, YouTube) and overall phone screen time.
   - **Screen Time Locked Redirection:** When earned balance reaches zero, `StudyLockBlockerActivity` immediately intercepts protected apps, directing the student to "Start Study Session".

6. **Accountability & Anti-Quitting System**
   - Confirmation dialog before abandoning an active study session: *"Your progress will be saved, but this session will not generate a reward unless completed."*
   - Active session recovery: If interrupted by Android system or backgrounding, the dashboard offers to resume the unfinished session.
   - Time manipulation detection compares monotonic `SystemClock.elapsedRealtime()` against wall-clock time.

7. **Cyber-Academic Dark Design System**
   - Deep Obsidian background (`#0F172A`)
   - Slate surfaces with elevation (`#1E293B`, `#334155`)
   - Electric Indigo accents (`#6366F1`)
   - Neon Emerald for correct answers and earned time (`#10B981`)
   - Coral Red for locked states and exhausted balance (`#EF4444`)

---

## Architecture

- **Clean Architecture / MVVM**: Clear separation between UI composables, ViewModels, Repository, Domain engines, Room Database, and Android Services.
- **Single-Activity Architecture**: Jetpack Compose with Compose Navigation.
- **Room Database**:
  - `StudyDeckEntity`: Reusable decks and subjects.
  - `StudyMaterialEntity`: Scanned OCR source text.
  - `QuestionEntity`: 4-option questions with explanations.
  - `FlashcardEntity`: Front/back study cards.
  - `StudySessionEntity`: Session tracking, scores, and durations.
  - `StudyAnswerEntity`: Per-question answer logs.
  - `ScreenTimeTransactionEntity`: Transactional balance ledger.
  - `BlockedAppEntity`: Protected app registry.
- **DataStore**: Preferences for daily goals, reward caps, haptics, and active session snapshot.

---

## Permissions & Android Platform Compliance

StudyLock adheres strictly to Android platform policies:
- **Camera (`android.permission.CAMERA`)**: Contextually requested before opening scanner.
- **Usage Stats (`android.permission.PACKAGE_USAGE_STATS`)**: Optional foreground app discovery.
- **Accessibility Service (`StudyLockAccessibilityService`)**: Monitored for window state changes on user-selected apps only. Never collects personal keystrokes or private window content.
- **Overlay (`android.permission.SYSTEM_ALERT_WINDOW`)**: Provides blocker redirection.
- **Vibration (`android.permission.VIBRATE`)**: Tactile feedback on answer selection and completion.

---

## Build & Test Instructions

- Run all unit and Robolectric tests:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- Build debug APK:
  ```bash
  gradle :app:assembleDebug
  ```
