# Desktop package builds

Build Linux packages on the matching distro VM when testing manually:

```bash
cd ~/Reader
./gradlew :desktopApp:packageDeb -x test
./gradlew :desktopApp:packageRpm -x test
./gradlew :desktopApp:packageAur -x test
./gradlew :desktopApp:packageFlatpak -x test
```

Build a Windows MSIX locally on Windows with the Windows SDK installed:

```powershell
cd C:\Users\aryan\Desktop\Reader
.\gradlew.bat -PdesktopOnly=true -PdesktopAllowUnconfiguredStandardServices=true :desktopApp:packageReleaseMsix -x test
```

Copy the newest generated MSIX to your desktop:

```powershell
$msix = Get-ChildItem .\desktopApp\build\compose\binaries\main-release\msix -Filter *.msix | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Copy-Item -Force $msix.FullName "$env:USERPROFILE\Desktop\"
```

The MSIX task is separate from MSI packaging. It stages the release app image at
`desktopApp/build/msix/package`, packages it with Windows SDK `makeappx.exe`, and
writes the MSIX to:

```text
desktopApp/build/compose/binaries/main-release/msix
```

For Microsoft Store submission, set the package identity values from Partner
Center so `AppxManifest.xml` matches the reserved app identity:

```powershell
.\gradlew.bat `
  -PdesktopOnly=true `
  -PdesktopMsixIdentityName=<Partner Center package identity name> `
  -PdesktopMsixPublisher=<Partner Center publisher CN> `
  -PdesktopMsixPublisherDisplayName=<Publisher display name> `
  :desktopApp:packageReleaseMsix -x test
```

If Windows SDK tools are not on `PATH`, pass them explicitly:

```powershell
.\gradlew.bat `
  -PdesktopMakeAppxPath="C:\Program Files (x86)\Windows Kits\10\bin\<sdk-version>\x64\makeappx.exe" `
  :desktopApp:packageReleaseMsix -x test
```

Local signing is optional and separate:

```powershell
.\gradlew.bat `
  -PdesktopMsixCertificatePath=C:\path\to\certificate.pfx `
  -PdesktopMsixCertificatePassword=<password> `
  :desktopApp:signReleaseMsix -x test
```

Recommended VM split:

- Ubuntu: `./gradlew :desktopApp:packageDeb -x test`
- Ubuntu with Flatpak tooling: `./gradlew :desktopApp:packageFlatpak -x test`
- Fedora: `./gradlew :desktopApp:packageRpm -x test`
- Arch: `./gradlew :desktopApp:packageAur -x test`

Desktop-only Gradle invocations automatically skip the Android app module and the
Android target in `:shared`, so desktop packaging does not require `sdk.dir`,
Android SDK installation, or Android release signing values. You can force that
mode for unusual command shapes with:

```bash
./gradlew -PdesktopOnly=true :desktopApp:packageDeb -x test
```

Desktop release values are centralized in `gradle.properties`:

```properties
desktopVersion=1.0.1
desktopPackageVersion=1.0.1
desktopAurPackageRelease=1
```

The AUR path is native Arch packaging. It does not wrap the `.deb` or `.rpm`.
`packageAur` first creates a Linux app tarball, then generates an AUR worktree at:

```text
desktopApp/build/aur/episteme-bin
```

On Arch, install/test the generated package with:

```bash
sudo pacman -U ~/Reader/desktopApp/build/aur/episteme-bin/*.pkg.tar.zst
episteme
```

For the OSS/offline flavor:

```bash
./gradlew :desktopApp:packageAur -PdesktopFlavor=oss -x test
sudo pacman -U ~/Reader/desktopApp/build/aur/episteme-oss-bin/*.pkg.tar.zst
episteme-oss
```

To inspect the AUR recipe manually instead:

```bash
./gradlew :desktopApp:prepareAurPackage -x test
cd ~/Reader/desktopApp/build/aur/episteme-bin
makepkg -si
```

For publish-ready AUR metadata, pass the release tarball URL:

```bash
./gradlew :desktopApp:prepareAurPackage \
  -PdesktopAurSourceUrl=https://example.com/releases/episteme-1.0.1-linux-x64.tar.gz \
  -x test
```

Then publish the generated `PKGBUILD` and `.SRCINFO` from the AUR directory.

The generated AUR recipes use `license=('AGPL-3.0-only')` and install the root
`LICENSE` file into `/usr/share/licenses/$pkgname/`.


## Flatpak packaging

Flatpak packaging is generated from the same Compose Desktop distributable used
by the Linux tarball and AUR paths. The task creates a Flatpak manifest,
MetaInfo file, desktop entry, icon export, license export, local repository, and
single-file `.flatpak` bundle.

Install the required tools and runtime in an Ubuntu VM:

```bash
sudo apt update
sudo apt install -y flatpak flatpak-builder
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install -y flathub org.freedesktop.Platform//25.08 org.freedesktop.Sdk//25.08
```

Build the standard Flatpak bundle:

```bash
cd ~/Reader
./gradlew -PdesktopOnly=true :desktopApp:packageFlatpak -x test
```

For a local package without production cloud config, either build the OSS flavor
or allow unconfigured standard services explicitly:

```bash
./gradlew -PdesktopOnly=true -PdesktopFlavor=oss :desktopApp:packageFlatpak -x test
./gradlew -PdesktopOnly=true -PdesktopAllowUnconfiguredStandardServices=true :desktopApp:packageFlatpak -x test
```

The bundle is written to:

```text
desktopApp/build/compose/binaries/main/flatpak
```

Install and run it in the Ubuntu VM:

