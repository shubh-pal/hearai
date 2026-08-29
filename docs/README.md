# HearAI landing page

The public marketing site (`index.html`) — a single static page built from the
`HearAI Landing.dc.html` Claude Design mock. No framework, no build step: plain HTML/CSS/JS
with transparent 3D product renders in `assets/`.

It lives in `docs/` so **GitHub Pages → Deploy from a branch → `/docs`** serves it with no
workflow. `requirements.md` in this folder is ignored by Pages (only `index.html` is served).

## Preview locally

```bash
cd docs
python3 -m http.server 8782
# open http://localhost:8782
```

## Deploy

**GitHub repo → Settings → Pages → Build and deployment**
- Source: **Deploy from a branch**
- Branch: your default branch, folder **`/docs`**

Live at `https://shubh-pal.github.io/hearai/`. For a custom domain, add a `CNAME` file to this
folder and set it in Pages settings.

## Download links

The buttons in **Give it a listen** point straight at release assets:

```
https://github.com/shubh-pal/hearai/releases/download/v1.0/<asset>
```

| Platform | Asset |
|---|---|
| Android | `HearAI-1.0.apk` |
| Windows | `HearAI-Desktop-Setup-0.1.0.exe` (NSIS, x64) |
| macOS (Apple Silicon) | `HearAI-Desktop-0.1.0-arm64.dmg` |
| macOS (Intel) | `HearAI-Desktop-0.1.0-x64.dmg` |

The tag and filenames live in the `downloads` array (and the `TAG` constant above it) in the
inline `<script>` in `index.html` — bump them when you cut a new release.

## Building the release artifacts

```bash
./scripts/build-release.sh          # needs JDK 17, Android SDK, Node, ./release.keystore
gh release create v1.0 release-artifacts/* --title "HearAI 1.0" --notes-file RELEASE_NOTES.md
```

`build-release.sh` cross-builds the Windows `.exe` from macOS/Linux (electron-builder bundles
its own wine). All builds are unsigned — users get the standard unknown-source / Gatekeeper
prompt. `release.keystore` is git-ignored; keep it (and its password) safe — you need the same
key to ship Android updates.

## Editing content

Copy and lists (features, FAQ, languages, downloads, pipeline steps) live in the `data` object
in the inline `<script>` at the bottom of `index.html`. The typing-caption demo (desktop mock)
reads from the `lines` array just below it.
