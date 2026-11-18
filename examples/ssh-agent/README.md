# SSH Agent

An SSH agent that proxies signing requests to the phone over `adb`, so any
`ssh`, `git`, or `ssh-keygen` command can use the keys stored on the device.



## Setup

**Prerequisites**:
- Go 1.25 or newer
- `adb` on your `PATH`, with the phone connected and USB debugging authorized
- The Phone Key app installed, with at least one asymmetric key that has the
  `SIGN` purpose (`ECDSA` P-256/P-384/P-521 or `RSA`)

### 1. Start the phone server

Open the Phone Key app and keep it in the foreground. The HTTP server starts
automatically and listens on `127.0.0.1:8765` by default. The port can be
changed in the app settings.

### 2. Build and run the agent

```bash
cd examples/ssh-agent
go build -o ssh-agent ./cmd/ssh-agent
./ssh-agent
```

Use a different port if you changed it in the app:

```bash
./ssh-agent -port 9000
```

On startup the agent forwards the port with `adb forward tcp:<port> tcp:<port>`
(if it is not already forwarded) and listens on the Unix socket
`/tmp/phone-key-ssh-agent.sock`.

### 3. Point SSH at the agent

In the same shell (or add it to your shell profile):

```bash
export SSH_AUTH_SOCK=/tmp/phone-key-ssh-agent.sock
```

Verify that the phone's keys are visible:

```bash
ssh-add -l
```

### 4. Test end to end

```bash
./test-ssh-list-identities.sh   # list keys
./test-ssh-sign.sh              # sign and verify a message
```

Once exported, `ssh`, `scp`, and `git` use the agent automatically. For example:

```bash
ssh user@example.com
```

## Start automatically on login

### Linux (systemd user service)

Install the binary somewhere on your `PATH`:

```bash
mkdir -p ~/.local/bin
go build -o ~/.local/bin/phone-key-ssh-agent ./cmd/ssh-agent
```

Find where `adb` lives, since systemd user services do not inherit your shell
`PATH`:

```bash
command -v adb
```

Create `~/.config/systemd/user/phone-key-ssh-agent.service` and set
`Environment=PATH=...` to the directory printed above (keep the standard
directories as shown):

```ini
[Unit]
Description=Phone Key SSH agent

[Service]
ExecStart=%h/.local/bin/phone-key-ssh-agent
Restart=on-failure
RestartSec=5
Environment=PATH=%h/Android/Sdk/platform-tools:/usr/local/bin:/usr/bin:/bin

[Install]
WantedBy=default.target
```

If you changed the server port, add `-port <port>` to `ExecStart`.

Enable and start it:

```bash
systemctl --user daemon-reload
systemctl --user enable --now phone-key-ssh-agent
systemctl --user status phone-key-ssh-agent
```

Follow the logs with:

```bash
journalctl --user -u phone-key-ssh-agent -f
```

Finally, export the socket in your shell profile (`~/.bashrc`, `~/.zshrc`,
or equivalent):

```bash
export SSH_AUTH_SOCK=/tmp/phone-key-ssh-agent.sock
```

The agent needs the phone connected and USB debugging authorized at startup;
`Restart=on-failure` retries until `adb` can reach it. If you want the service
to run without an active login session (for example, before your first SSH
connection), enable lingering:

```bash
loginctl enable-linger "$USER"
```

### macOS (launchd)

Create `~/Library/LaunchAgents/com.bartmr.phone-key-ssh-agent.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.bartmr.phone-key-ssh-agent</string>
  <key>ProgramArguments</key>
  <array>
    <string>/Users/you/.local/bin/phone-key-ssh-agent</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
</dict>
</plist>
```

Adjust the path, then load it:

```bash
launchctl load ~/Library/LaunchAgents/com.bartmr.phone-key-ssh-agent.plist
```

Add the `SSH_AUTH_SOCK` export to `~/.zshrc` as described above. `launchd`
does not read your shell `PATH`, so make sure `adb` is available at one of the
standard locations (`/usr/local/bin` or `/opt/homebrew/bin`) or add an
`EnvironmentVariables` entry to the plist.

## Notes

- Only keys with a public key and the `SIGN` purpose are exposed; symmetric
  keys and keys without `SIGN` are skipped.
- Signing may require unlocking the phone or confirming with biometrics,
  depending on how the key was created.
- `Add`, `Remove`, `Lock`, and `Unlock` are no-ops: keys are managed entirely
  on the phone.
