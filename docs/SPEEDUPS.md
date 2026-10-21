# Resource Packs screen speed-ups

fastpacks and packview carry the same speed-ups for vanilla's Resource Packs screen. fastpacks stays its own mod. packview has a built-in copy so it is fast on its own, and that copy turns itself off when fastpacks is installed, so the two never patch the same code twice.

This file is kept identical in both repos. When a speed-up is added or changed in one mod, make the matching change in the other and update this list in both.

| Speed-up | What vanilla does | What the mods do | fastpacks | packview |
|---|---|---|---|---|
| Cached pack key | `ResourcePackRepository.Entry.toString()` rebuilds the pack's name string on every call, and sorting and lookups call it thousands of times | Compute the key once per scan (name, folder or zip, modified time) and return it | `mixin/MixinResourcePackRepositoryEntry`, `PackKey` | `mixin/fast/MixinFastResourcePackRepositoryEntry`, `fast/PackKey` |
| Background icon decode | Every `pack.png` is read and decoded on the render thread when the screen opens | Return a placeholder, decode on worker threads, shrink icons over 128 px, swap the icon in when ready | `mixin/MixinResourcePackRepositoryEntry`, `icon/IconLoader`, `icon/IconImages` | `mixin/fast/MixinFastResourcePackRepositoryEntry`, `fast/icon/IconLoader`, `fast/icon/IconImages` |
| Row culling | Every row of the pack list is drawn each frame, including rows scrolled out of view | Skip rows that are fully outside the list area | `mixin/MixinGuiListExtended`, `ListCulling` | `mixin/fast/MixinFastGuiListExtended`, `fast/ListCulling` |

Switches:

- fastpacks: `-Dfastpacks.baseline=true` turns its speed-ups off, for benchmarks.
- packview: `-Dpackview.baseline=true` turns its built-in copy off. It also stays off whenever fastpacks is installed; the log says which one is active ("Fast loading: ...").

Measured by fastpacks 0.3.0: opening the screen in the real game with OptiFine went from 21,212 ms to 137 ms (155x); startup 3.5x faster.
