package org.popcraft.bolt.command.impl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.popcraft.bolt.BoltPlugin;
import org.popcraft.bolt.command.Arguments;
import org.popcraft.bolt.command.BoltCommand;
import org.popcraft.bolt.data.Profile;
import org.popcraft.bolt.lang.Translation;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.EntityProtection;
import org.popcraft.bolt.protection.Protection;
import org.popcraft.bolt.util.BoltComponents;
import org.popcraft.bolt.util.Pagination;
import org.popcraft.bolt.util.Profiles;
import org.popcraft.bolt.util.SchedulerUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AdminFindCommand extends BoltCommand {
    private static final String NULL_OWNER_ALIAS = "#null";
    private static final String FILTER_ALL = "all";
    private static final String FILTER_CONTAINER = "container";
    private static final Set<Material> CONTAINER_BLOCKS = EnumSet.of(
            Material.BARREL,
            Material.BLAST_FURNACE,
            Material.BREWING_STAND,
            Material.CHEST,
            Material.CHISELED_BOOKSHELF,
            Material.COMPOSTER,
            Material.CRAFTER,
            Material.DECORATED_POT,
            Material.DISPENSER,
            Material.DROPPER,
            Material.ENDER_CHEST,
            Material.FURNACE,
            Material.HOPPER,
            Material.JUKEBOX,
            Material.SMOKER,
            Material.TRAPPED_CHEST
    );
    private static final Set<EntityType> CONTAINER_ENTITIES = EnumSet.of(
            EntityType.ARMOR_STAND,
            EntityType.CHEST_MINECART,
            EntityType.FURNACE_MINECART,
            EntityType.GLOW_ITEM_FRAME,
            EntityType.HOPPER_MINECART,
            EntityType.ITEM_FRAME
    );

    public AdminFindCommand(BoltPlugin plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSender sender, Arguments arguments) {
        if (arguments.remaining() < 1) {
            shortHelp(sender, arguments);
            return;
        }
        final String player = arguments.next();
        final String filter = normalizeFilter(arguments.next());
        if (filter == null) {
            shortHelp(sender, arguments);
            return;
        }
        SchedulerUtil.thenAcceptSender(resolveOwner(player), plugin, sender, owner -> {
            if (owner == null) {
                BoltComponents.sendMessage(
                        sender,
                        Translation.PLAYER_NOT_FOUND,
                        Placeholder.component(Translation.Placeholder.PLAYER, Component.text(player))
                );
                return;
            }
            final List<Protection> protectionsFromPlayer = plugin.loadProtections().stream()
                    .filter(protection -> owner.equals(protection.getOwner()))
                    .filter(protection -> FILTER_ALL.equals(filter) || isContainerProtection(protection))
                    .sorted(Comparator.comparingLong(Protection::getCreated).reversed())
                    .toList();
            Pagination.runPage(plugin, sender, protectionsFromPlayer, 0);
        });
    }

    private CompletableFuture<UUID> resolveOwner(final String player) {
        if (NULL_OWNER_ALIAS.equalsIgnoreCase(player)) {
            return CompletableFuture.completedFuture(Profiles.NIL_UUID);
        }
        return Profiles.findOrLookupProfileByName(player)
                .thenApply(Profile::uuid);
    }

    private String normalizeFilter(final String filter) {
        if (filter == null || filter.isBlank()) {
            return FILTER_ALL;
        }
        final String normalized = filter.toLowerCase(Locale.ROOT);
        if (FILTER_ALL.equals(normalized) || FILTER_CONTAINER.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private boolean isContainerProtection(final Protection protection) {
        return switch (protection) {
            case BlockProtection blockProtection -> isContainerBlock(blockProtection.getBlock());
            case EntityProtection entityProtection -> isContainerEntity(entityProtection.getEntity());
        };
    }

    private boolean isContainerBlock(final String block) {
        final Material material = Material.matchMaterial(block);
        return material != null && (CONTAINER_BLOCKS.contains(material) || material.name().endsWith("SHULKER_BOX") || material.name().endsWith("COPPER_CHEST"));
    }

    private boolean isContainerEntity(final String entity) {
        return Arrays.stream(EntityType.values())
                .filter(entityType -> entityType.name().equalsIgnoreCase(entity))
                .findFirst()
                .map(entityType -> CONTAINER_ENTITIES.contains(entityType) || entityType.name().endsWith("_CHEST_BOAT") || entityType.name().endsWith("_CHEST_RAFT"))
                .orElse(false);
    }

    @Override
    public List<String> suggestions(CommandSender sender, Arguments arguments) {
        if (arguments.remaining() == 0) {
            return Collections.emptyList();
        }
        arguments.next();
        if (arguments.remaining() == 0) {
            final List<String> suggestions = plugin.getServer().getOnlinePlayers().stream().map(Player::getName).collect(java.util.stream.Collectors.toList());
            suggestions.add(NULL_OWNER_ALIAS);
            return suggestions;
        }
        arguments.next();
        if (arguments.remaining() == 0) {
            return List.of(FILTER_ALL, FILTER_CONTAINER);
        }
        return Collections.emptyList();
    }

    @Override
    public void shortHelp(CommandSender sender, Arguments arguments) {
        BoltComponents.sendMessage(
                sender,
                Translation.HELP_COMMAND_SHORT_ADMIN_FIND,
                Placeholder.component(Translation.Placeholder.COMMAND, Component.text("/bolt admin find"))
        );
    }

    @Override
    public void longHelp(CommandSender sender, Arguments arguments) {
        BoltComponents.sendMessage(sender, Translation.HELP_COMMAND_LONG_ADMIN_FIND);
    }
}
