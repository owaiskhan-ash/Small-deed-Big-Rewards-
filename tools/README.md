# Build & asset tooling

Everything in this directory runs on the Python 3 standard library only — no
Pillow, ImageMagick, JDK or network access required.

## Regenerating the launcher icons

All raster assets in the app are **generated**, never hand-edited. The previous
WebP icons were destroyed by a text pipeline before they were committed (see
`../ERROR_AUDIT.md` §1.1); generating them here makes that class of corruption
impossible to reintroduce.

```sh
# Built-in brand mark (eight-pointed star on the emerald gradient):
python3 tools/make_icons.py

# Your own artwork, letterboxed into the adaptive-icon safe zone:
python3 tools/make_icons.py --source /path/to/logo.png

# Tune the palette:
python3 tools/make_icons.py --source logo.png --bg 064E3B --mark FFFFFF
```

Produces, under `app/src/main/res/`:

| File | Sizes (mdpi→xxxhdpi) | Notes |
|---|---|---|
| `mipmap-*/ic_launcher.png` | 48/72/96/144/192 | rounded-square mask for API < 26 |
| `mipmap-*/ic_launcher_round.png` | same | true circular alpha mask |
| `mipmap-*/ic_launcher_foreground.png` | 108/162/216/324/432 | adaptive layer, content in the 66dp safe zone |
| `mipmap-anydpi-v26/ic_launcher{,_round}.xml` | — | adaptive icon (bg gradient + foreground + monochrome) |
| `values/ic_launcher_background.xml` | — | flat fallback colour |
| `drawable/ic_launcher_background.xml` | — | gradient vector used by the adaptive icon |

and, for the Play Console listing, `tools/playstore/play-store-icon.png`
(512×512, required by Play) plus a round variant.

The source artwork must be a PNG; transparency is respected and the mark is
fitted to the safe zone preserving aspect ratio. Commit the regenerated PNGs —
`.gitattributes` marks every image glob `binary` so git can never re-encode
them again.

## PNG codec self-test

`tools/pngtool.py` is a minimal PNG encoder/decoder (8-bit, colour types
0/2/3/4/6, all five scanline filters) plus area-average resampling and alpha
masks. It also **rejects** the mangled assets loudly instead of decoding them
to garbage.

```sh
python3 tools/test_pngtool.py     # exits non-zero on any failure
```

## Gradle wrapper

`gradle/wrapper/gradle-wrapper.properties` pins Gradle **9.3.1** (the minimum
AGP 9.1.1 supports) and `gradlew` / `gradlew.bat` are present, but the wrapper
**jar** is a build artefact that this checkout does not carry. On any machine
with Gradle or Android Studio installed, generate it once:

```sh
gradle wrapper --gradle-version 9.3.1 --distribution-type bin
```

`./gradlew` detects the missing jar and prints exactly this instruction rather
than failing with a confusing JVM classpath error. JDK 17 or newer is required.

## Release signing

`app/build.gradle.kts` signs `release` only when a real upload keystore is
supplied (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, or
`my-upload-key.jks` beside `settings.gradle.kts`). With no keystore present the
release artefact is left **unsigned** on purpose so Play App Signing can sign
it, instead of the build hard-failing on a clean checkout. The `debug` variant
uses AGP's built-in signing and needs nothing committed.
