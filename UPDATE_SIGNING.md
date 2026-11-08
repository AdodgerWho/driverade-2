# Driverade repeatable APK updates

Driverade keeps its application ID (`com.driverade`) unchanged. Android permits an in-place APK update only when the **installed APK and new APK have the same signing certificate** and the new `versionCode` is not lower. This setup uses a permanent personal keystore for deliberately requested builds.

## One-time signing setup

Generate and back up `driverade-updates.jks` outside the repository. The key must remain under the owner's control. The existing key alias is `driverade_updates` if generated from the provided instructions.

In **GitHub repository → Settings → Secrets and variables → Actions → New repository secret**, create:

| Name | Value |
| --- | --- |
| `DRIVERADE_KEYSTORE_BASE64` | One-line base64 representation of the **entire binary JKS file** |
| `DRIVERADE_KEYSTORE_PASSWORD` | JKS store password |
| `DRIVERADE_KEY_ALIAS` | `driverade_updates` (or actual alias of your JKS) |
| `DRIVERADE_KEY_PASSWORD` | Private-key password (may equal the JKS store password) |

Generate base64 locally on Linux:

```bash
base64 -w 0 driverade-updates.jks > driverade-keystore.base64
```

For macOS/BSD, use `base64 < driverade-updates.jks | tr -d '\n' > driverade-keystore.base64`.

**Do not** commit the JKS, encoded copy, or passwords; do not send them in ChatGPT. Store an encrypted, independent offline backup of the keystore and passwords.

## CI vs permanent-signed builds

The workflow `.github/workflows/build.yml` supports two deliberately distinct modes:

1. **Automatic pull-request and main push CI:** run unit tests and assemble a standard debug-signed test artifact named `Driverade-CI-DEBUG-NOT-FOR-UPDATES`. This does **not** use the personal keystore and should not be installed over a permanent-signed version.
2. **Explicit manual update build:** in **Actions → Android CI Build v3.64 → Run workflow**, choose a trusted/reviewed branch and turn on **Sign a reviewed build with the permanent Driverade key**. This reads the four repository secrets only in the signed build step, verifies the keystore and alias, signs the debug APK via Gradle's existing `driveradeUpdates` config, verifies the APK certificate, and uploads `Driverade-PERMANENT-SIGNED-UPDATE`.

If the signing secrets are missing or invalid, a requested permanent-signed build **fails** instead of silently emitting a debug-signed APK. The keystore is reconstructed only into the runner's temporary directory, deleted at the end of the signed-build step, and is never placed in Actions cache or uploaded as an artifact. Do not run unreviewed branch code with signing secrets.

The workflow sets `DRIVERADE_VERSION_CODE = 10000 + GITHUB_RUN_NUMBER`. Check that each subsequent installed build's code increases. The human-readable `versionName` may remain `3.64` while `versionCode` changes.

## Transition and verification

**Important first transition:** your currently installed APK was built with the earlier development signing certificate. A new personal signing certificate cannot update that install in place. Export critical diagnostics, private inspections, and any necessary app settings first. The first personal-key APK will normally require a **final uninstall/reinstall**, which erases local app data because the manifest sets `allowBackup=false`.

Before installing, check the GitHub Actions run completed successfully and inspect the `apksigner verify --print-certs` output in the workflow log. Compare the signing SHA-256 fingerprint across two permanent-signed builds. Download the artifact named `Driverade-PERMANENT-SIGNED-UPDATE`, install version A, then verify version B installs over A with no uninstall.

Never merge unverified parser or device functionality merely because a build passes. Android accessibility permissions still require manual approval and may need re-granting after a reinstall.
