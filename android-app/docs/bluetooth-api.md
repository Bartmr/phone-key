# Phone Key — Bluetooth API Protocol

How the Phone Key mobile app communicates with a computer over Bluetooth Low Energy (BLE).

## Overview

Phone Key turns your phone into a hardware-backed security key (like a YubiKey). Your computer connects to the phone over BLE and sends JSON-encoded commands. The phone processes them using its hardware security enclave (TEE / Secure Element) and responds over the same BLE characteristic.

The phone runs a GATT **server**; the computer is the GATT **client**.

## BLE GATT Service

| Property | Value |
|---|---|
| Service UUID | `a667f940-6a50-49ac-9b75-2b9639564972` |
| Characteristic UUID | `69924d24-8e47-4d43-9e86-dde30201a474` |
| Characteristic properties | READ, WRITE, NOTIFY |
| Characteristic permissions | `ENCRYPTED_MITM` (pairing required) |

The connection requires Bluetooth pairing with MITM protection — both devices will show a pairing dialog.

### Data flow

- **Computer → Phone**: Write to the characteristic (GATT Write).
- **Phone → Computer**: Send a notification (GATT Notify) with the response payload, followed by a second notification containing the single byte `0x02` as a terminator.

```
 Client (computer)                      Server (phone)
        |                                      |
        |--- GATT Write (JSON command) ------->|
        |                                      |  process command
        |<-- GATT Notify (response payload) ---|
        |<-- GATT Notify (0x02 terminator) ----|
        |                                      |
```

## Message format

### Type discriminator

Every message from the client **must** include a `"type"` field. The server uses it to deserialize into the correct message class:

| `type` value | Direction | Description |
|---|---|---|
| `"ssh-request-identities"` | Client → Server | Request the list of available SSH keys |
| `"ssh-sign"` | Client → Server | Request a cryptographic signature |
| `"echo"` | Client → Server | Echo test (connectivity check) |

---

## Commands (Client → Server)

### 1. `ssh-request-identities`

Ask the phone for all available SSH public keys.

**Request:**

```json
{
  "type": "ssh-request-identities"
}
```

No additional fields.

**Response:**

A JSON array of identity objects:

```json
[
  {
    "alias": "my-github-key",
    "publicKeyBase64": "c3NoLWVkMjU1MTkgQUFBQUMzTnphQzFsWkRJMU5URTVBQ..."
  }
]
```

| Field | Type | Description |
|---|---|---|
| `alias` | string | The Android KeyStore alias for this key. Used in `ssh-sign` to identify which key to sign with. |
| `publicKeyBase64` | string | The SSH-format public key (e.g. `ecdsa-sha2-nistp256 AAAA...`), base64-encoded (standard encoding). |

**Details:**

- Only **EC keys** (ECPublicKey) are returned. Keys from other algorithms (e.g. RSA) are silently skipped.
- The phone loads keys from the Android KeyStore and cross-references them with the app's own key metadata repository.
- An empty array `[]` is returned if no keys are enrolled.

### 2. `ssh-sign`

Ask the phone to sign data with a specific key. This triggers biometric authentication (fingerprint / face) because the private key is hardware-backed and requires user verification.

**Request:**

```json
{
  "type": "ssh-sign",
  "keyAlias": "my-github-key",
  "data": "AAAAIGZsNThuNnJmSnBOK1NLTDFoMzhuM3h0QkJpQXNmQ2t3"
}
```

| Field | Type | Description |
|---|---|---|
| `type` | string | Must be `"ssh-sign"`. |
| `keyAlias` | string | The alias of the key to sign with (from the `ssh-request-identities` response). |
| `data` | string | The data to sign, base64-encoded (standard encoding). This is typically the SSH signing payload (session identifier concatenated with data-to-be-signed). |

**Response (success):**

Raw bytes — the ECDSA signature as the concatenation of `r || s` (both as fixed-length big-endian integers). The byte length depends on the curve:

| Curve | Signature length |
|---|---|
| P-256 (`ecdsa-sha2-nistp256`) | 64 bytes (r: 32, s: 32) |
| P-384 (`ecdsa-sha2-nistp384`) | 96 bytes (r: 48, s: 48) |
| P-521 (`ecdsa-sha2-nistp521`) | 132 bytes (r: 66, s: 66) |

The client is responsible for encoding this into the SSH signature blob format (mpint length-prefixed `r` and `s`).

**Response (error):**

An empty byte array (zero-length).

### 3. `echo`

Simple ping/pong for connectivity testing.

**Request:**

```json
{
  "type": "echo",
  "data": "hello"
}
```

**Response:**

The `data` string echoed back verbatim as raw UTF-8 bytes. There is no JSON wrapper — it's the raw string bytes.

---

## Error responses (Server → Client)

### Busy error

The phone processes only **one command at a time**. If a new command arrives while another is still in progress, the phone immediately responds with:

```json
{
  "error": "busy"
}
```

The client should retry after a short delay.

---

## Concurrency model

The phone enforces sequential command processing with an `AtomicReference<CommandState>`:

1. A command arrives and is parsed.
2. If the command requires a state transition (i.e. it's not an `echo`):
   - The phone attempts to CAS (compare-and-swap) the current state from `null` to the new command state.
   - If CAS fails (another command is in progress), it responds with `{"error": "busy"}` and stops processing.
3. When the command completes, the state is reset to `null`.

`echo` commands are exempt from this lock — they always execute regardless of current state.

---

## Full exchange examples

### Listing identities

```
Client → Phone:  {"type":"ssh-request-identities"}
Phone → Client:  [{"alias":"my-key","publicKeyBase64":"c3NoLWVkMjU1MTk..."}]
Phone → Client:  0x02
```

### Signing

```
Client → Phone:  {"type":"ssh-sign","keyAlias":"my-key","data":"AAAAIGZsNThuNnJm..."}
                 (phone shows biometric prompt; user authenticates)
Phone → Client:  <64 raw bytes (P-256 r||s)>
Phone → Client:  0x02
```

### Echo

```
Client → Phone:  {"type":"echo","data":"ping"}
Phone → Client:  ping
Phone → Client:  0x02
```

### Busy rejection

```
Client → Phone:  {"type":"ssh-sign","keyAlias":"my-key","data":"AAAAIGZ..."}
Client → Phone:  {"type":"ssh-request-identities"}     ← sent before sign completes
Phone → Client:  {"error":"busy"}
Phone → Client:  0x02
                 ... later, the sign completes ...
Phone → Client:  <64 raw bytes>
Phone → Client:  0x02
```

---

## Security properties

- **BLE pairing**: The GATT characteristic requires `ENCRYPTED_MITM` — the connection is encrypted and MITM-protected. Both devices display a pairing code.
- **Hardware-backed keys**: Private keys are generated and stored in the Android KeyStore backed by the Trusted Execution Environment (TEE) or Secure Element. They never leave the secure hardware.
- **Biometric binding**: Each signing operation requires the user to authenticate with biometrics (fingerprint or face). The `KeyProperties.AUTH_BIOMETRIC_STRONG` requirement is enforced by the secure hardware, not the app.
- **Single-command locking**: Only one sensitive command (sign or list-identities) may be in flight at a time, preventing interleaving attacks.
