# Phone Key

An app that turns your phone into a portable hardware-backed key store, similar to a YubiKey or Apple's Secure Enclave.

> [!TIP]
> Curious about my AI coding setup? Reach out to me on [Linkedin](https://www.linkedin.com/in/bartmr/)

## Install

**Prerequisites**:
- Android SDK
- Android device with debugging enabled and authorized (`Settings / Developer Options`)

```bash
cd android-app
./gradlew installProdReleaseLocal
```

## API

[API](./android-app/docs/api.md)

## Examples and utilities

- [SSH Agent](./examples/ssh-agent/README.md)
