# Soda (Luna) lyrics for Echo

> **Status: BLOCKED.** The upstream could not be used from the audit environment (see the compatibility matrix). The real request code and offline tests are included; the extension fails soft instead of returning placeholder lyrics.

An independently installable [Echo](https://github.com/brahmkshatriya/echo) lyrics extension.
One provider per repository, built on Echo's official `LyricsClient` API.

| | |
|---|---|
| Extension id | `echo_lyrics_soda` |
| Extension class | `dev.brahmkshatriya.echo.extension.SodaExtension` |
| Echo type | `lyrics` |
| Repository | https://github.com/ram22002/Echo-Lyrics-Soda |
| Licence | GPL-3.0 (see `LICENSE`, `NOTICE`) |

## What it does

Douyin Soda / Luna lyrics.

It returns candidate lyrics for the playing track, then loads the selected candidate as
Echo's `Lyrics.Timed`, `Lyrics.WordByWord` or `Lyrics.Simple`. Playback is never touched:
the extension only supplies timestamps, Echo handles seeking and the player.

## Upstream API

| Method | Endpoint | Parameters | Auth | Audit result |
|---|---|---|---|---|
| GET | https://api.qishui.com/luna/search/track | signed device params, q | device signature | 200 empty body |
| GET | https://beta-luna.douyin.com/luna/h5/seo_track | track_id, device_platform=web | none | not reached |

No endpoint is invented. Every request in `SodaSource.kt` matches the
upstream behaviour recorded in the project audit
(`docs/lyrics/PROVIDER_COMPATIBILITY_MATRIX.md`).

## Install

1. Download the `.eapk` from this repository's Releases.
2. In Echo: **Settings → Extensions → Install** and pick the file.
3. Open a track, open lyrics, and select **Soda (Luna)** as the source.

Echo keeps one entry per lyrics extension, with its own enable switch and its position in
the extension list. That is the provider list; one repository cannot add entries for the
others.

## Build

```bash
./gradlew :ext:test        # unit tests (offline)
./gradlew :ext:shadowJar   # the extension plugin jar
./gradlew :app:assembleDebug   # the .eapk (needs the Android SDK)
```

Version metadata is derived from git: `version_code` is the commit count and `version` is
`v<short hash>`, exactly like the official template.

## Test

* `./gradlew :ext:test` runs parser, matching and codec tests with fixtures only.
* `./gradlew :ext:test -Decho.live=1` additionally runs `LiveApiTest`, which performs a
  real request. It is skipped by default so CI stays deterministic and the upstream is not
  polled on every push.

## Settings

This extension has no settings.

## Limitations

* The Android search endpoint needs a signed device payload and returned an empty body.
* Echo has no field for translations or romanization; when a provider offers them they are merged into the line text behind a setting, or exposed in `Lyrics.extras`.
* Echo stores one lyrics source per track. Automatic fallback between two installed lyrics extensions is decided by Echo, not by this extension.

## Notes

Written in Kotlin for Echo's extension API. The upstream service is not affiliated with
this project; usage is subject to that service's terms and nothing here bypasses
authentication, payment or DRM.
