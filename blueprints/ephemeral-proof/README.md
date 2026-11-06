# Ephemeral proof engine blueprint

**Status:** Prototype in Driverade; not the planned full cryptographic engine.

## Existing source
- `app/src/main/java/com/driverade/service/EphemeralDemandProof.kt`

## Existing behavior
Uses SecureRandom-generated in-memory HMAC-SHA256 key and nonce, signed demand-point fields, five-minute freshness window, and replay rejection within the same instance.

## Security boundary
This proves consistency only to the same in-memory key holder. It does **not** authenticate a remote client, encrypt stored data, provide a persistent audit chain, or resist a fully compromised client. The nonce set is in-memory, and cross-process replay protection is not established.

## Proposed reusable API
`EphemeralSigner.sign(payload)`; `EphemeralVerifier.verifyOnce(proof)`, with explicit clock, key lifecycle, serialization, and replay-store interfaces.

## Acceptance tests
Field tampering; stale/future timestamps; nonce reuse; process restart; canonicalization collisions; memory pressure; interoperability. Obtain a security review before using as a trust boundary.

**Gate:** Do not begin implementation of the full encryption / cryptographic-integrity engine until the repository is confirmed private. Never commit real keys or secrets.
