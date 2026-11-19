# Third-party runtime components

Slime references the following licensed upstream Android runtime component as a pinned Git submodule:

- [LeviLauncher-Android](https://github.com/LiteLDev/LeviLauncher-Android), pinned to `d544df8cafab731ba7686dfb88951602f13293ef`. Its `minecraft` Android library module provides Minecraft Bedrock Android Activity/API classes and related Android resources. Upstream license: Apache License 2.0; the upstream license is retained in the submodule.

The LeviLauncher application itself is not built or bundled. Slime consumes only the `minecraft` library module.

## Preloader status

[preloader-android](https://github.com/LiteLDev/preloader-android) was reviewed as a potential native runtime dependency, but its repository currently exposes no LICENSE file and GitHub reports no license metadata. Slime therefore does not include or build that code in this public project. Re-integration requires an explicit license grant or a separately licensed Android hook/preloader implementation.

The Minecraft runtime host, native dependency loader, version-specific signature rules, and working gameplay hooks remain incomplete. A successful compile of the `minecraft` library is not proof that an installed Minecraft version can already launch in-process.
