# FilePipe Help

## Fix common issues

### Fix a rule that isn't working
- Give the rule a name, file types, source folders, and (for **Move** and **Copy**) a destination folder.
- Tap any highlighted folder path on the edit screen and grant access again.
- If the rule uses Download folder or a storage root, switch to **All files access** in Settings.

### Fix "Folders need attention" errors
- Common after changing **Selective access** vs **All files access**, restoring a backup, or revoking a folder grant.
- Open **Edit rule**, tap the highlighted path, and pick the folder again in the folder picker.
- Download folder and storage roots need **All files access**.
- Red highlights mean the rule cannot run correctly until the folder problem is fixed.
- Amber highlights mean a source folder is missing or unavailable. The rule can still be saved, but that source will not be scanned.

### Why does my rule icon have an amber/red ring?
- A ring on a collapsed rule card means one or more folders need attention.
- Amber means only a source folder is missing or unavailable. Turn on **Hide missing source folder warnings** in the rule editor if you do not want that reminder on the Rules tab.
- Red means access was lost, the destination has a problem, or the folder cannot be used in the current access mode.

### Can't add the Download folder?
- In **Selective access**, Android blocks the Download folder and some storage roots.
- Turn on **All files access** in Settings, then add the folder, or pick a subfolder inside Download with the picker.

### Files not moving?
- Confirm the rule is enabled and has a schedule set.
- Check folder access — tap any highlighted path in the rule editor and grant access again or pick a replacement folder.
- Review advanced filters: size, age, or filename pattern may be excluding files you expect to match.
- Check battery restrictions for FilePipe in Android settings.

---

## Getting started

### How do I create a rule?
- Tap **+** on the Rules tab to start a new rule.
- Pick source folders, file types (enter manually or use template), and an operation: **Move**, **Copy**, or **Delete**.
- For **Move** and **Copy**, pick a destination folder and set how to handle duplicate files there.
- **Delete** rules do not use a destination or conflict settings — matched files are removed from their source folders.
- Use advanced filters to match by filename pattern, size range, or file age.
- Save, then tap **Preview** 👁️ to see which files would be affected before committing.
- Add a schedule if you want the rule to run automatically in the background.

### What are templates?
- Templates are pre-built rule starting points for common use cases: screenshots, videos, downloads, documents, and more.
- On any rule's page, tap the "Use template" button and choose a template to automatically fill extensions and in some cases, sources.
- Customise any template just like a regular rule.

### What are "All files" and "No extension" file types?
- These are special file types you can add from **Add type** on the rule editor (quick chips in the dialog) or from the **Use template** list. They are NOT the same as **All files access** in Settings.
- **All files** matches every file in your source folders, regardless of extension. Advanced filters still apply — filename pattern, exclude pattern, size, and age can narrow the match.
- Adding **All files** replaces every other file type on the rule. You cannot combine it with specific types such as `jpg` or `pdf`; only the **All files** chip remains.
- **No extension** matches files that have no extension — names with no dot, names that are only a dot and suffix (such as `.gitignore`), or names ending in a trailing dot (such as `README.`).
- Files like `.config.json` or `.env.local` do count as having an extension (`json`, `local`) and are not matched by **No extension** alone.
- **No extension** can be combined with normal types (for example `jpg` and **No extension** together). **All files** cannot be combined with anything else.
- If a filename pattern requires a specific extension (such as `*.pdf`) but your file types do not include it — or you only selected **No extension** — the rule editor shows a warning because no files would match.

### How do I run a rule manually?
- Tap a rule on the Rules tab and use the **Run** action.
- Use **Preview** first if you want to see which files match before the rule acts on them.
- **Delete** rules (and batches that include one) show a confirmation dialog before any files are removed. (Only when run manually.)

---

## Storage access modes

### All files access

#### Best for most people

**Use this to:**
- Organize your Download folder automatically
- Work across your entire storage
- Run rules without extra setup

**Good to know**
- No repeated permission prompts for every folder
- FilePipe only acts on the rules you create
- Fastest option — scans and moves run quicker, especially for large libraries and big files

### Selective access

#### Best for individual folder control

**Use this if you:**
- Only want to organize a few folders
- Prefer granting access one folder at a time

**Limitations**
- Some folders (like Download) can't be selected
- You'll need to approve each folder manually
- Can run slower than All files access on large libraries or big files
- Copied files lose their original timestamp, and instead use the current time.

---

## Managing rules

