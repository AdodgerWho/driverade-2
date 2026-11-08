# Driverade: seamless APK updates

## Verified update identity

In-place updates require a matching Android package name and signing certificate,
plus a compatible versionCode that does not decrease. Use increasing codes to avoid
accidentally attempting a downgrade.

The established permanently signed build is **GitHub Actions run #236**:

- Application ID: `com.driverade`
- Installed build versionCode: **10236**
- Signing certificate SHA-256: `a825f5a6602891307f4fefa1b255e795c50e30a9f3e80aefb3d5daf7e5249b16`
- The public SHA-256 above is the certificate fingerprint from the run #236 signing
  verification output; it is not a secret and does not give access to the private key.

### Do not install the automatic CI artifact as an update

PR and ordinary CI runs are **debug signed** with a different certificate.
Their APK filename starts with `Driverade-CI-DEBUG-`; the artifact is named
`Driverade-CI-DEBUG-NOT-FOR-UPDATES`. It cannot replace the permanent-signed
installation, even when its build and unit tests pass.

Only a manually requested **permanent-signed** run with
`sign_for_updates=true` produces an APK with filename
`Driverade-UPDATE-<versionCode>.apk`. The GitHub workflow checks the application
ID, versionCode, and signing certificate against the known good baseline, refusing
the update artifact if its identity is wrong. No new signing key is generated.

## From a GitHub-authenticated terminal

Trigger the review branch's intentional permanent signing:

```bash
gh workflow run build.yml \
  --repo AdodgerWho/driverade-2 \
  --ref experiment/legacy-three-path-parser-pr37-20261107 \
  -f sign_for_updates=true
```

Then open the newest run in **Actions > Android CI Build v3.64** and confirm
`Build APK with permanent signing key` and
`Verify APK identity and prepare distinctly named artifact` both succeeded.

Download the artifact **Driverade-PERMANENT-SIGNED-UPDATE**, **extract the ZIP**
and install the contained `Driverade-UPDATE-<versionCode>.apk` file. Do **not**
try to install the ZIP itself, nor the CI debug APK.

## If Android says 'App not installed'

1. Check that the extracted file begins with `Driverade-UPDATE-`, not
   `Driverade-CI-DEBUG-`. The previous run #254 was automatic debug signing.
2. Check the build's identity-verification step passed and that its versionCode
   is **greater than 10236** (and also greater than any *newer* installed build).
3. Confirm the installed application really was signed with the permanent key.
   A legacy debug-signed app cannot be overwritten by a different certificate.
   Preserving app data across this first identity switch may require app-specific
   backup/restore because Android disallows cross-signer in-place updates.
4. If both signer and code are compatible, capture the specific PackageInstaller
   failure reason before changing signing or deleting app data. Installer
   policies, corrupt files and Android storage restrictions are distinct issues.

Android generally does not display the precise package-install failure in its UI.
With Android Debug Bridge available on a computer, this command gives a better
failure reason:

```bash
adb install -r Driverade-UPDATE-<versionCode>.apk
```

Never paste keystore contents, signing passwords, or GitHub secrets into logs
or support messages.
