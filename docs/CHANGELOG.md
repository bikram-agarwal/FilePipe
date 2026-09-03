## v3.10.1 UI Scale

### ✨ New Features
- UI scale in Appearance settings (75% to 125%). Make the whole interface - Text, icons, and spacing - smaller or larger.

### 🐛 Bug Fixes
- Several UI fixes for landscape / tablet layout, gesture vs 3-button navigation mode etc. 

---

## v3.9.9 Preserve file timestamp

### 🛠 Improved Features
- Moving files is now much faster, especially for large files and big batches.
- Files now keep their original date and time when moved (SAF & All files mode) or copied (All files mode), so they stay in the right order in your gallery and file manager instead of all showing up as brand new.
- Moved files now show up in your gallery and file manager right away.

---

## v3.9.8 Misc polish

### 🛠 Improved Features
- Haptic feedback is cleaner and preference-gated.
- Backups that use raw folders with All Files Access restore more reliably.
- Sharing diagnostic logs is smoother and less likely to freeze the UI.
- Preview or beta builds are correctly treated as older than the matching stable version when checking for updates.

### 🐛 Bug Fixes
- Failed backup exports no longer leave a half-written backup file behind.
- Swipe actions on rules no longer get stuck after the first swipe.
- Jumping into a Settings section from Help lands on the right place.

---

## v3.9.7 Run history improvements

### 🛠 Improved Features
- Delete only the history entries matching the active status filter without removing unrelated runs.
- History cards now show each rule’s icon, and the operation icon (copy, move, or delete), for easier visual parsing.
- Cancelled runs are now reliably recorded as cancelled, including cancellations during preparation or after processing some files.

### 🐛 Bug Fixes
- Settings sections now expand or collapse correctly.

---

## v3.9.6 Improvements & Fixes

### 🛠 Improved Features
- Partially completed undos are now tracked per file and can be retried without repeating files that were already restored.
- History now loads, filters, sorts, and groups large numbers of runs more efficiently.
- Backup imports use less memory, exports avoid exposing incomplete files, and local or cloud backups can use persistent destination folders.
- Recursive scans now cover up to 32 folder levels while avoiding unnecessary file processing.
- Rules selected together run in order and avoid processing equivalent source folders or files twice.
- File-size filters now accept fractional megabytes, including decimal-comma input.
- Folder permissions that are no longer needed are released automatically.
- Update download actions now use a clearer, consistent layout.

### 🐛 Bug Fixes
- Incomplete copies and moves that fail to remove their source are no longer reported as successful.
- Permanent deletion skips files changed after confirmation and refreshes confirmations that are too old.
- Fixed overlapping, interrupted, and partially failed undo operations.
- Fixed repeated Save taps potentially creating duplicate rules.
- Fixed stale previews and folder checks reappearing after dismissal or replacement.
- Invalid regular expressions, age ranges, equivalent source and destination folders, and unusable folder permissions are now rejected safely.
- Backup restoration no longer leaves rules, history, or settings partially restored after errors.
- Restored backups now correctly preserve schedules, folder access, undo progress, and history outcomes.
- Update checks now handle stalled connections and server errors, use safe APK filenames, and no longer hide updates merely because the installer opened.
- Background maintenance and backup tasks no longer retry indefinitely.
- Runs interrupted by an app or device restart no longer remain stuck as in progress.
- Database migration failures no longer risk silently deleting rules and history.
- Cancelling a manual run no longer clears the selected rules.

---

## v3.9.5 Delete operation, more file types

### ✨ New Features
- **Delete rules.** Alongside Move and Copy, you can now create rules to permanently delete the files it matches.
- **New "All files" and "No extension" file types.** "All files" matches everything in your source folders, with your filename, size, and age filters still applying. "No extension" matches files with no file type, such as `.gitignore` or `README.`.


### 🛠 Improved Features
- Rule cards now show a small icon telling you whether the rule moves, copies, or deletes.
- Runs that don't affect any files are now recorded in History, where you can filter for them with the "No changes" chip and delete them if you'd rather not keep them.

### 🐛 Bug Fixes
- Rule sort orders were broken. They are fixed now. 

---

## v3.9.4 Fonts, AMOLED, RegEx, Dot folders

### ✨ New Features
- Import your own font (`.ttf` or `.otf`) from Settings → Appearance and use it across the whole app.
- Advanced filters now support regular expressions.

### 🛠 Improved Features
- Pure black (OLED) is now a separate toggle under System or Dark, instead of a fourth theme mode.
- dot folders (folders whose names start with ".") are now selectable in all-files-access folder picker.

