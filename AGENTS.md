This repository is a monorepo containing all the projects that make Phone Key.

Phone Key is a mobile app that allows you to use your phone's hardware-backed security enclave (e.g. Trusted Execution Environment) just like a hardware key (e.g. YubiKey). It communicates with your computer through Bluetooth.

## Shared conventions

- Group directories, files and code by what they do (feature, UI section, command or responsability), not by what they are technically.
- Do not create generic directories like `utils`, `helpers`, `shared`, etc.
- To keep the logic simple and easy to understand, minimize mutable state and side effects. Prefer pure functions that take inputs and return outputs.
- do not create unnecessary functions, constants and variables. if code is not reused, just inline it.
- Avoid try/catch. Let errors bubble up, hit the global error handlers, and crash the thread. Use return values for expected failure paths - not exceptions. Reserve exceptions only for truly unexpected conditions that the code cannot reasonably recover from.
- do not type values with `any` or use unsafe type casts. Either validate the value at runtime with Zod or a JSON decoder, check the instance type and throw an explicit error, or type it as `unknown`.
- Avoid ternaries inside other ternaries.

## Project `./cli`

### Tech Stack

- Go
- tinyb (Bluetooth LE via BlueZ D-Bus API)

### Important files and directories

- `bluetooth/bluetooth.go` manages the BLE connection to the mobile app.
- `config/config.go` loads the user's config.

## Project `./android-app`

### Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 (dynamic colors on Android 12+)
- Nordic Semiconductor's Android BLE Library
- Biometric API
- Bouncy Castle (SSH key format)

### Important packages

- `com.bartmr.phonekey.ui` is for generic UI components, logic and design tokens.

### Important files and directories

- `docs` documents the API protocol for communicating with the mobile app.