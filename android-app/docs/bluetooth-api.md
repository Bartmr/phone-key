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

---

## Commands (Client → Server)

### 1. `list-keys`

Ask the phone for all available keys in the Android KeyStore, with full metadata and public key bytes.

**Request:**

```ts
interface ListKeysRequest {
  type: "list-keys";
}
```

No additional fields.

**Response:**

```ts
{
  /** The Android KeyStore alias for this key. Used in `sign` to identify which key to sign with. */
  alias: string;

  /** Key algorithm: `"EC"`, `"RSA"`, `"AES"`, `"HMAC"` or others. */
  algorithm: string;

  /** Key size in bits (e.g. 256, 2048). */
  keySize: number;

  /**
   * Bitmask of `KeyProperties.PURPOSE_*` values.
   * `2` = SIGN, `1` = VERIFY, `4` = ENCRYPT, `8` = DECRYPT.
   */
  purposes: number;

  /** Configured digest algorithms (e.g. `["SHA-256"]`). */
  digests: string[];

  /** Configured signature padding schemes. */
  signaturePaddings: string[];

  /** Configured encryption padding schemes. */
  encryptionPaddings: string[];

  /** Configured block cipher modes. */
  blockModes: string[];

  /** Whether biometric/device-credential authentication is required to use this key. */
  userAuthenticationRequired: boolean;

  /** How long after authentication the key can be reused without re-authenticating. */
  userAuthenticationValidityDurationSeconds: number;

  /**
   * X.509 SubjectPublicKeyInfo DER bytes, base64-encoded.
   * Only present for asymmetric key pairs (`PrivateKeyEntry`).
   * `null` for symmetric keys (`SecretKeyEntry`).
   */
  publicKeyBase64: string | null;
}

interface ListKeysResponse {
  type: "list-keys";
  keys: KeyEntry[];
}
```

**Details:**

- All keys from the Android KeyStore are returned — symmetric (AES, HMAC) and asymmetric (EC, RSA).
- The client is responsible for filtering keys based on algorithm and purposes.
- An empty array `[]` is returned in `keys` if no keys are enrolled.

**Example:**

```
Client → Phone:  {"type":"list-keys"}
Phone → Client:  {"type":"list-keys","keys":[{"alias":"my-key","algorithm":"EC","keySize":256,...}]}
Phone → Client:  0x02
```

### 2. `sign`

Ask the phone to sign data with a specific asymmetric key. If the key requires user authentication, this triggers biometric authentication (fingerprint / face).

**Request:**

```ts
interface SignRequest {
  type: "sign";

  /** The alias of the key to sign with (from the `list-keys` response). */
  keyAlias: string;

  /** The data to sign, base64-encoded (standard encoding). */
  data: string;

  /**
   * Java `Signature` algorithm string (e.g. `"SHA256withECDSA"`, `"SHA512withRSA"`).
   * If omitted, the phone derives it from the key's first configured digest and algorithm.
   */
  algorithm?: string;
}
```

**Response:**

```ts
/**
 * Successful sign response.
 */
interface SignResponse {
  type: "sign";

  /**
   * Base64-encoded signature bytes from `Signature.sign()`.
   *
   * The raw bytes depend on the algorithm:
   * - **ECDSA**: DER-encoded ASN.1 signature (sequence of two INTEGERs: r and s).
   * - **RSA**: Raw big-endian signature bytes (PKCS#1 v1.5 or PSS, depending on key configuration).
   *
   * The client is responsible for encoding this into the appropriate protocol format
   * (e.g. SSH signature blob with mpint encoding).
   */
  signature: string;
}
```

**Example:**

```
Client → Phone:  {"type":"sign","keyAlias":"my-key","data":"AAAAIGZsNThuNnJm...","algorithm":"SHA256withECDSA"}
                 (phone shows biometric prompt; user authenticates)
Phone → Client:  {"type":"sign","signature":"MEUCIQDfn0jA..."}
Phone → Client:  0x02
```

### 3. `echo`

Simple ping/pong for connectivity testing.

**Request:**

```ts
interface EchoRequest {
  type: "echo";

  /** Arbitrary string to echo back. */
  data: string;
}
```

**Response:**

The `data` string echoed back verbatim as raw UTF-8 bytes. There is no JSON wrapper — it's the raw string bytes.

```ts
type EchoResponse = ArrayBuffer;
```

**Example:**

```
Client → Phone:  {"type":"echo","data":"ping"}
Phone → Client:  ping
Phone → Client:  0x02
```

## Error responses (Server → Client)

All error responses follow this schema:

```ts
interface ErrorResponse {
  type: "error";
  message: string;
}
```

---

## Security properties

- **BLE pairing**: The GATT characteristic requires `ENCRYPTED_MITM` — the connection is encrypted and MITM-protected. Both devices display a pairing code.
- **Hardware-backed keys**: Private keys are generated and stored in the Android KeyStore backed by the Trusted Execution Environment (TEE) or Secure Element. They never leave the secure hardware.
