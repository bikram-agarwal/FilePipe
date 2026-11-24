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
- Review advanced filters: size, age, include patterns, or exclude patterns may be leaving out files you expect to match. An exclude pattern ending in `/` skips whole subfolders.
- Check battery restrictions for FilePipe in Android settings.

---

## Getting started

### How do I create a rule?
- Tap **+** on the Rules tab to start a new rule.
- Pick source folders, file types (enter manually or use template), and an operation: **Move**, **Copy**, or **Delete**.
- For **Move** and **Copy**, pick a destination folder and set how to handle duplicate files there.
- **Delete** rules do not use a destination or conflict settings — matched files are removed from their source folders.
- Use advanced filters to match by name (include and exclude patterns), size range, or file age.
- Save, then tap **Preview** 👁️ to see which files would be affected before committing.
- Add a schedule if you want the rule to run automatically in the background.

### What are templates?
- Templates are pre-built rule starting points for common use cases: screenshots, videos, downloads, documents, and more.
- On any rule's page, tap the "Use template" button and choose a template to automatically fill extensions and in some cases, sources.
- Customise any template just like a regular rule.

### What are "All files" and "No extension" file types?
- These are special file types you can add from **Add type** on the rule editor (quick chips in the dialog) or from the **Use template** list. They are NOT the same as **All files access** in Settings.
- **All files** matches every file in your source folders, regardless of extension. Advanced filters still apply — include and exclude patterns, size, and age can narrow the match.
- Adding **All files** replaces every other file type on the rule. You cannot combine it with specific types such as `jpg` or `pdf`; only the **All files** chip remains.
- **No extension** matches files that have no extension — names with no dot, names that are only a dot and suffix (such as `.gitignore`), or names ending in a trailing dot (such as `README.`).
- Files like `.config.json` or `.env.local` do count as having an extension (`json`, `local`) and are not matched by **No extension** alone.
- **No extension** can be combined with normal types (for example `jpg` and **No extension** together). **All files** cannot be combined with anything else.
- If every file include pattern requires a specific extension (such as `*.pdf`) but your file types do not include it — or you only selected **No extension** — the rule editor shows a warning because no files would match.

### How do I run a rule manually?
- Tap **Preview** on a rule card. A sheet lists the files the rule would act on.
- Tap the sheet's **Move**, **Copy**, or **Delete** button to run it. The run progess is shown in the sheet, file by file, and you can **Cancel** it there. When it ends, tap **Done**, or **View in History** to see what happened.
- To run several rules at once, long-press a rule card to start selecting, pick the others, then tap **Preview**. If the rules do different things, the button reads **Run**.
- The run acts on exactly the files the sheet listed. A listed file that changed since then is left alone, and a file that appeared since then waits for the next run.
- If you close the sheet while it runs, the run keeps going and its progress shows on the rule card.

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
- When you run it yourself, the preview sheet is the confirmation: it says the files would be **permanently deleted**, and its button turns red (reading **Delete**, or **Run** if the batch mixes in other rules). Only the files it lists are deleted, and a listed file that changed since is kept.
- If the sheet has been open for more than 5 minutes, tapping **Delete** closes it, scans again, and asks you to confirm in a dialog instead.
- Scheduled delete rules run automatically in the background with no confirmation at all. Double-check filters and schedules before turning them on.

### Why is my rule skipping files?
- The file may not match the extensions or advanced filters.
- An exclude pattern may match the file, or a folder the file is inside if the pattern ends in `/` (see **How do include and exclude patterns work?**).
- For **Move** and **Copy**, **Conflict setting** controls what happens when a file with the same name already exists in the destination: **Skip** leaves it untouched, **Overwrite** replaces it, **Rename** keeps both by adding a suffix to the incoming filename.
- Lost folder access can hide files until you grant access again.

### What does preview do?
- Preview lists files the rule would act on.
- Nothing changes on disk until you run the rule for real.
- Use it to check filters, destination (for **Move** and **Copy**), and access before you rely on the rule.

### Can rules scan subfolders?
- Yes. In the rule editor, turn on **Scan subfolders** in the **Source folders** section. This applies to the whole rule. Subfolders are scanned up to 32 levels deep.
- For **Move** and **Copy**, turn on **Mirror subfolders to destination** to recreate the source subfolder structure in the destination, so files land in matching subdirectories.

### How do include and exclude patterns work?
Under **Advanced filters** in the rule editor, two boxes narrow down which files a rule acts on. Put one pattern per line. A file only has to match one line.

