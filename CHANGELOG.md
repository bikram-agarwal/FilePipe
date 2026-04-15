## v3.1.1 UI consistency

### 🔧 Improved Features

- In Play Store flavor, tapping `check update` in banner won't open double bottom sheets.
- Update and install banner layout made consistent.
- Updated rule error banner layout to be similar to update banner layout.
- Added missed tooltips in edit rule page.
- Settings highlight container now doesn't shrink the section card.
- [Dev build] Added buttons in Settings screen for mocking in-app update banners for testing.

---

## v3.1.0 In-App Updates, Review, Share

### ✨ New Features

- **Update nudge**: When a new version is available, an update banner appears at top of screen so you never miss an update.
- **Seamless updates (Play Store)**: FilePipe can now download and install updates from Google Play without leaving the app. A banner shows download progress and lets you restart to apply the update when it's ready.
- **Rate/Review the app**: You can now rate/review FilePipe directly from Settings.
- **Share FilePipe**: Share a link to FilePipe with friends from the Settings screen.

### 🔧 Improved Features

- **Help always one tap away**: A new  ❔ button in the Settings header takes you straight to the FAQ and help section.
- Adjusted how the app works with **edge-to-edge** on newer Android versions.
- Tweaked colors on onboarding title screen and permissions screen. 

### 🐛 Bug Fixes

- Fixed a bug where a newly duplicated rule wouldn't appear on the list.

---

## v3.0.0: You control access

### ✨ New Features

- **All Files Access mode**: a new access option that lets FilePipe work with any folder, including `Download`, SD card root, and internal storage root, folders that are restricted by Android in "Selective Access" mode. Now you can add folders without extra prompts per folder. Choose between `All Files Access` and `Selective Access` during setup or in Settings at any time.
- **Help & FAQs**: a searchable help/FAQ section covering common questions about rules, storage access, scheduling, privacy, and backup. Accessible from Settings and from rule error banners.

### 🔧 Improved Features

- **Onboarding permissions screen** — You now choose between `Selective Access` and `All Files Access` with a clear side-by-side explanation. It walks you through granting required access.
- **Folder access indicators** — the rule editor now distinguishes between a folder that's completely inaccessible in your current mode (🚫) and one that's allowed but may have a permission issue (⚠️).
- **Settings deep-links from Help**: tapping a quick action in the FAQ scrolls Settings to the right section and briefly highlights it so you know exactly where to look.

### 🐛 Bug Fixes

- **Undo after copy**: undoing a rule that copied files now correctly reports success for files that were already manually deleted at the destination, instead of counting them as failures.
- **APK update cache**: the downloaded update APK is no longer deleted from the app cache before the copy to your Downloads folder has finished.
- **Backup restore**: run history is now correctly linked to the right rules after a restore, even if rule names aren't unique.

---

## v2.2.0: Smart updates, Import & Restore

### 🆕 New features

- **Import and Restore**: Now in addition to `restore` that wipes everything before restoring the backup, you can choose `import`, which adds new rules from the backup and updates matching ones.
- **Smart updates**: Update checks now compare asset timestamps. This ensures you get hotfixes even when the version number hasn't changed.
- **Scheduled update checks**: Added scheduled update checks (At startup/Daily/Weekly) with optional push notifications, so you stay current without manual refreshing.
- **Save apks**: Added support for saving update APKs to Downloads folder.

### 🔧 Improvements

- Clearer alerts about folder issues. Different alerts for missing folder vs lost permission
- New option to suppress missing source folder alerts
- Long explanations of some features are now shown as tooltips, instead of inline subtitles
- Fixed snackbar messages hanging around
- Changelog is now paginated, so you don't get a wall of text

---

## v2.1.0: Custom colors, Custom rule order, Granular history & sorting

### 🆕 New features

- **Cancel a running job**: A Cancel button now appears inline on whichever rule is running. Tap it to stop the operation mid-batch; the partial result (including a count of files that were queued but not touched) is saved to history.
- **Custom rule order**: A new "My Order" sort option lets you drag and drop rules into any order you like. Long-press and drag a card to reorder; long-press and hold (without dragging) to enter multi-select.
- **Custom accent colors**: You can now add your own accent color using any hex code, alongside the built-in presets.
- **Fixed card colors**: New toggle in Appearance settings: use neutral grey card surfaces instead of accent-tinted ones.

