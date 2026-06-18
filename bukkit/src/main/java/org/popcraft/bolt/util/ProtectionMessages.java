package org.popcraft.bolt.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.popcraft.bolt.BoltPlugin;
import org.popcraft.bolt.lang.Translation;
import org.popcraft.bolt.protection.Protection;

import java.util.Optional;

import static org.popcraft.bolt.util.BoltComponents.resolveTranslation;

public final class ProtectionMessages {

    private ProtectionMessages() {
    }

    public static void sendAccessDenied(final CommandSender sender, final Protection protection, final boolean actionBar) {
        BoltComponents.sendMessage(
                sender,
                Translation.LOCKED,
                actionBar,
                Placeholder.component(Translation.Placeholder.PROTECTION, Protections.displayType(protection, sender))
        );
    }

    public static void sendProtectionMessage(final CommandSender sender, final Protection protection, final String translationKey, final boolean actionBar) {
        BoltComponents.sendMessage(
                sender,
                translationKey,
                actionBar,
                Placeholder.component(Translation.Placeholder.PROTECTION_TYPE, Protections.protectionType(protection, sender)),
                Placeholder.component(Translation.Placeholder.PROTECTION, Protections.displayType(protection, sender))
        );
    }

    public static void sendProtectionNotification(final BoltPlugin plugin, final CommandSender sender, final Protection protection, final boolean actionBar) {
        Profiles.findOrLookupProfileByUniqueId(protection.getOwner()).thenAccept(profile -> SchedulerUtil.schedule(plugin, sender, () -> {
            final String owner = Optional.ofNullable(profile.name()).orElse(null);
            if (owner == null) {
                BoltComponents.sendMessage(
                        sender,
                        Translation.PROTECTION_NOTIFY_GENERIC,
                        actionBar,
                        Placeholder.component(Translation.Placeholder.PROTECTION_TYPE, Protections.protectionType(protection, sender)),
                        Placeholder.component(Translation.Placeholder.PROTECTION, Protections.displayType(protection, sender))
                );
                return;
            }
            BoltComponents.sendMessage(
                    sender,
                    Translation.PROTECTION_NOTIFY,
                    actionBar,
                    Placeholder.component(Translation.Placeholder.PROTECTION_TYPE, Protections.protectionType(protection, sender)),
                    Placeholder.component(Translation.Placeholder.PROTECTION, Protections.displayType(protection, sender)),
                    Placeholder.component(Translation.Placeholder.PLAYER, Component.text(owner))
            );
        }));
    }

    public static void sendProtectionInfo(final BoltPlugin plugin, final CommandSender sender, final Protection protection, final boolean full) {
        Profiles.findOrLookupProfileByUniqueId(protection.getOwner()).thenAccept(profile -> SchedulerUtil.schedule(plugin, sender, () -> BoltComponents.sendMessage(
                sender,
                full ? (protection.getAccess().isEmpty() ? Translation.INFO_FULL_NO_ACCESS : Translation.INFO_FULL_ACCESS) : Translation.INFO,
                Placeholder.component(Translation.Placeholder.PROTECTION_TYPE, Protections.protectionType(protection, sender)),
                Placeholder.component(Translation.Placeholder.PROTECTION, Protections.displayType(protection, sender)),
                Placeholder.component(Translation.Placeholder.PLAYER, Optional.ofNullable(profile.name()).<Component>map(Component::text).orElse(resolveTranslation(Translation.UNKNOWN, sender))),
                Placeholder.component(Translation.Placeholder.ACCESS_LIST_SIZE, Component.text(protection.getAccess().size())),
                Placeholder.component(Translation.Placeholder.ACCESS_LIST, Protections.accessList(protection.getAccess(), sender)),
                Placeholder.component(Translation.Placeholder.CREATED_TIME, Time.relativeTimestamp(protection.getCreated(), sender)),
                Placeholder.component(Translation.Placeholder.ACCESSED_TIME, Time.relativeTimestamp(protection.getAccessed(), sender))
        )));
    }
}
