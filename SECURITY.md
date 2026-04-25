# Security Policy

## Scope

Nodex-Android connects directly from your Android device to Linux servers over SSH. It stores SSH credentials (passwords and private keys) on-device using the Android Keystore via AndroidX Security Crypto. No data is transmitted to any service other than the SSH servers you configure yourself.

The following are in scope for security reports:

- SSH authentication handling (password auth, key-pair auth)
- Credential storage and the on-device credential vault
- SSH host key verification and the host key audit trail
- Privilege escalation via sudo (sudo-over-stdin implementation)
- Any unintended data exfiltration or network call outside of SSH

The following are **out of scope**:

- Vulnerabilities in the servers you connect to
- Physical access attacks (device theft, unlocked screen)
- Android OS or manufacturer firmware vulnerabilities
- Third-party library vulnerabilities that have no practical exploit path in this app

## Supported versions

Only the latest release on the `main` branch receives security fixes. There are no long-term-support branches.

## Reporting a vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

Report security issues privately so that a fix can be prepared before public disclosure:

1. Open a [GitHub Security Advisory](https://github.com/bolyki01/nodex-android/security/advisories/new) (draft — visible only to you and maintainers).
2. Include:
   - A clear description of the vulnerability and its impact
   - Steps to reproduce or a proof-of-concept
   - The Android version, device model, and app version you tested on
   - Any suggested mitigations if you have them

You will receive an acknowledgment within 7 days. We aim to release a fix within 30 days of a confirmed vulnerability. We will credit you in the release notes unless you prefer to remain anonymous.

## Cryptographic libraries

This app ships BouncyCastle (bcprov-jdk18on and bcpkix-jdk18on) and SSHJ for SSH transport and cryptographic operations. If you discover a vulnerability in these libraries that affects this app, please report it here as well as to the upstream maintainers.