---

## v3.9.1

### 🛠 Improved Features
- Undo operations now show live progress bar, and continue safely if you leave the History detail screen.
- Tapping Undo on a scheduled-run notification now opens the matching History entry and automatically starts the undo operation, so progress and results remain visible.
- Layouts now adapt more gracefully to narrow screens, landscape orientation, and larger text.

---

## v3.8.9 Filter by orientation

### ✨ New Features
- **Filter by orientation** — image and video rules can now target only **Portrait** or only **Landscape** files, from Advanced filters. It reads real dimensions and honors rotation, so both photos and videos are matched correctly.

### 🛠 Improved Features
- **Faster scanning in Selective access** — folders accessed via the Android folder picker are read far more efficiently, so previews and runs over large libraries are noticeably quicker. (All files access remains the fastest option overall.)
- **Templates keep your settings** — applying a template no longer resets your Move/Copy choice or subfolder options; it only fills in file types, name, icon, and sources.
- **Clearer run history** — each moved or copied file now shows its real source and destination folder, including subfolders, instead of collapsing everything to the rule's root folder.
- **Slimmer navigation rail** — refreshed side navigation on tablets and in landscape.

### 🐛 Bug Fixes
- **Onboarding layout in landscape** — titles and action buttons no longer clip under the status and navigation bars; the onboarding screens now render edge-to-edge correctly.

---

## v3.8.8 External storage support, auto open changelog

### ✨ New Features
- **Pick folders on SD cards and other drives.** The in-app folder picker now has a "Devices" view that lists every storage volume — internal storage, SD cards, and USB drives — so you can set up rules for files anywhere.

### 🛠 Improved Features
- Tidier layout on tablets and in landscape: removed decorative blurs from few unnecessary screens.
- Fully transparent status and navigation bars for a cleaner edge-to-edge look.
- After the app updates, it now opens the changelog for you automatically.

### 🐛 Bug Fixes
- Fixed a freeze (and occasional "app isn't responding") when tapping + to create a new rule in landscape / two-panel mode.

---

## v3.8.6 Multiple filename patterns

### 🛠 Improved Features
- **Match several filename patterns in one rule.** A rule's advanced filters now accept multiple comma-separated filename (and exclude) patterns — a file matches if any one of them matches.
- **Smoother rotation.** The app no longer restarts its screen when you rotate the device or resize the window, so switching to landscape is seamless and keeps your place.
- **More detailed diagnostics report**, now including display and font-scale information — handy when reporting an issue.

### 📦 Others
- GitHub releases are now build-attested for better supply-chain security and your peace of mind.

---

## v3.8.4 Adaptive layout, richer schedule, alert FAB

### ✨ New Features
- **Alerts button** near the navigation bar replaces the old update button. Tap it to expand update status (available, downloading, ready to install).
- **Richer schedules**: set "every N hours/days/weeks", pick multiple weekdays, and choose a start time.

### 🛠 Improved Features
- Better layouts in **landscape**, on **tablets**, and on **large text/display** devices. The app now adapts dynamically when your device uses extra-large font or display size settings.
- Trashed rules can now be opened to view their details.
- **More consistent confirmation dialogs** throughout the app, with destructive actions clearly marked.

### 🐛 Bug Fixes
- Fixed update alert dismissal and re-show behavior after manual update checks.
- Automatic trash emptying now retries if it hits an error instead of quietly skipping.

### 📦 Others
- GitHub releases are now build-attested using GitHub Actions, for better supply chain security and your peace of mind.

---

## v3.8.0 - Landscape layouts, improved theming, better schedules

### ✨ New Features
- Added a new two-panel and landscape layouts for phones, foldables and large tablets.
- Added a Surface shading slider so you can fine-tune how much color tint appears in app backgrounds and surfaces.
- Added separate custom color controls for primary, secondary, and tertiary theme colors.
- Custom themes can now show and save multi-color accent palettes.

### 🛠 Improved Features
- Rules, History & Settings screens remember sort order and expanded/collapsed state after restart.
- Those preferences are now included in backup and restore.
- Tapping a rule card now opens it for editing. The `Edit` swipe action is replaced with `Expand / Collapse`.
- Video templates now include `.webm`.
- Play Store version now asks for alarm permission, so that scheduled rules can run more reliably.
  - GitHub version automatically gets exact alarm permission. 
- Scheduled rules are restored after reboot, app update, time change, or timezone change.
- Progress notifications are cleared when background rule runs finish.
- App now follows system time format (12 hours / 24 hours)

