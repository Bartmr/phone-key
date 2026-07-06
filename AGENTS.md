This repository is a monorepo containing all the projects that make Phone Key.

Phone Key is a mobile app that allows you to use your phone's hardware-backed security enclave (e.g. Trusted Execution Environment) just like a hardware key (e.g. YubiKey). It communicates with your computer through USB (Android Open Accessory protocol).

## Shared conventions

- Group directories, files and code by what they do (feature, UI section or responsability), not by what they are technically.
- Do not create generic directories like `utils`, `helpers`, `shared`, etc.
- To keep the logic simple and easy to understand, minimize mutable state and side effects. Prefer pure functions that take inputs and return outputs.
- do not create unnecessary functions, constants and variables. if code is not reused, just inline it.
- Avoid try/catch. Let errors bubble up, hit the global error handlers, and crash the thread. Use return values for expected failure paths - not exceptions. Reserve exceptions only for truly unexpected conditions that the code cannot reasonably recover from.
- do not type values with `any` or use unsafe type casts. Either validate the value at runtime with Zod or a JSON decoder, check the instance type and throw an explicit error, or type it as `unknown`.
- Avoid ternaries inside other ternaries.

## Project `./cli-go`

### Tech Stack

- Go
- gousb (libusb binding)
- golang.org/x/crypto/ssh/agent (SSH agent protocol)

### Files and directories

- `src/config.go` loads the user's config from `~/.phone-key.json`.
- `src/usb.go` manages the USB/AOA connection to the mobile app.
- `src/ssh-agent.go` implements the SSH agent protocol, exposed via a Unix socket.
- `src/main.go` entry point.
- `development/test-usb/main.go` standalone USB echo test binary.
- `development/test-ssh-list-identities.sh` tests SSH agent identity listing.
- `development/test-ssh-sign.sh` tests SSH signing through the agent.
- `build.sh` build script.
- `99-phone-key.rules` udev rule for USB device access (copy to `/etc/udev/rules.d/`).

### Building

```sh
./build.sh
```

This produces `build/main` (SSH agent) and `build/test-usb` (USB echo test).

Requires `libusb-1.0-0-dev` installed. Udev rule must be installed once for USB device access.

## Project `./android-app`

### Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 (dynamic colors on Android 12+)
- Android USB Accessory API
- Biometric API
- Bouncy Castle (SSH key format)

### Packages

- `com.bartmr.phonekey.ui` is for generic UI components, logic and design tokens.
- `com.bartmr.phonekey.usb` manages the USB accessory connection.
- `com.bartmr.phonekey.keystore` manages Android Keystore keys.
- `com.bartmr.phonekey.ssh` handles SSH key formatting and signing.

### Files and directories

- `docs` documents the API protocol for communicating with the mobile app.