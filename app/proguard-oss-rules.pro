# F-Droid verifies the OSS APK against a Linux rebuild, while release assets are
# usually produced on a developer machine. R8's generated short names have
# drifted across those environments, which changes classes.dex and the generated
# baseline profile assets. Keep shrinking/optimization enabled for OSS, but make
# the bytecode names stable by disabling obfuscation for this flavor only.
-dontobfuscate
