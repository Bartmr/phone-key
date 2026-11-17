# Local Network Discovery

Requirements this addresses ([index.md](./index.md)):

- discoverable by client
- secured / encrypted traffic
- easy for developers to implement their own clients
- should work on protected / guest wifi
- should handle when the phone's IP changes in the network, or the network itself changes

## Decision

Use **mDNS / DNS-SD for discovery** and **TLS with a pinned fingerprint for trust**.

Discovery only *locates* the phone. It never authenticates it. Authentication happens
during the TLS handshake, so a spoofed discovery record cannot impersonate the device.

## Advertisement

The app runs its server in a **foreground service**; Android will kill background
sockets and multicast listeners otherwise.

| Item | Value |
| --- | --- |
| Service type | `_phonekey._tcp.` |
| Port | the TLS listener port |
| Discovery | `android.net.nsd.NsdManager.registerService` |
| Required state | `MulticastLock` held + `CHANGE_WIFI_MULTICAST_STATE` permission |

`NsdManager` is unreliable without a `MulticastLock` on several OEMs. If it still proves
flaky on target devices, add a UDP-broadcast beacon (`jmDNS`) that carries the same
payload; clients should accept either transport.

### TXT record schema

| Key | Meaning |
| --- | --- |
| `v` | discovery/protocol version, e.g. `1` |
| `id` | stable device UUID |
| `fp` | `sha256` of the transport certificate's SPKI, hex or base64 |
| `name` | human-friendly device name |
| `proto` | wire protocol, e.g. `json` |

Keep records small (well under 1300 bytes) and backward compatible: clients must ignore
unknown keys and refuse versions they do not understand.

## Trust model

- The phone gets a **dedicated, non-exportable transport key in the Android Keystore**.
  Serve TLS with a self-signed certificate derived from it. Keep this key separate from
  signing keys so it can rotate independently.
- The client reads `fp` from discovery, compares it with the fingerprint of the
  certificate actually presented on connect, then persists it (trust on first use).
  Mismatch = abort.
- A spoofed TXT record cannot impersonate the phone because the presented certificate
  will not match `fp`.

### First-time pairing

Discovery is not a secure channel for the *first* fingerprint. For out-of-band trust,
the app displays a **QR code** encoding `id`, `host:port`, and `fp`. Scanning it both
pins the device and covers networks where multicast is blocked (WSL, containers,
guest WiFi, AP isolation).

## Fallbacks

- Manual `host:port` + fingerprint entry (the client config at
  `cli/internal/config/config.go` already has a config file to hold these).
- QR code (above) when mDNS is unavailable.

## Developer ergonomics

- Every ecosystem has a DNS-SD client (Go `hashicorp/mdns` / `grandcat/zeroconf`,
  Python `zeroconf`, Avahi/Bonjour on Linux/macOS), so third-party discovery is ~20 lines.
- Publish the service type constant, TXT schema, and `v` version here so clients can
  negotiate.
- Document the discovery step separately from the wire protocol; the wire protocol
  should be versioned independently.

## Non-goals

- No custom UDP discovery + custom crypto when mDNS + TLS already express it. Adding
  both raises the barrier for client authors without improving security.
- Discovery is never treated as authentication.
