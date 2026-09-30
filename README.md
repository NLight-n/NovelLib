# Novel Library (NovelLib)

> **Local-first Android novel library and e-reader for personal/family use.**

NovelLib allows users to discover web novels in any standard mobile browser, share novel URLs to the app via Android's native `ACTION_SEND` intent, and read, track, download, and synchronize novels across trusted devices over the local network—without requiring any external account or centralized server.

---

## Repository Structure

This repository is organized as a monorepo containing both the Android mobile application and the remotely updateable declarative source definition registry:

```text
.
├── NovelLib_android_app_spec.yaml   # Complete application architecture specification
├── android/                         # Android application (Kotlin, Compose, Room, WorkManager)
│   ├── app/                         # Main application module
│   ├── gradle/                      # Gradle wrapper configuration
│   ├── build.gradle.kts             # Root Gradle build script
│   └── settings.gradle.kts          # Project settings
└── sources/                         # Declarative Web Novel Scraper Registry
    ├── registry.json                # Master source registry index (with SHA-256 checksums)
    └── definitions/                 # Declarative website scrapers (JSON format)
        └── royalroad.json           # Royal Road fiction scraper
```

---

## Key Features

- **Local-First & Serverless:** All reading progress, novel metadata, and downloaded chapters are stored locally on the device in Room and app-private storage.
- **Multi-Profile Isolation:** Multiple family members can use the same device with separate libraries and reading progress. Profiles optionally support local **Argon2id** password protection.
- **E-Ink Hardware Optimized (Bigme & Onyx Boox):**
  - Discrete tap-to-page navigation with zero scroll animation / ghosting.
  - EPDC hardware screen refresh controller and electrophoretic clear-flash overlay.
  - Physical button support (Volume Up/Down, Page Up/Down, DPAD).
  - High-contrast pure monochrome theme tokens and battery diagnostics.
- **Declarative Web Scrapers:** Website extraction rules are purely declarative JSON (CSS selectors, regex, HTML sanitization). No downloaded code is executed.
- **Storage Policies & Download Manager:** Online, Offline, and Hybrid caching (`latest_n_chapters`, `latest_n_unread_chapters`, `all_unread_chapters`) with retention-based cache eviction.
- **Background Automation:** WorkManager periodic novel update checks, queued background chapter downloads, and cache cleanup with system notifications.
- **Browser Share Integration:** Share any novel URL directly from Chrome, Firefox, or Brave (`ACTION_SEND`) to import fiction into your library.
- **Peer-to-Peer LAN Synchronization:** Serverless mDNS (NSD) discovery, EC secp256r1 mutual pairing with 6-digit Short Authentication String (SAS), and deterministic conflict resolution (Set Union + LWW).

---

## Building and Running Locally

### Prerequisites
- JDK 17 or JDK 21
- Android SDK Platform 34+
- Android Build-Tools 34.0.0+

### Build Commands (from `android/` directory)

```bash
cd android

# Run unit tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## Declarative Source Registry

Novel sources are defined declaratively in `sources/definitions/<source_id>.json`. The app checks `sources/registry.json` for updates.

To host the registry via GitHub:
- **Raw URL:** `https://raw.githubusercontent.com/<username>/NovelLib_app/main/sources/registry.json`
- **GitHub Pages:** Enable GitHub Pages in repository settings pointing to `/sources` or the root branch.
