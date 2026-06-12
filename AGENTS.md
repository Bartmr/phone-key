This repository contains Phone Key.

Phone Key is a mobile app that allows you to use your phone as an hardware key (e.g. Yubikeys)

Computers, specially belonging to IT professionals, run a lot of sofware from many sources, with thousands of dependencies from many authors. This means that computers are prone to silently be running malware that came from a supply-chain attack. The objective of this app is to move all critical keys and authentication actions to the mobile phone, since mobile operative systems are inherently more secure, and apps are isolated. There is less chance for an info-stealer to steal your keys if they are stored in the app's data. Also, hardware keys are expensive, more prone to being lost or break, and keys can't be transfered.

## Shared conventions

- Code is organized by an hierarchy of features, concerns and UI sections.
- Do not create generic directories like `utils`, `helpers`, etc.
- Use the `tree` command in the terminal to inspect the file structure. Invoke: `tree --gitignore -a -F [path]`
    - `--gitignore`: respect `.gitignore` so ignored files are omitted.
    - `-a`: include hidden files.
    - `-F`: append `/` to directories and `*` to executables for clarity.
    - Optionally add `-L [depth]` to limit depth in large trees.
