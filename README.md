# 🎨 Murat Ray Tracer — Android

> *Pure Java, Zero Dependencies, Garbage-Friendly — now on Android*

## 📖 Overview

**Murat Ray Tracer** is a hobbyist, open-source ray tracer originally written in **pure Java** for desktop. This repository is the **Android port** of the same engine, adapted to run on Android API 29–36 with a minimal framework UI.

The desktop Swing GUI was replaced with a simple two-pane layout: a generated image on top, a scene text editor below. The **scene parser is preserved intact**, so the full expressive power of the original `.txt` scene format is available on the phone — procedural materials, CSG, multiple lights, and all.

The core philosophy is unchanged: **pure Java, no AndroidX, no Gradle wrappers, no lambdas, no external libraries**. Everything is compiled from the command line with `javac`, `d8`, `aapt2`, and `apksigner`.

---

## ✨ Features

- **100% Pure Java core** – The renderer, math, materials, lights, and shapes are the same code as the desktop version. Only AWT/Swing/ImageIO touchpoints were adapted.
- **No AndroidX, no Kotlin** – Framework APIs only (`Activity`, `Bitmap`, `Canvas`, `AssetManager`).
- **Garbage-Friendly Design** – Hot paths are zero-allocation; scratch pools and target-parameter matrix/vector methods are preserved.
- **73 Material Types** – Procedural textures (checkerboard, marble, wood, fractal noise), dielectric, glass, metal, and more.
- **12 Light Types** – Ambient, point, directional, spot, area, sphere, tube, projector, bioluminescent, black hole, fractal, pulsating.
- **16 Shape Types** – Plane, Sphere, Box, Disc, Cone, Cylinder, EmojiBillboard, Torus, TorusKnot, Triangle, Mesh, and 3 CSG combiners (Union, Intersection, Difference).
- **Powerful Scene Parser** – The full desktop `.txt` scene format works on Android. Load, edit, and re-render directly on the device.
- **Add / New Scene workflow** – Insert shapes, materials, and lights directly into the editor from built-in templates. Create a fresh scene with one tap.
- **Custom Fonts** – Drop a `.ttf` into `ttf/` and use it in text-based materials.
- **Video Export** – Convert a sequence of rendered images into an MP4 (H.264) via `MediaCodec`.
- **CSG Support** – Union, intersection, and difference (experimental, same as desktop).
- **OBJ Export** – Geometry export still available from the core.

---

## 📱 Android UI

The app is deliberately minimal. The top bar offers four actions, the bottom bar four more:

| Top Bar | Action |
|---------|--------|
| **Exit** | Clean shutdown (`finishAndRemoveTask`) |
| **To Movie** | Convert selected JPGs to an MP4 (choose FPS) |
| **Generate** | Parse the scene and render it (in background) |
| **Save Img** | Save the rendered bitmap to `Download/carets/raytracer/images/` |

| Bottom Bar | Action |
|------------|--------|
| **Add** | Insert a shape / material / light template, or start a new scene |
| **Set** | Temporarily change editor font, size, colors, line spacing |
| **Select Scene** | Pick a scene `.txt` file from storage |
| **Save Scene** | Save the edited scene back to the file |

The screen is split vertically:
- **Top half** – the rendered image (`ImageView`, red border)
- **Bottom half** – the scene text editor (`MEditor`, green border, monospace)

Rendering runs off the UI thread; a translucent overlay with *"Rendering…"* blocks interaction while the frame is being produced.

---

## 📂 Project Structure (Android port)

```
src/
├── net/murat/
│   ├── shape/       # Primitives: Sphere, Box, Plane, Torus, Mesh, CSG, ...
│   ├── material/    # 73 material types + OurFontFactory
│   ├── light/       # 12 light sources
│   ├── math/        # Vector3, Point3, Matrix3/4, Ray, Intersection, AABB
│   ├── lovert/      # Camera, Scene, MuratRayTracer (core renderer)
│   ├── uikit/       # MEditor, MFilePicker, MOptionPane, MColorPicker
│   ├── util/        # Color utilities
│   └── gui/         # Android Activity, SceneParser, GenerateFolders, Utilities
```

Everything outside `uikit/` and `gui/` is shared with the desktop version.

---

## 🚀 Build

No Gradle. No Android Studio. Build from the command line with the included `make.sh`:

```bash
./make.sh
```

The script compiles the Java sources against `android.jar`, converts to DEX with `d8`, packages with `aapt2`, and signs with `apksigner`. The output is `bin/RayTracer.apk`.

Requirements:
- Android SDK with `build-tools` and `platforms/android-36`
- A keystore (`RayTracer.keystore`, not committed for security)
- Java 8+ JDK

---

## 📜 Scene File Format

Unchanged from the desktop version. Scenes are plain text:

