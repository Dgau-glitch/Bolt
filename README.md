# Bolt
Modern protection solution for individual blocks and entities.

## Getting Started

- [Installing](https://github.com/pop4959/Bolt/wiki#documentation)
- [Migration for LWC users](https://github.com/pop4959/Bolt/wiki#migration-for-lwc-users)
- [Command Reference](https://github.com/pop4959/Bolt/wiki#commands)
- [Configuration](https://github.com/pop4959/Bolt/wiki#configuration)

## Building

Build the runtime plugin jar with:

```bash
./gradlew :bolt-bukkit:shadowJar
```

Use the generated `bukkit/build/libs/Bolt-<version>.jar` on Bukkit, Paper, and Folia servers. The `bolt-folia` Gradle module is a helper module that contains Folia-specific scheduler utilities; it is bundled into the Bukkit shadow jar and is not a separate plugin jar to install.

Running `./gradlew build` is also safe: the root project is an aggregator and delegates assembly to `:bolt-bukkit:shadowJar`, while `verifyPluginJarMetadata` checks that the runtime jar contains `plugin.yml`.

## Support

For questions about Bolt, first try the [Wiki](https://github.com/pop4959/Bolt/wiki) to see if your question is already answered there.

If you can't find what you're looking for, visit us in the LWC channels on our [Discord server](https://discord.gg/ZwVJukcNQG).