### 🔧 Improvements

- **History: Cancelled and Undone filters**: Two new filter chips in the History screen to show only cancelled or undone runs.
- **Maintain subdirectory structure**: When a rule scans subfolders, the destination now mirrors the source folder structure instead of flattening everything into one folder.
- **Sort menus**: Sort and group menus on both the Rules and History screens now show which option is currently selected.
- **Runs survive leaving the app**: Starting a manual run and switching to another app no longer risks the operation being cut short. FilePipe now keeps the run alive in the background until it finishes or you cancel it.
 
### 🐛 Bug fixes

- **Accurate "Last ran" sort**: The "Last ran" sort on the Rules screen now reflects actual run history rather than the rule's last-edited timestamp.
- **Undo copy runs**: Undoing a copy rule now correctly removes the copied files from the destination, instead of "moving them back" to source folders. If the run created new folders at the destination, those are cleaned up too.
- **Folder access refresh**: Returning to the Rules screen after granting folder access in the system picker now immediately clears the amber access-warning banner, without needing to restart the app.
- **Minor UI fixes**: Sort change no longer causes the rules list to scroll unexpectedly. Compact mode run button height now matches the enabled toggle next to it.

---

## v2.0.0: Onboarding, more themes, advance filters

### ✨ New Features

- **Onboarding**: a short first-launch flow: welcome screen with the title card, then a template picker to get your first rule started effortlessly.
- **Advanced rule filters**: narrow down which files a rule touches with filename patterns, file size/age limits, and exclude patterns. All tucked under an
  expandable "Advanced" section when editing a rule.
- **Hourly schedules**: in addition to daily and weekly, rules can now run every N hours.
- **Simulate**: see exactly which files a rule would move, without actually moving anything.
- **App shortcuts**: long-press the app icon to run any of your top 4 enabled rules directly.
- **Undo from notification**: tap Undo on a scheduled-run notification to reverse the last batch of moves.
- **Custom theme palettes**: go beyond Material You: pick from Tonal Spot, Vibrant, Expressive, Rainbow, Monochrome, and more, or choose one of 9 preset accent colors.
- **Progressive blur**: optional frosted-glass blur effect (Android 12+).
- **Gradient background**: instead of solid color, get subtle gradient behind the app content, toggleable in Settings.
- **In-app updates**: check for updates right from Settings. GitHub installs get APK updates from GitHub Releases; Play Store installs use Google Play's in-app update flow.

### 🔧 Improvements
- **Smarter storage access**: the app no longer requires the broad "manage all files" permission. Folder access is granted individually via the system folder picker and remembered per folder. Cards now show an amber hint if a previously picked folder is no longer accessible.
- **History depth**: history now loads progressively as you scroll (no more loading everything at once). Filter by outcome (including "no changes" runs), group by date or rule.
- **Richer notifications**: scheduled-run notifications show the first few filenames that were  moved, so you know at a glance what happened.
- **Rule icons**: set any emoji as the rule icon alongside the built-in presets.
- **Settings redesign**: cleaner grouped cards, a reworked theme section with live palette preview, and an About section.
- **Swipe action preview**: Settings shows a preview of what each swipe direction will do before you commit.
- **Polished cards**: rule and history cards have smoother animations, tooltips on action buttons, and hover/press elevation feedback.
- **Empty states**: friendly illustrated empty states on the Rules and History screens.

### 🐛 Other Changes

- Various performance improvements to rule execution and history loading.
- Improved handling of edge cases when undoing a run involving nested folders.

---

## v1.0.0: Launch of **FilePipe**

### ✨ Features

- **Rules**: Create rules with multiple source folders, file extensions, and a destination. Folders scanned recursively.
- **Templates**: Start fast with presets (screenshots, images, video, music, downloads, documents etc.).
- **Move or copy**: Per-rule operation mode, plus conflict handling (skip, overwrite, or rename with a suffix).
- **Run your way**: Run rules on demand, or let **daily / weekly schedules** take care of it.
- **History**: Every run is logged with per-file detail; **undo** a run to put files back when possible.
- **Swipe shortcuts**: Configurable swipe gestures on rule cards (edit, duplicate, delete, history etc.).
- **Backup**: Export rules to JSON, import again, optional auto-export and scheduled export (Settings).
- **Look and feel**: Light, dark, black (OLED), system default, optional **Material You** colors.