**File include patterns**
- Only files whose names match a line are acted on. Leave the box empty to act on every file of the rule's types.
- For example, `Screenshot_*` on one line and `IMG_*` on the next limit the rule to screenshots and camera photos.

**Exclude patterns**
- Files whose names match a line are skipped.
- A line ending in `/` skips a whole folder. FilePipe doesn't look inside it at all.
- A file is acted on only if its name matches an include pattern (when there are any) and no exclude pattern. So a name that matches both boxes is skipped.

**Writing a pattern**
- `*` matches any text and `?` any single character.
- A pattern has to match the whole name: `cache` matches only `cache`, while `*cache*` matches any name containing it.
- Upper and lower case don't matter.
- Commas are ordinary characters, so `Invoice, *.pdf` matches `Invoice, March.pdf`.

**Skipping a folder**
1. In the **Source folders** section, turn on **Scan subfolders**. Folder patterns only work when this is on, because FilePipe does not look inside subfolders otherwise.
2. Under **Advanced filters**, type the folder name followed by `/` on its own line in **Exclude patterns**, for example `cache/`.
3. Tap **Save**, then **Preview** to check which files the rule would act on.

**What a folder pattern matches**
- `cache/` skips every folder named `cache`, at any depth inside each source folder.
- `/cache/` (starting with `/`) skips only a `cache` folder directly inside a source folder.
- `DCIM/cache/` skips only the `cache` folder inside `DCIM`. Paths start at each source folder.
- `thumb*/` skips every folder whose name starts with `thumb`.
- Without the trailing `/`, a pattern applies to file names only: `cache` skips a file named `cache`, not a folder.

To use regular expressions instead, tap **RegEx Off** next to a box's label (it then reads **RegEx On**). See **How do RegEx filters work?**

### How do RegEx filters work?
Each pattern box has a **RegEx Off** button next to its label. Tap it to switch that box to regular expressions; it then reads **RegEx On**. Everything in **How do include and exclude patterns work?** still applies, including one pattern per line. What changes is how a single pattern matches:
- A pattern matches anywhere in the name unless you anchor it: `cache` also matches `app_cache`, while `^cache$` matches only `cache`.
- For example, `^IMG_\d{4}\.(jpg|png)$` matches `IMG_1234.jpg` and `IMG_1234.png`.
- Upper and lower case don't matter.
- In **Exclude patterns**, a trailing `/` still makes a folder pattern, matched against the folder's name. For example, `\.thumbnails/` skips any folder with `.thumbnails` in its name, and `^cache$/` skips only folders named exactly `cache`. A pattern with `/` inside, such as `DCIM/cache/`, is matched against the folder's path from the source folder. A `/` at the start does the same as with wildcards: `/^cache$/` skips only a `cache` folder directly inside a source folder.
- If a line isn't a valid regular expression (such as an unclosed bracket), the rule editor names that line, and the rule can't be saved until you fix it.

**Switching between wildcards and RegEx**
- Switching rewrites the box's patterns so the rule still matches exactly the same files. Turning RegEx on turns `IMG_*` into `^IMG_.*$` and `cache/` into `^cache$/`; turning it off turns them back. Tap **Undo** on the message that appears to put the box back as it was.
- A regular expression that has no wildcard equivalent (such as `\d{4}` or `(jpg|png)`) can't be turned back. FilePipe names those lines and asks before switching. If you switch anyway, they stay as typed, and as wildcards they only match a name spelled exactly like them.

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
- **UI scale** makes the whole interface smaller or larger (75% to 125%). Text, icons, and spacing change together.
- **Blur bars** apply a frosted-glass effect behind the top and bottom bars.
- Effects can be combined or turned off individually in Settings.

---

## Offline flavor

### What's different in the Offline flavor
- The Offline flavor is a separate build of FilePipe with no internet permission at all. It's available from GitHub Releases as `filepipe-v<version>-offline.apk`.
- **No internet permission.** FilePipe can't connect to internet.
- **No update checks.** FilePipe can't look for new versions itself. Use **Track updates via ObtainX** on the Settings page (under Updates on tablets), or install a newer offline APK from GitHub Releases by hand.
- **No changelog after an update.** The other versions show what's new the first time you open them after updating.
- **Installs alongside the other versions** as a separate app, with its own rules, history, and settings. To move your rules over, make a backup in one and restore it in the other, then re-pick your folders so Android grants access to the new app.