```bash
flatpak install --user -y desktopApp/build/compose/binaries/main/flatpak/episteme-1.0.1-linux-x64.flatpak
flatpak run io.github.Aryan_Raj3112.episteme
```

For the OSS/offline flavor:

```bash
flatpak install --user -y desktopApp/build/compose/binaries/main/flatpak/episteme-oss-1.0.1-linux-x64.flatpak
flatpak run io.github.Aryan_Raj3112.episteme_oss
```

Useful local validation commands after `prepareFlatpakPackage`:

```bash
./gradlew -PdesktopOnly=true :desktopApp:prepareFlatpakPackage -x test
flatpak-builder --show-manifest desktopApp/build/flatpak/io.github.Aryan_Raj3112.episteme/io.github.Aryan_Raj3112.episteme.yml
flatpak-builder-lint manifest desktopApp/build/flatpak/io.github.Aryan_Raj3112.episteme/io.github.Aryan_Raj3112.episteme.yml
```

The generated manifest currently grants `--filesystem=home` because Episteme can
open and sync arbitrary local book folders. That is convenient for a manual VM
bundle, but Flathub reviewers generally prefer portal-backed file access and the
smallest possible static permissions. If Flathub asks for tighter sandboxing,
move desktop file/folder picking to XDG portals and reduce this permission to the
specific XDG locations that remain necessary.

## Flathub overview

Flathub is the main public app store and build service for Flatpak apps. Users
add the Flathub remote once, then install apps with `flatpak install flathub
<app-id>` or through graphical software centers that integrate with Flathub.

To get Episteme onto Flathub:

1. Keep the app ID stable. The generated IDs are `io.github.Aryan_Raj3112.episteme`
   and `io.github.Aryan_Raj3112.episteme_oss`; Flathub requires code-hosting IDs
   for GitHub-hosted projects to use the `io.github.` prefix.
2. Prepare a source-buildable Flathub manifest named after the app ID, for
   example `io.github.Aryan_Raj3112.episteme.yml`. The local bundle manifest in
   `build/flatpak/...` is useful as a starting point, but Flathub normally wants
   sources and dependencies declared in the manifest rather than prebuilt local
   binaries.
3. Make sure the manifest uses the latest Flathub-hosted runtime at submission
   time, installs the license under `/app/share/licenses/$FLATPAK_ID`, exports an
   app-ID-prefixed desktop file and MetaInfo file, and passes
   `flatpak-builder-lint`.
4. Fork `flathub/flathub`, branch from `new-pr`, add the required manifest and
   supporting files at the repository root, then open a pull request against the
   `new-pr` branch titled `Add io.github.Aryan_Raj3112.episteme`.
5. Respond to reviewer feedback. After approval, Flathub creates the app repo
   under the Flathub GitHub organization and future updates happen there instead
   of through the initial submission flow.

## AUR repository setup

Create an account at:

```text
https://aur.archlinux.org/register/
```

Add your public SSH key in the account settings, then confirm SSH works:

```bash
ssh aur@aur.archlinux.org
```

The command should authenticate and print AUR help text. It will not open a
normal shell.

Create the package repos by cloning their not-yet-existing names:

```bash
git clone ssh://aur@aur.archlinux.org/episteme-bin.git
git clone ssh://aur@aur.archlinux.org/episteme-oss-bin.git
```

If a name already exists, inspect it first. If it is abandoned, follow the AUR
orphan/adoption process instead of creating a duplicate package name.

For each release, extract the matching `aur-<package>-<version>.tar.gz` metadata
archive from the GitHub release, copy `PKGBUILD` and `.SRCINFO` into the matching
AUR clone, then commit and push:

```bash
tar -xzf aur-episteme-bin-1.0.1.tar.gz -C episteme-bin
cd episteme-bin
git add PKGBUILD .SRCINFO
git commit -m "Update to 1.0.1"
git push
```

Repeat the same flow for `episteme-oss-bin`.

## CI release workflow

`Desktop release` in GitHub Actions builds desktop artifacts for standard and
OSS flavors:

- Windows MSI
- Ubuntu/Debian DEB
- Fedora RPM
- Linux tarball used by AUR
- Direct Arch `.pkg.tar.zst`
- AUR metadata archives containing `PKGBUILD` and `.SRCINFO`
- Single-file Flatpak bundles
- `SHA256SUMS.txt`

Before running it, publish Pdfium once from a machine that has the ignored
`third_party/pdfium` folders:

```powershell
.\scripts\desktop\publish-pdfium-release.ps1 `
  -Repository Aryan-Raj3112/episteme `
  -Tag pdfium-desktop-v1
```

That release must contain:

```text
pdfium-linux-x64-v8.zip
pdfium-win-x64-v8.zip
```

The desktop release workflow downloads those assets with:

```powershell
.\scripts\desktop\download-pdfium.ps1 -Tag pdfium-desktop-v1
```

Required GitHub Secrets for standard desktop packages:

```text
DESKTOP_FIREBASE_PROJECT_ID
DESKTOP_FIREBASE_WEB_API_KEY
DESKTOP_GOOGLE_OAUTH_CLIENT_ID
DESKTOP_GOOGLE_OAUTH_CLIENT_SECRET
```

`MYAPP_RELEASE_STORE_FILE` is not used by desktop packaging. Android is skipped
for `:desktopApp:*` tasks.

AUR publishing still needs the two AUR repos:

```text
episteme-bin
episteme-oss-bin
```

Upload the generated `PKGBUILD` and `.SRCINFO` from:

```text
aur-episteme-bin-<version>.tar.gz
aur-episteme-oss-bin-<version>.tar.gz
```
