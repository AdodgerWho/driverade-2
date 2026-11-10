# Driverade repeatable APK updates

The first APK signed by the new permanent key requires uninstalling any build signed by a different certificate. **Uninstalling removes local app data** because AndroidManifest sets allowBackup=false. Export any needed logs/settings first.

## One-time signing setup

Generate and safely back up a new JKS keystore **outside GitHub and outside the repository**:

```sh
keytool -genkeypair -v -keystore driverade-updates.jks -alias driverade_updates -keyalg RSA -keysize 3072 -validity 10000
base64 -w 0 driverade-updates.jks
```

Create repository Actions secrets:
- `DRIVERADE_KEYSTORE_BASE64`: base64 of the entire JKS file
- `DRIVERADE_KEYSTORE_PASSWORD`: keystore password
- `DRIVERADE_KEY_ALIAS`: `driverade_updates`
- `DRIVERADE_KEY_PASSWORD`: private-key password

Never commit the JKS or passwords. Back up the JKS and passwords offline: losing them prevents seamless updates to apps signed with this key.

Without the secrets, CI **falls back to standard runner debug signing**, and the APK is **not guaranteed to update** an existing installation. The workflow prints a warning.

## Validation

On every build the workflow verifies the APK, prints package identity/version and signing certificate SHA-256 digest, and uploads `Driverade.apk`. Version codes are `10000 + GitHub Actions run number`; version names are human-readable and may remain unchanged across builds.

Confirm that the certificate digest is the same across successive builds. Install build A and then build B without uninstalling. Do not merge untested feature changes based only on a successful compilation.

The accessibility walkthrough requires the user to grant permission manually. It cannot grant permission on their behalf.