```
Camera mainCamera {
    position = P(0.00, 1.00, 5.00);
    lookAt = P(0.00, 0.00, -5.00);
    fov = 60.0;
}

Renderer Settings {
    width = 640;
    height = 480;
    backgroundColor = 0xFF0000FF;
}

# Lighting
MuratAmbientLight muratAmbient {
    name = ambient;
    color = 0xFFFFFFFF;
    intensity = 1.0;
}

# Geometries/Shapes
Sphere sphere {
    radius = 2.0;
    transform = translate(0.000, 0.500, 0.000) * rotate(0.000, 0.000, 0.000) * scale(1.000, 1.000, 1.000);
    material = BrunostCheeseMaterial brunostcheese {
        cheeseColor = 0xFFD2691E;
        caramelColor = 0xFFFFA500;
        caramelAmount = 0.5;
    };
}

-END-
```

The parser is the same class as on the desktop, so any scene file that renders on desktop should parse and render on Android.

On first launch, the app copies the following templates into `Download/carets/raytracer/`:

| File | Location | Purpose |
|------|----------|---------|
| `settings.txt` | `info/` | Renderer limits and editor visual settings |
| `reference.txt` | `info/` | Full scene-format reference |
| `sample.txt` | `scenes/` | Sphere + checkerboard floor example |
| `aaTemplate.txt` | `scenes/` | Empty scene used by "New Scene" |
| `template.txt` | `scenes/` | Quick-start scene (plane + checkerboard + 2 lights) |
| `csgUnionExample.txt` | `scenes/` | Union CSG example |
| `csgDifferenceExample.txt` | `scenes/` | Difference CSG example |
| `csgIntersectionExample.txt` | `scenes/` | Intersection CSG example |

---

## 🛠️ Technologies

- **Pure Java** (source/target 8), compiled with `javac`
- **Android SDK** (API 29 min, API 36 target), `d8`, `aapt2`, `apksigner`
- **No AndroidX, no Gradle, no Kotlin, no lambdas**

AI assistance during development (both desktop and Android port):

| Tool | Role |
|------|------|
| **DeepSeek** | Code generation, optimization suggestions |
| **Google Gemini** | Architecture discussions |
| **Claude** | Algorithm explanations, Android API adaptation |
| **ChatGPT** | Debugging and problem-solving |

---

## ⚠️ Known Issues & Limitations

The same limitations as the desktop version apply to the core engine. Android-specific notes:

| Issue | Description |
|-------|-------------|
| **Render resolution** | Default is 400×300 for speed on mobile CPUs. Raise it in `settings.txt` if you want more detail — rendering will be slower, and very high values may trigger an ANR. |
| **Video resolution** | Capped at 800×600 for performance, even if `settings.txt` allows more. |
| **MEditor focus** | After rendering, the editor may lose focus on some devices. Tap the editor to regain it. |
| **CSG Operations** | Union, intersection, difference work inconsistently with complex geometry (unchanged from desktop). |
| **Mesh Transform** | Mesh shapes cannot be reliably transformed after loading (unchanged). |
| **TorusKnot Mesh** | Degenerate triangles (`f 1 1 2`). Use ray marching instead (unchanged). |
| **Dielectric Material** | Not perfectly rendered; some artifacts may appear (unchanged). |
| **Performance** | Ray marching shapes (Torus, TorusKnot) are significantly slower than analytic shapes — especially on mobile. |
| **Custom Shape/Material/Light** | Not supported on mobile; the parser returns `null` for `Custom` types. |
| **Deprecated shapes** | `Ellipsoid`, `Hyperboloid`, `Paraboloid` classes are still present in `src/` but are not exposed in the UI or the parser. Kept for reference. |

---

## 🖼️ Sample Renders

Sample renders live in the `images/` directory, produced with this engine on Android:

- Turkey flag with reflection and refraction
- CSG union of two spheres with checkerboard floor
- CSG difference with a cloud, rain drops, and "MERHABA" text
- Two spheres with Turkish flag, "MERHABA", and carpet backdrop
- CSG intersection of a sphere and a smaller world

A demo video (`movies/demo.mp4`) shows a sequence of these renders.

---

## 🤝 Contributing

This is a personal hobby project, but contributions are welcome:

1. Fork the repository.
2. Create a branch for your fix/feature.
3. Submit a pull request with a clear description.

Areas that need attention (shared with desktop):
- CSG implementation (accuracy and performance)
- Mesh transformation and persistence
- TorusKnot mesh generation
- Dielectric material refinement

Android-specific areas:
- Background render thread polish (cancellation, progress reporting)
- Better file picker integration for `.txt` scenes
- Landscape layout (currently portrait-only)

---

## 📄 License

**GNU General Public License v3.0.**

You are free to:
- Use, modify, and distribute this software
- Use it for personal or commercial purposes (with proper attribution)

You must:
- Disclose the source code of any derivative works
- License derivatives under the same GPLv3 terms
- Include the original copyright and license notices

See the [LICENSE](LICENSE) file for full details.

---

## 🙏 Acknowledgments

- **Murat iNAN** – Creator and maintainer
- **AI tools** – DeepSeek, Google Gemini, Claude, and ChatGPT for their assistance throughout development (desktop engine and Android port)
- The **open-source community** – For inspiring and enabling projects like this
- All contributors and testers

---

## 📬 Contact

For questions, suggestions, or feedback:
- Open an issue on GitHub
- Contact the maintainer directly

---

*Happy Ray Tracing — now on Android!* 🎨📱
