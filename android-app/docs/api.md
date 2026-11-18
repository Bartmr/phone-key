# Phone Key HTTP API

The app runs a small HTTP server that allows you to use the keys stored in the Android
Keystore, without them ever leaving the device.

The server starts automatically when the app is in the foreground and stops when the app is no longer visible.

The API is plain HTTP with no authentication or authorization. Protection comes from the socket being bound to the device's loopback interface: only code on the phone or a host with an `adb forward` for that port can reach it.

## Base URL

```
http://127.0.0.1:<port>
```

- Default port: `8765`
- Configurable in the app settings (allowed range `1024`-`65535`)
- The server binds to `127.0.0.1` only. It is not reachable from the local
  network or the internet. To reach it from a computer, forward the port with
  `adb forward tcp:<port> tcp:<port>`.

## Client example

```bash
# Forward the port (once per connection)
adb forward tcp:8765 tcp:8765

# List keys
curl -sS -X GET http://127.0.0.1:8765/list-keys

# Sign "hello" with a key
curl -sS -X POST http://127.0.0.1:8765/sign \
  -H 'Content-Type: application/json' \
  -d '{"keyAlias":"my-key","data":"aGVsbG8="}'
```

A complete client implementation is available in
[`examples/ssh-agent`](../../examples/ssh-agent/README.md).

## Endpoints

- A signing request may wait for user authentication. Allow at least a couple of minutes.

## `GET /list-keys`

Returns every key present in the app's Android Keystore, including keys that
cannot be used for signing.

### Request

No body is required and any body is ignored.

```bash
curl -sS -X GET http://127.0.0.1:8765/list-keys
```

### Response

`200 OK`

```ts
interface ListKeysResponse {
  /** List of key entries. Can be empty. */
  keys: KeyEntry[];
}

interface KeyEntry {
  /** Keystore alias, as shown in the app. */
  alias: string;

  /** JCA key algorithm reported by Android Keystore, e.g. `EC`, `RSA`, `AES`, `HmacSHA256`. */
  algorithm: string;

  /** Key size in bits (curve order for EC, modulus for RSA). `0` if unknown. */
  keySize: number;

  /**
   * Bitmask of allowed purposes:
   * - `1` - `ENCRYPT`
   * - `2` - `DECRYPT`
   * - `4` - `SIGN`
   * - `8` - `VERIFY`
   * - `32` - `WRAP_KEY`
   *
   * For example, `12` means `SIGN` and `VERIFY`. A key is usable with
   * `/sign` only if it is asymmetric and has the `SIGN` bit set.
   */
  purposes: number;

  /** Allowed digests, e.g. `["SHA-256"]`. Empty if not configured. */
  digests: string[];

  /** Allowed RSA signature paddings: `PKCS1`, `PSS`. */
  signaturePaddings: string[];

  /** Allowed encryption paddings: `OAEPPadding`, `PKCS1Padding`, `NoPadding`, `PKCS7Padding`. */
  encryptionPaddings: string[];

  /** Allowed block modes: `ECB`, `CBC`, `CTR`, `GCM`. */
  blockModes: string[];

  /** Whether signing requires the user to authenticate. */
  userAuthenticationRequired: boolean;

  /** How long an authentication remains valid. `0` means authenticate on every use. */
  userAuthenticationValidityDurationSeconds: number;

  /** DER-encoded X.509 `SubjectPublicKeyInfo`, Base64. `null` for symmetric keys. */
  publicKeyBase64: string | null;
}
```

Keys that cannot be read (for example, an entry with an unsupported type) are
skipped instead of failing the whole request.

---

## `POST /sign`

Signs the supplied bytes with a key stored on the phone. The private key never
leaves the device.

### Request

```ts
interface SignRequest {
  /** Alias of the key to use, from `/list-keys`. */
  keyAlias: string;

  /** Raw bytes to sign, Base64-encoded (standard alphabet). */
  data: string;
}
```

If the key was created with `userAuthenticationRequired`, the phone displays a
biometric/device-credential prompt before signing. The request stays open until
the user authenticates or cancels. If authentication fails or is cancelled,
the endpoint returns `500` with `{"error":"unknown-error"}`.

If `userAuthenticationValidityDurationSeconds` is greater than `0`, a recent
successful authentication may be reused for that many seconds.

### Response

`200 OK`

```ts
interface SignResponse {
  /** Signature bytes, Base64-encoded (standard alphabet). */
  signature: string;
}
```

If another command is in progress, the server will reply with a `409` and the following body:

```json
{
  "error": "busy"
}
```
