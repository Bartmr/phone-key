# USB Communication Protocol

Phone Key uses the Android Open Accessory (AOA) protocol for communication between the computer CLI and the Android app.

## Transport

- **Protocol:** Android Open Accessory (AOA) 2.0
- **AOA VID:** `0x18D1`
- **AOA PID:** `0x2D00` (or `0x2D01` with ADB)
- **Bulk IN endpoint:** `0x81`
- **Bulk OUT endpoint:** `0x02`

## Connection Flow

1. CLI sends `ACCESSORY_GET_PROTOCOL` to check AOA support
2. CLI sends `ACCESSORY_SEND_STRING` for manufacturer, model, description, version, URI, serial
3. CLI sends `ACCESSORY_START` to trigger re-enumeration
4. Device re-enumerates in AOA mode (VID `0x18D1`, PID `0x2D00`/`0x2D01`)
5. CLI opens AOA device and claims bulk endpoints
6. Communication begins

## Framing

All messages use length-prefixed framing:

- **Send:** 4 bytes big-endian uint32 (payload length) + payload bytes
- **Receive:** 4 bytes big-endian uint32 (payload length) + payload bytes

Maximum payload size: 65536 bytes.

## Messages

All messages are JSON over the transport. Client (CLI) sends a request, phone responds.

### Request Identities

```
→ {"type":"ssh-request-identities"}
← [{"alias":"my-key","publicKeyBase64":"ZWNkc2Etc2hhMi1uaXN0cDI1NiBBQUFB..."}]
```

The response is a JSON array of objects, each with:
- `alias`: the key's alias in Android Keystore
- `publicKeyBase64`: the OpenSSH-format public key, base64-encoded

### Sign

```
→ {"type":"ssh-sign","keyAlias":"my-key","data":"dGhpcyBpcyBkYXRhIHRvIHNpZ24="}
← <raw signature bytes>
```

- `keyAlias`: the key alias (from the identities response)
- `data`: the data to sign, base64-encoded
- Response: raw ECDSA signature (r || s, 64 bytes for P-256)

If signing fails (user cancels biometric prompt, error), the response is an empty byte array.