### 🐛 Bug Fixes
- Fixed a possible Android 15 boot/startup issue related to EmojiCompat initialization.

---

## v3.7.0 - Rule trash, color slider, expressive refresh

### ✨ New Features
- **Rule Trash** - Deleted rules go to Trash first. Restore them from History → Trash within 30 days, or delete forever.
- **Preview multiple rules** - Select several rules and preview matching files for each before batch running.
- **Richer run details** - History run pages show file thumbnails where possible, with open/share actions.
- **Custom theme live preview** - New color slider with live preview to help you choose a custom color for the theme.
- **Cloud backup folder** - Set a second backup destination alongside your local one.
- **Share diagnostic log** - Export a diagnostic log from settings to help troublsehoot any issue.
- **Developer options** - Hidden tools screen for debugging and testing.
- **More from Bikram** - About section now showcases my other apps.

### 🛠 Improved Features
- **Flexible Extension Input** - Add multiple file extensions at once. Separate them with commas, semicolons, or spaces, and enter them with or without a leading dot (e.g. `jpg, png, .mp3`).
- **Subfolder controls** - "Scan subfolders" and "Mirror subfolders to destination" are now separate toggles.
- **Auto-export** - More reliable backup-on-exit; writes to all configured folders and retries after a failed export.
- **Help content** - FAQ restructured to load from updated [in-app help document](./HELP.md).
- **Onboarding** - Permissions screen during onboarding improved with easier layout and a beautiful graphic.
- **Updated look** - Material Symbols icons and more Material 3 Expressive styling across the app.
- **Smarter navigation** - Side rail on tablets and wide screens; bottom bar on phones.
- **Update UI** - Big update banner at top replaced with a small update bar at bottom and a floating Update button.
- **Animations** - Smoother transitions; respects Android reduced-motion setting.

### 🐛 Bug Fixes
- Fixed long-press on a rule's action buttons accidentally entering multi-select.

### 📦 Others
- Requires Android 12+ (minimum SDK raised).
- Targets Android API 37.

---

## v3.1.1 UI consistency

### 🛠 Improved Features
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

### 🛠 Improved Features
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

### 🛠 Improved Features
- **Onboarding permissions screen** — You now choose between `Selective Access` and `All Files Access` with a clear side-by-side explanation. It walks you through granting required access.
- **Folder access indicators** — the rule editor now distinguishes between a folder that's completely inaccessible in your current mode (🚫) and one that's allowed but may have a permission issue (⚠️).
- **Settings deep-links from Help**: tapping a quick action in the FAQ scrolls Settings to the right section and briefly highlights it so you know exactly where to look.

### 🐛 Bug Fixes
- **Undo after copy**: undoing a rule that copied files now correctly reports success for files that were already manually deleted at the destination, instead of counting them as failures.
- **APK update cache**: the downloaded update APK is no longer deleted from the app cache before the copy to your Downloads folder has finished.
- **Backup restore**: run history is now correctly linked to the right rules after a restore, even if rule names aren't unique.

---

## v2.2.0: Smart updates, Import & Restore

### ✨ New features
- **Import and Restore**: Now in addition to `restore` that wipes everything before restoring the backup, you can choose `import`, which adds new rules from the backup and updates matching ones.
- **Smart updates**: Update checks now compare asset timestamps. This ensures you get hotfixes even when the version number hasn't changed.
- **Scheduled update checks**: Added scheduled update checks (At startup/Daily/Weekly) with optional push notifications, so you stay current without manual refreshing.
- **Save apks**: Added support for saving update APKs to Downloads folder.

### 🛠 Improvements
- Clearer alerts about folder issues. Different alerts for missing folder vs lost permission
- New option to suppress missing source folder alerts
- Long explanations of some features are now shown as tooltips, instead of inline subtitles
- Fixed snackbar messages hanging around
- Changelog is now paginated, so you don't get a wall of text

---

## v2.1.0: Custom colors, Custom rule order, Granular history & sorting

### ✨ New features
- **Cancel a running job**: A Cancel button now appears inline on whichever rule is running. Tap it to stop the operation mid-batch; the partial result (including a count of files that were queued but not touched) is saved to history.
- **Custom rule order**: A new "My Order" sort option lets you drag and drop rules into any order you like. Long-press and drag a card to reorder; long-press and hold (without dragging) to enter multi-select.
- **Custom accent colors**: You can now add your own accent color using any hex code, alongside the built-in presets.
- **Fixed card colors**: New toggle in Appearance settings: use neutral grey card surfaces instead of accent-tinted ones.

### 🛠 Improvements
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

### 🛠 Improvements
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
