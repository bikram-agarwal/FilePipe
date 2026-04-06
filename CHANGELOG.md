## v2.1.1 - Smoothing rough edges

### 🆕 New features
- Added support for saving downloaded update APKs to Downloads folder.

### 🔧 Improvements
- Clearer alerts about folder issues. Different alerts for missing folder vs lost permission
- New option to suppress missing source folder alerts
- Long explanations of some features are now shown as tooltips, instead of inline subtitles
- Fixed snackbar messages hanging around
- Changelog is now paginated, so you don't get a wall of text

---

## v2.1.0 - Custom colors, Custom rule order, Granular history & sorting

### 🆕 New features

- **Cancel a running job** — A Cancel button now appears inline on whichever rule is running. Tap it to stop the operation mid-batch; the partial result (including a count of files that were queued but not touched) is saved to history.
- **Custom rule order** — A new "My Order" sort option lets you drag and drop rules into any order you like. Long-press and drag a card to reorder; long-press and hold (without dragging) to enter multi-select.
- **Custom accent colors** — You can now add your own accent color using any hex code, alongside the built-in presets.
- **Fixed card colors** — New toggle in Appearance settings: use neutral grey card surfaces instead of accent-tinted ones.

### 🔧 Improvements

- **History: Cancelled and Undone filters** — Two new filter chips in the History screen to show only cancelled or undone runs.
- **Maintain subdirectory structure** — When a rule scans subfolders, the destination now mirrors the source folder structure instead of flattening everything into one folder.
- **Sort menus** — Sort and group menus on both the Rules and History screens now show which option is currently selected.
- **Runs survive leaving the app** — Starting a manual run and switching to another app no longer risks the operation being cut short. File Pipe now keeps the run alive in the background until it finishes or you cancel it.
 
### 🐛 Bug fixes

- **Accurate "Last ran" sort** — The "Last ran" sort on the Rules screen now reflects actual run history rather than the rule's last-edited timestamp.
- **Undo copy runs** — Undoing a copy rule now correctly removes the copied files from the destination, instead of "moving them back" to source folders. If the run created new folders at the destination, those are cleaned up too.
- **Folder access refresh** — Returning to the Rules screen after granting folder access in the system picker now immediately clears the amber access-warning banner, without needing to restart the app.
- **Minor UI fixes** — Sort change no longer causes the rules list to scroll unexpectedly. Compact mode run button height now matches the enabled toggle next to it.

---

## v2.0.0 - Onboarding, more themes, advance filters

### ✨ New Features

- **Onboarding** — a short first-launch flow: welcome screen with the title card, then a template picker to get your first rule started effortlessly.
- **Advanced rule filters** — narrow down which files a rule touches with filename patterns, file size/age limits, and exclude patterns. All tucked under an
  expandable "Advanced" section when editing a rule.
- **Hourly schedules** — in addition to daily and weekly, rules can now run every N hours.
- **Simulate** — see exactly which files a rule would move, without actually moving anything.
- **App shortcuts** — long-press the app icon to run any of your top 4 enabled rules directly.
- **Undo from notification** — tap Undo on a scheduled-run notification to reverse the last batch of moves.
- **Custom theme palettes** — go beyond Material You: pick from Tonal Spot, Vibrant, Expressive, Rainbow, Monochrome, and more, or choose one of 9 preset accent colors.
- **Progressive blur** — optional frosted-glass blur effect (Android 12+).
- **Gradient background** — instead of solid color, get subtle gradient behind the app content, toggleable in Settings.
- **In-app updates** — check for updates right from Settings. GitHub installs get APK updates from GitHub Releases; Play Store installs use Google Play's in-app update flow.

### 🔧 Improvements
- **Smarter storage access** — the app no longer requires the broad "manage all files" permission. Folder access is granted individually via the system folder picker and remembered per folder. Cards now show an amber hint if a previously picked folder is no longer accessible.
- **History depth** — history now loads progressively as you scroll (no more loading everything at once). Filter by outcome (including "no changes" runs), group by date or rule.
- **Richer notifications** — scheduled-run notifications show the first few filenames that were  moved, so you know at a glance what happened.
- **Rule icons** — set any emoji as the rule icon alongside the built-in presets.
- **Settings redesign** — cleaner grouped cards, a reworked theme section with live palette preview, and an About section.
- **Swipe action preview** — Settings shows a preview of what each swipe direction will do before you commit.
- **Polished cards** — rule and history cards have smoother animations, tooltips on action buttons, and hover/press elevation feedback.
- **Empty states** — friendly illustrated empty states on the Rules and History screens.

### 🐛 Other Changes

- Various performance improvements to rule execution and history loading.
- Improved handling of edge cases when undoing a run involving nested folders.

---

## v1.0.0 - Launch of **File Pipe**

### ✨ Features

- **Rules** - Create rules with multiple source folders, file extensions, and a destination. Folders scanned recursively.
- **Templates** - Start fast with presets (screenshots, images, video, music, downloads, documents etc.).
- **Move or copy** - Per-rule operation mode, plus conflict handling (skip, overwrite, or rename with a suffix).
- **Run your way** - Run rules on demand, or let **daily / weekly schedules** take care of it.
- **History** - Every run is logged with per-file detail; **undo** a run to put files back when possible.
- **Swipe shortcuts** - Configurable swipe gestures on rule cards (edit, duplicate, delete, history etc.).
- **Backup** - Export rules to JSON, import again, optional auto-export and scheduled export (Settings).
- **Look and feel** - Light, dark, black (OLED), system default, optional **Material You** colors.
