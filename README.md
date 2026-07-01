# SegmentDriveApp v0.1.3

Starter Android Studio project for:
- Android 15 only
- CameraX foreground video + audio capture
- 5-minute local MP4 segments
- Google Drive upload of sealed segments
- local Room manifest/state tracking
- WorkManager retry pipeline

## Required manual setup
1. Open in Android Studio.
2. Replace `PUT_FIXED_DRIVE_FOLDER_ID_HERE` in `app/build.gradle.kts`.
3. Register the app in Google Cloud / Google Auth platform for Android.
4. Ensure Drive API is enabled for the project.
5. Run once, tap **Authorize Drive**, then record.

## Important note
This is a **starter scaffold**, not a finished production app.
The upload worker currently uploads each sealed segment in one `PUT` request after the Drive resumable session is created. That keeps the project simpler for V1.


## v0.1.2 stability pass
- fixed finalize/upload race across segment rotation by binding finalize handling to the correct DB row
- added conservative control-state callbacks for recording status
- improved recovery scan messaging and file existence handling
- kept CameraX path unchanged for stability


## GitHub Actions APK build
This project now includes a GitHub Actions workflow at `.github/workflows/build-debug-apk.yml`.

What it does:
- installs JDK 17
- installs Android SDK packages for API 35
- builds `:app:assembleDebug`
- uploads the debug APK as a workflow artifact
- uploads build reports/logs for debugging

Recommended repository setup:
1. Push the project to GitHub.
2. In **Settings -> Secrets and variables -> Actions**, add a repository secret named `DRIVE_FOLDER_ID`.
   - If the secret is missing, CI still compiles using the placeholder folder id.
3. Open the **Actions** tab and run **Build Debug APK**.
4. Download the APK artifact from the workflow run.

Tooling note:
- Root AGP was updated to `8.6.1` because the project uses `compileSdk = 35` / `targetSdk = 35`.
- The workflow uses Gradle `8.7` and JDK `17`.