### What does Delete do?
- **Delete** permanently removes matched files from your source folders. There is no destination folder and no undo anywhere in the app.
- The rule editor shows a warning when **Delete** is selected. Use **Preview** first and narrow your filters carefully before you run or schedule the rule.
- **Conflict setting** does not apply to **Delete** — it is only used for **Move** and **Copy**.
- When you tap **Run** on a delete rule (or run a batch that includes one), FilePipe asks you to confirm before anything is deleted. (Only when run manually.)
- Scheduled delete rules run automatically in the background without that extra confirmation — double-check filters and schedules before enabling them.

### Why is my rule skipping files?
- The file may not match the extensions or advanced filters.
- For **Move** and **Copy**, **Conflict setting** controls what happens when a file with the same name already exists in the destination: **Skip** leaves it untouched, **Overwrite** replaces it, **Rename** keeps both by adding a suffix to the incoming filename.
- Lost folder access can hide files until you grant access again.

### What does preview do?
- Preview lists files the rule would act on.
- Nothing changes on disk until you run the rule for real.
- Use it to check filters, destination (for **Move** and **Copy**), and access before you rely on the rule.

### Can rules scan subfolders?
- Yes — enable **Include subfolders** on a source folder in the rule editor.
- For **Move** and **Copy**, the destination can optionally recreate the source subfolder structure so files land in matching subdirectories.

### How do RegEx filters work?
- Tap the **RegEx** chip inside the **Filename pattern** or **Exclude pattern** text box to switch that field to regular expression mode.
- In wildcard mode (default), use `*` to match any text (e.g. `Screenshot_*`) or separate multiple patterns with commas.
- In RegEx mode, enter regular expression syntax (e.g. `^IMG_\d{4}\.(jpg|png)$` or `\.nomedia|.*\.tmp`). RegEx matching is case-insensitive.
- If a regular expression has invalid syntax (such as an unclosed bracket), an error message will display and the rule cannot be saved until corrected.

---

## History & undo

### What is in History?
- History records every rule run: which files matched, what happened to each, and whether it succeeded.
- Runs are grouped by date and rule. Tap a run to see per-file details, including how many files were moved, copied, or deleted.
- Open the History tab to review past runs and access undo for **Move** and **Copy** runs.

### How do I undo a rule run?
- **Move** and **Copy** runs can be undone. Tap **Undo** in the run notification, or open the **History** tab, tap a completed run, and use the undo option there.
- **Delete** runs cannot be undone. Deleted files are gone permanently — there is no undo button in History or in notifications for delete operations.

---

## Automation & scheduling

### How do scheduled rules work?
- Scheduled rules run in the background at the interval you configure: hourly, daily, or weekly.
- They still need valid folder access and files that match the rule.
- Use **Preview** after changes so automation matches what you expect.
- Scheduled **Delete** rules run without an extra confirmation prompt — treat them like any other scheduled rule and verify filters before you enable the schedule.

### Not getting scheduled run notifications?
- Make sure FilePipe has notification permission granted in Android settings.
- Enable run notifications in the FilePipe Settings screen.
- Battery saver and background restrictions can suppress or delay notifications — exclude FilePipe in battery settings if needed.

### Why did my rule not run?
- Confirm the schedule and the rule are both enabled.
- Android battery limits and notification settings can block or delay background work.
- Recheck folder access and that matching files still exist.

---

## Backup & restore

### What gets backed up?
- Backups include rules, run history, and settings.
- Android does not restore folder grants from a backup.
- After restore, revisit rules with highlighted paths and grant again.

### Can I schedule automatic backups?
- Yes — open Settings and enable the auto-export option to back up your rules on a schedule.
- Backups are saved to a folder you choose and can be shared or archived.

### What do I fix after restore?
- For **Selective access**, tap each affected folder and grant again.
- If rules used **All files access**, turn that back on in Android settings.
- Red highlights mean access must be restored or the folder choice must change.
- Amber highlights mean only a source folder is missing or unavailable. Recreate the folder, pick a replacement, or hide that missing-source warning for the rule.

---

## Customization

### Theme and appearance
- Choose **Light**, **Dark**, **OLED black**, or **System** mode.
- **OLED black** uses a true black background — sharper on OLED screens.

### Colors
- **Material You** pulls colors from your wallpaper on Android 12 and above, and updates automatically when the wallpaper changes.
- **Presets** offer hand-tuned color schemes to choose from.
- **Custom color** lets you enter any hex value. You can save multiple custom colors and switch between them.
- **Palette style** controls how Material 3 expands your seed color into a full palette — the same color can feel very different across styles.

### Visual effects
- **Gradient background** blends your primary color into the screen background for depth.
- **Blur bars** apply a frosted-glass effect behind the top and bottom bars.
- Effects can be combined or turned off individually in Settings.
