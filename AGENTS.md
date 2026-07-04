This repository is a monorepo containing all the projects that make Phone Key.

Phone Key is a mobile app that allows you to use your phone's hardware-backed security enclave (e.g. Trusted Execution Environment) just like a hardware key (e.g. YubiKey). It communicates with your computer through Bluetooth.

## Shared conventions

- Do not create generic directories like `utils`, `helpers`, etc.
- there should be the least amount of moving parts (state, variables, asynchronous logic, effects, etc.) to achieve something.
- do not create unnecessary functions, constants and variables. if code is not reused, just inline it.
- Avoid try/catch. Let errors bubble up, hit the global error handlers, and crash the thread. Use return values for expected failure paths - not exceptions. Reserve exceptions only for truly unexpected conditions that the code cannot reasonably recover from.
- do not type values with `any` or use unsafe type casts. Either validate the value at runtime with Zod or a JSON decoder, check the instance type and throw an explicit error, or type it as `unknown`.
- Avoid ternaries inside other ternaries.

## Project `./cli`

### Tech Stack

- rust
- bluer

### Files and directories

- `src` is organized by CLI command, not by technical role. Each command gets its own module, and subcommands map to submodules inside it. Don't extract shared helpers into generic modules - keep logic colocated with the command it serves unless it is genuinely reused across commands.
- `src/config.rs` loads the user's config.
- `src/bluetooth.rs` manages the BLE connection to the mobile app.
- `development` has development and debugging scripts.

## Project `./android-app`

### Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 (dynamic colors on Android 12+)
- Nordic Semiconductor's Android BLE Library
- Biometric API
- Bouncy Castle (SSH key format)

### Packages

- `com.bartmr.phonekey` packages are organized by screen/feature, not by technical role. Put files near the screen they belong to - don't extract them into shared layers unless they are genuinely reused across screens.
- `com.bartmr.phonekey.ui` is for generic UI components, logic and design tokens.

### Files and directories

- `docs` documents the API protocol for communicating with the mobile app.