This repository contains Phone Key.

Phone Key is a mobile app that allows you to use your phone as an hardware key (e.g. Yubikeys).

**Why?**: Your computer, specially if you are in tech, runs a lot of sofware from many sources, with thousands of dependencies from many authors. If a single author is compromised, your computer might be silently running malware that came from a supply-chain attack, and this malware can steal your sessions, keys and credentials in your computer. The objective of this app is to move all critical keys and authentication to your mobile phone, since mobile operative systems are inherently more secure, and apps are isolated. There is less chance for an info-stealer in your phone to steal your keys if they are stored in another app's data. Also, hardware keys are expensive, more prone to being lost or break, and keys can't be transfered.

For now, we it will just support storing and using SSH and GPG keys, through Bluetooth.

## Shared conventions

- Code is organized by an hierarchy of features, concerns and UI sections.
- Do not create generic directories like `utils`, `helpers`, etc.
- Use the `tree` command in the terminal to inspect the file structure. Invoke: `tree --gitignore -a -F [path]`
    - `--gitignore`: respect `.gitignore` so ignored files are omitted.
    - `-a`: include hidden files.
    - `-F`: append `/` to directories and `*` to executables for clarity.
    - Optionally add `-L [depth]` to limit depth in large trees.
- do not create unnecessary functions. if code is not reused, just inline it.
- Avoid try/catch. Let the error bubble up, hit the global loggers and crash the thread.