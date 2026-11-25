<div align="center">

  <p>
    <b>Vortex — Open-Source Cross-Platform Media Client</b>
    <br />
    Browse catalogs, manage your library, sync Trakt/Simkl/MDBList progress, and play media across screens with Kotlin &amp; Compose Multiplatform.
  </p>

  <p>
    <a href="https://www.watchvortex.me">Website</a> ·
    <a href="https://github.com/Rajat-Solankii/Vortex/actions/workflows/vortex-release.yml">CI Releases</a> ·
    <a href="./LICENSE">GPL-3.0 License</a>
  </p>

</div>

---

## Notice of Modification & Provenance (GNU GPL v3.0)

In accordance with **Sections 4, 5, and 7 of the GNU General Public License v3.0 ([`LICENSE`](./LICENSE))**:

- **Upstream Project:** Vortex is an independent downstream fork of **[Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile)** (`NuvioMedia`), originally licensed under the **GNU General Public License v3.0**. All upstream copyright notices and GPL-3.0 license terms remain preserved in full.
- **Maintainer & Modification Notice:** Modified and maintained by **Rajat-Solanki** (`@Rajat-Solankii`), starting **November 2026**.
- **Summary of Downstream Modifications:**
  - Rebranded package identity (`com.rajatsolankii.vortex` / `com.rajatsolankii.vortex.debug`), package namespace (`com.rajatsolankii.vortex`), app icons, launcher aliases, wordmarks, and UI strings to **Vortex**.
  - Replaced remote account authentication gates with local profile creation and offline-resilient network checks (`NetworkStatusRepository`).
  - Integrated Vortex catalog/API proxy client (configured via `VORTEX_API_HOST`), built-in addon routing, multi-tier stream resolution, and embedded web playback fallback (`VortexEmbedPlayer` / `VortexAdShield`).
  - Added portrait companion player controls, adaptive resize modes, and automated GitHub Actions release packaging & Telegram channel delivery (`.github/workflows/vortex-release.yml`).
- **Trademark & Non-Affiliation Disclaimer:** **Vortex** is an independent, unofficial community fork. It is **not affiliated with, endorsed by, sponsored by, or officially supported by Nuvio, NuvioMedia, or Tapframe**. Upstream names, trademarks, logos, store identifiers (`com.nuvio.app`, `com.nuvio.media`), and official websites (`nuvio.tv`) remain the property of their respective owners and are excluded from endorsement under GPL-3.0 Section 7(e).

---

## Features

- **Kotlin Multiplatform & Compose UI:** Shared business logic and UI targeting Android (and iOS builds).
- **Catalog Discovery & Metadata:** Home rows, Trending, Popular Movies & TV Shows, Anime, genre filters, ratings, trailers, and details powered by external metadata/catalog APIs.
- **Library & Profiles:** Local profile creation and switching, Library, Continue Watching, and tracker sync with **Trakt**, **Simkl**, and **MDBList**.
- **Adaptive Media Player:** Hardware/software playback with portrait companion controls, resize/zoom modes, multi-source fallback, and sandboxed web embed support.
- **Automated CI Releases:** Pushes to `main` build verified signed `fullRelease` APKs via GitHub Actions.

---

## Architecture & Downstream Key Files

| Area | Path |
| --- | --- |
| Android build & `com.rajatsolankii.vortex` flavor config | [`androidApp/build.gradle.kts`](./androidApp/build.gradle.kts) |
| Vortex metadata proxy client | [`composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexApi.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexApi.kt) |
| Built-in catalog & source module | [`composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexBuiltinAddon.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexBuiltinAddon.kt) |
| Server definitions & fallback routing | [`composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexServers.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexServers.kt) |
| Media extraction & embed player shield | [`VortexMediaExtractor.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexMediaExtractor.kt) · [`VortexAdShield.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexAdShield.kt) · [`VortexEmbedPlayer.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/vortex/VortexEmbedPlayer.kt) |
| Portrait companion player UI | [`PlayerPortraitCompanion.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/features/player/PlayerPortraitCompanion.kt) |
| Network reachability probe | [`NetworkStatusRepository.kt`](./composeApp/src/commonMain/kotlin/com/rajatsolankii/vortex/core/network/NetworkStatusRepository.kt) |
| CI release & Telegram publisher | [`.github/workflows/vortex-release.yml`](./.github/workflows/vortex-release.yml) |

---

## Build From Source

```bash
git clone https://github.com/Rajat-Solankii/Vortex.git
cd Vortex
```

### Android

Requires Android Studio / Android SDK and JDK 17+.

```bash
# Debug APK
./gradlew :androidApp:assembleFullDebug

# Release APKs (uses VORTEX_RELEASE_* signing config from local.properties)
./gradlew :androidApp:assembleFullRelease
```
### iOS

Requires macOS and Xcode:

```bash
env VORTEX_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

---

## Legal & Disclaimer

1. **Software License (GPL-3.0):** This project is free software redistributed under the [GNU General Public License v3.0](./LICENSE). Anyone receiving binaries or source builds has the right to obtain, inspect, modify, and redistribute the corresponding source code under the same GPL-3.0 terms.
2. **No Warranty:** Per **GPL-3.0 Sections 15–16**, this software is provided **"AS IS"**, without warranty of any kind, express or implied.
3. **Third-Party Services & Content:** Vortex is a media player and metadata client. This repository does not host, store, or distribute media files. Any external metadata services, trackers, or add-on endpoints queried by users are operated by independent third parties and remain the responsibility of their respective operators and users.

---

## Upstream Attribution

Built upon the open-source codebase of [Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile) by NuvioMedia and its contributors, redistributed under [GNU GPL v3.0](./LICENSE).
