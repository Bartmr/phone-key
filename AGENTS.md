This repository is a monorepo containing all the projects that make Phone Key.

Phone Key is a mobile app that allows you to use your phone as an hardware key (e.g. Yubikeys).

**Why?**: Your computer, specially if you are in tech, runs a lot of sofware from many sources, with thousands of dependencies from many authors. If a single author is compromised, your computer might be silently running malware that came from a supply-chain attack, and this malware can steal your sessions, keys and credentials in your computer. The objective of this app is to move all critical keys and authentication to your mobile phone, since mobile operative systems are inherently more secure, and apps are isolated. There is less chance for an info-stealer in your phone to steal your keys if they are stored in another app's data. Also, hardware keys are expensive, more prone to being lost or break, and keys can't be transfered.

For now, we it will just support storing and using SSH and GPG keys, through Bluetooth.

## Shared conventions

- Files in each project are organized as a hierarchy of features, concerns and UI sections.
    - global, wider or more generic logic is placed higher in the directory tree, while local, narrower or more specific logic is placed deeper in the directory tree.
- Do not create generic directories like `utils`, `helpers`, etc.
- there should be the least amount of moving parts (state, variables, asynchronous logic, effects, etc.) to achieve something.
- do not create unnecessary functions and variables. if code is not reused, just inline it.
- Avoid try/catch. Let the error bubble up, hit the global loggers and crash the thread.

## `./cli`

### Tech Stack

Tech Stack:
- @abandonware/noble

### Directories

- `scripts` has quick scripts for debugging that will not be included in the final release

## `./mobile-app`

### Tech Stack

- Expo
- React Native
- React Native Paper
- Expo Router
- react-native-ble-plx

### Directories

- `src/ui` is for generic UI logic and components
- `src/app` defines the routes in the app based on the directory and files tree, using Expo Router
- `src/app-impl` contains the routes implementations, and their components
