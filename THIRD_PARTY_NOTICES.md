# Third-party runtime components

Slime uses the following upstream Android runtime components as pinned Git submodules:

- [LeviLauncher-Android](https://github.com/LiteLDev/LeviLauncher-Android), pinned to `d544df8cafab731ba7686dfb88951602f13293ef`. Its `minecraft` Android library module provides the Minecraft Bedrock Android Activity/API classes and related Android resources. Upstream license: Apache License 2.0, retained in the submodule.
- [preloader-android](https://github.com/LiteLDev/preloader-android), pinned to `cc08b40a31471d3e2dedb267f5f17b28da2b0604`. It provides the Android native Preloader library. Upstream license and notices are retained in the submodule.

The LeviLauncher application itself is not built or bundled. Slime consumes the `minecraft` library module and the upstream Preloader source. Native runtime linking remains gated until the installed Minecraft version, ABI, and dependency graph are validated at runtime.
