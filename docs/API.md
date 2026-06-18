# Bolt API integration guide

Bolt exposes its Bukkit/Folia API through Bukkit's `ServicesManager`:

```java
BoltAPI bolt = Bukkit.getServicesManager().load(BoltAPI.class);
if (bolt == null) {
    return;
}
```

## Folia scheduler contract

Methods that accept live Bukkit objects (`Block`, `Entity`, `Player`, `World`) must be called from the scheduler context that owns those objects. Pure data operations on `Protection` objects are safe to perform away from region ticks unless the called method explicitly touches live Bukkit world state.

For integrations running async work, hop back to the owning entity/region scheduler before calling methods such as `findProtection(block)`, `canAccess(block, player, ...)`, or sending messages to a player.

## Optional Bolt messages for integrations

If another plugin performs Bolt access checks itself, it can now opt into the same user-facing messages Bolt uses internally instead of duplicating language keys and placeholders.

```java
Protection protection = bolt.findProtection(block);
if (protection != null && !bolt.canAccess(protection, player, Permission.INTERACT)) {
    bolt.sendAccessDeniedMessage(player, protection, true); // true = action bar
    event.setCancelled(true);
}
```

Available helpers:

- `sendAccessDeniedMessage(sender, protection, actionBar)` sends the standard locked/interact-denied message.
- `sendProtectionNotification(sender, protection, actionBar)` sends the standard protection notification and resolves the owner name when possible.
- `sendProtectionInfo(sender, protection, full)` sends the same information message used by Bolt's info interaction; `full=true` includes access list and timestamps.
- `sendProtectionMessage(sender, protection, translationKey, actionBar)` sends any Bolt translation that uses the common `<protection_type>` and `<protection>` placeholders.
- `displayType(protection, viewer)` returns the same localized display component Bolt inserts into its messages.

## Custom block and entity display names

Custom-block or custom-entity plugins can register display names that replace Bolt's default material/entity translation in all Bolt protection messages.

```java
bolt.registerBlockDisplayName("myplugin:reinforced_chest", Component.text("Reinforced Chest"));
bolt.registerEntityDisplayName("myplugin:display_case", Component.text("Display Case"));
```

Keys are matched case-insensitively. For vanilla objects you can also override Bukkit names such as `CHEST`, `HOPPER`, `ITEM_FRAME`, or `minecraft:chest`. If no custom name is registered, Bolt keeps its existing fallback chain: Bolt language file override, Adventure translatable component, then `unknown` for unresolvable stored entity data.

Unregister overrides during plugin shutdown if they are temporary:

```java
bolt.unregisterBlockDisplayName("myplugin:reinforced_chest");
bolt.unregisterEntityDisplayName("myplugin:display_case");
```
