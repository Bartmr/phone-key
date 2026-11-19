# Phone Key

An app that turns your phone into a portable hardware-backed key store.

## Android app (`cd ./android-app`)

The app has two flavors: `prod` (`com.bartmr.phonekey`) and `dev` (`com.bartmr.phonekey.dev`), so both can be installed side by side.

You can reload the development build with `./gradlew installDevDebug`.

Prefer crashing on unknown exceptions/situations over handling them gracefully. Let unexpected exceptions propagate instead of catching them broadly or silently falling back. Only handle known, expected conditions. Do not add `catch (e: Exception)` or `runCatching` that swallows errors.
