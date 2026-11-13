# RaindanceMC/prime

- **`prime-module`** — Rain's Flexible Modules — Prime: `/prime` shows a player their Prime subscription and what it gives; the store (Tebex) runs `/prime grant <player|uuid> <days>` on every purchase and renewal and `/prime revoke <player|uuid>` on expiry; Prime members ask for an image as map art (`/prime mapart <https-link> <w>x<h>`), staff approve or refuse it (`/prime mapart list|approve|refuse`), the server draws it on maps and hands them over.
- **`prime-standalone`** — Rain's Prime: the module and the standard wrapper, shaded together.

Who is Prime is RainsCore's `social.support.PrimeMembers` (2.92.0): a network document with every paid month, `rains.prime` on whoever is Prime now and the LuckPerms group `prime` to match (rank tag, Discord role through Hush). The perks live where they belong: Cosmetics (`prime.only`: name colours, particles, weapon and armour skins, monthly drops), Ranks (the `[PRIME]` badge in nine colourways, `/rank style`), Guilds (a Prime leader styles the guild from level 1), Economy (`/eco statue place prime`), Essentials (Prime's feature requests are labelled `prime`).

Map art: only https links, only hosts on the public internet (no private, loopback or link-local address, no redirects), at most the configured megabytes and 8192 px a side; the tiles are kept in `plugins/RainsPrime/mapart/<id>/` and drawn on the maps again after every restart.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework).

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `prime-standalone/target/RainsPrime-0.1.0.jar` — needs RainsCore 2.92.0 or newer
