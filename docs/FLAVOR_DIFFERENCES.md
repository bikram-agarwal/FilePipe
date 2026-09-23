# FilePipe Flavor Differences

FilePipe is built using four product flavors under the `distribution` dimension: **`github`**, **`fdroid`**, **`playstore`**, and **`offline`**.

| Feature / Characteristic | GitHub Flavor (`github`) | F-Droid Flavor (`fdroid`) | Play Store Flavor (`playstore`) | Offline Flavor (`offline`) |
| :--- | :--- | :--- | :--- | :--- |
| **Application ID** | `dev.bikram.filepipe.gh` | `dev.bikram.filepipe.gh` | `dev.bikram.filepipe` | `dev.bikram.filepipe.offline` |
| **Side-by-Side Installation** | Replaces/blocks F-Droid (shares .gh ID). Can run alongside Play Store & Offline. | Replaces/blocks GitHub (shares .gh ID). Can run alongside Play Store & Offline. | Can run alongside all other flavors. | Can run alongside all other flavors. |
| **Internet permission** | Used for GitHub update checks, APK download, and changelog fetch. | Used for F-Droid package API and changelog fetch. | Used for Play in-app updates and changelog fetch. | No internet permission |
| **Update Check Source** | GitHub Release API for `bikram-agarwal/filepipe` | F-Droid package API for the installed package ID. | Google Play Core In-App Updates. | None. |
| **Update Action** | Downloads the release APK and launches the system package installer. | Opens FilePipe in user's installed FOSS package client for the update. If no FOSS client handles that deep link, the app falls back to the app web page. | Starts the Play Core in-app update flow. | N/A. Install a newer offline APK from GitHub Releases manually. |
| **Manifest Permissions** | Requests `REQUEST_INSTALL_PACKAGES` and `USE_EXACT_ALARM`. | Requests `USE_EXACT_ALARM`. | Requests `SCHEDULE_EXACT_ALARM`. | Requests `USE_EXACT_ALARM`. |
| **Save Update APK to Downloads** | Yes | No. | No. | No. |
| **In-App Rating / Review** | Does not prompt for Play ratings. | Does not prompt for Play ratings. | Uses the Google Play In-App Review API with automated prompt scheduling. | Does not prompt for Play ratings. |
| **Cross-Promo Cards** | Shows Remember and ObtainX cards. Tapping opens each app's webpage. | Shows Remember and ObtainX cards. Tapping first tries `fdroid.app:<target_package_id>`, then falls back to the target app's webpage. | Shows Remember only. Tapping opens the Remember Play Store listing. | Same as GitHub |

---

## Backup Portability FAQ

### Are backups portable between the GitHub, F-Droid, Play Store, and Offline flavors?

**Yes, the backup files (`filepipe_backup_*.json`) are fully portable between all four flavors.**

All flavors share the same data domain representation, Room database schema, and JSON serialization DTOs. Backup import uses `ignoreUnknownKeys = true`, so flavor-specific preference fields can be safely ignored by a flavor that does not use them.

> [!WARNING]
> **Storage Access Permissions (SAF/Document Trees) Do Not Transfer:**
> Android manages Storage Access Framework (SAF) folder permissions granted through `takePersistableUriPermission` at the package-name level.
>
> The GitHub and F-Droid flavors share `dev.bikram.filepipe.gh`, so their SAF grants are tied to the same package ID. The Play Store flavor uses `dev.bikram.filepipe`. The Offline flavor uses `dev.bikram.filepipe.offline`. SAF grants do not transfer across different package IDs.
>
> **How to resolve:** After restoring a backup on a flavor with a different package ID, re-pick affected folders with the system folder picker so Android grants access to the active package.
>
> Switching from GitHub/F-Droid to Offline (or the reverse) requires a backup restore plus re-picking folders.
