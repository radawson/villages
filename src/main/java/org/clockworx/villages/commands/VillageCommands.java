package org.clockworx.villages.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.boundary.VillageBoundaryCalculator;
import org.clockworx.villages.config.ConfigManager;
import org.clockworx.villages.detection.EntranceDetector;
import org.clockworx.villages.detection.EntranceMarker;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.model.Village;
import org.clockworx.villages.model.VillageBoundary;
import org.clockworx.villages.model.VillageEntrance;
import org.clockworx.villages.naming.VillageNameGenerator;
import org.clockworx.villages.regions.RegionManager;
import org.clockworx.villages.signs.WelcomeSignPlacer;
import org.clockworx.villages.storage.StorageManager;
import org.clockworx.villages.util.LogCategory;
import org.clockworx.villages.util.PluginLogger;

import java.util.List;
import java.util.Optional;

/**
 * Extended command handler for the Villages plugin.
 * 
 * Provides commands for:
 * - Village naming and info (existing)
 * - Border visualization and recalculation
 * - Entrance management (add, remove, list)
 * - Region management (create, delete, flags)
 * - Storage operations (reload, migrate, backup)
 * 
 * All commands use CommandAPI for modern command handling with
 * automatic tab completion and validation.
 * 
 * @author Clockworx
 * @since 0.2.0
 */
public class VillageCommands {
    
    private final VillagesPlugin plugin;
    private final StorageManager storageManager;
    private final RegionManager regionManager;
    private final VillageBoundaryCalculator boundaryCalculator;
    private final EntranceDetector entranceDetector;
    private final EntranceMarker entranceMarker;
    private final WelcomeSignPlacer signPlacer;
    private final SignManager signManager;
    private final PluginLogger logger;
    
    /**
     * Creates a new VillageCommands handler.
     */
    public VillageCommands(VillagesPlugin plugin, 
                          StorageManager storageManager,
                          RegionManager regionManager,
                          VillageBoundaryCalculator boundaryCalculator,
                          EntranceDetector entranceDetector,
                          EntranceMarker entranceMarker,
                          WelcomeSignPlacer signPlacer,
                          SignManager signManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.regionManager = regionManager;
        this.boundaryCalculator = boundaryCalculator;
        this.entranceDetector = entranceDetector;
        this.entranceMarker = entranceMarker;
        this.signPlacer = signPlacer;
        this.signManager = signManager;
        this.logger = plugin.getPluginLogger();
    }
    
    /**
     * Registers all village commands.
     */
    public void register() {
        // Main /village command with subcommands
        new CommandAPICommand("village")
            // Basic commands
            .withSubcommand(nameCommand())
            .withSubcommand(infoCommand())
            // Border commands
            .withSubcommand(borderShowCommand())
            .withSubcommand(borderRecalculateCommand())
            // Entrance commands
            .withSubcommand(entranceAddCommand())
            .withSubcommand(entranceRemoveCommand())
            .withSubcommand(entranceListCommand())
            .withSubcommand(entranceDetectCommand())
            // Region commands
            .withSubcommand(regionCreateCommand())
            .withSubcommand(regionDeleteCommand())
            .withSubcommand(regionFlagsCommand())
            // Admin commands
            .withSubcommand(reloadCommand())
            .withSubcommand(migrateCommand())
            .withSubcommand(backupCommand())
            // Debug commands
            .withSubcommand(debugCommand())
            .withSubcommand(debugOnCommand())
            .withSubcommand(debugOffCommand())
            .withSubcommand(debugStorageCommand())
            .withSubcommand(debugRegionsCommand())
            .withSubcommand(debugBoundariesCommand())
            .withSubcommand(debugEntrancesCommand())
            .register();
    }
    
    // ==================== Basic Commands ====================
    
    private CommandAPICommand nameCommand() {
        return new CommandAPICommand("name")
            .withPermission("villages.name")
            .withArguments(new GreedyStringArgument("name"))
            .executesPlayer((player, args) -> {
                String name = (String) args.get("name");
                handleNameCommand(player, name);
            });
    }
    
    private CommandAPICommand infoCommand() {
        return new CommandAPICommand("info")
            .withPermission("villages.info")
            .executes((sender, args) -> {
                handleInfoCommand(sender);
            });
    }
    
    // ==================== Border Commands ====================
    
    private CommandAPICommand borderShowCommand() {
        return new CommandAPICommand("border")
            .withSubcommand(new CommandAPICommand("show")
                .withPermission("villages.border.show")
                .withOptionalArguments(new IntegerArgument("duration"))
                .executesPlayer((player, args) -> {
                    int duration = (Integer) args.getOrDefault("duration", 30);
                    handleBorderShowCommand(player, duration);
                }));
    }
    
    private CommandAPICommand borderRecalculateCommand() {
        return new CommandAPICommand("border")
            .withSubcommand(new CommandAPICommand("recalculate")
                .withPermission("villages.border.recalculate")
                .executesPlayer((player, args) -> {
                    handleBorderRecalculateCommand(player);
                }));
    }
    
    // ==================== Entrance Commands ====================
    
    private CommandAPICommand entranceAddCommand() {
        return new CommandAPICommand("entrance")
            .withSubcommand(new CommandAPICommand("add")
                .withPermission("villages.entrance.add")
                .executesPlayer((player, args) -> {
                    handleEntranceAddCommand(player);
                }));
    }
    
    private CommandAPICommand entranceRemoveCommand() {
        return new CommandAPICommand("entrance")
            .withSubcommand(new CommandAPICommand("remove")
                .withPermission("villages.entrance.remove")
                .executesPlayer((player, args) -> {
                    handleEntranceRemoveCommand(player);
                }));
    }
    
    private CommandAPICommand entranceListCommand() {
        return new CommandAPICommand("entrance")
            .withSubcommand(new CommandAPICommand("list")
                .withPermission("villages.entrance.list")
                .executesPlayer((player, args) -> {
                    handleEntranceListCommand(player);
                }));
    }
    
    private CommandAPICommand entranceDetectCommand() {
        return new CommandAPICommand("entrance")
            .withSubcommand(new CommandAPICommand("detect")
                .withPermission("villages.entrance.detect")
                .executesPlayer((player, args) -> {
                    handleEntranceDetectCommand(player);
                }));
    }
    
    // ==================== Region Commands ====================
    
    private CommandAPICommand regionCreateCommand() {
        return new CommandAPICommand("region")
            .withSubcommand(new CommandAPICommand("create")
                .withPermission("villages.region.create")
                .executesPlayer((player, args) -> {
                    handleRegionCreateCommand(player);
                }));
    }
    
    private CommandAPICommand regionDeleteCommand() {
        return new CommandAPICommand("region")
            .withSubcommand(new CommandAPICommand("delete")
                .withPermission("villages.region.delete")
                .executesPlayer((player, args) -> {
                    handleRegionDeleteCommand(player);
                }));
    }
    
    private CommandAPICommand regionFlagsCommand() {
        return new CommandAPICommand("region")
            .withSubcommand(new CommandAPICommand("flags")
                .withPermission("villages.region.flags")
                .withOptionalArguments(new StringArgument("flag"))
                .withOptionalArguments(new StringArgument("value"))
                .executesPlayer((player, args) -> {
                    String flag = (String) args.getOrDefault("flag", null);
                    String value = (String) args.getOrDefault("value", null);
                    handleRegionFlagsCommand(player, flag, value);
                }));
    }
    
    // ==================== Admin Commands ====================
    
    private CommandAPICommand reloadCommand() {
        return new CommandAPICommand("reload")
            .withPermission("villages.admin.reload")
            .executes((sender, args) -> {
                logger.debugCommand("Executing /village reload command by " + sender.getName());
                plugin.reloadPluginConfig();
                logger.info(LogCategory.COMMAND, "Configuration reloaded by " + sender.getName());
                sender.sendMessage(Component.text("Villages configuration reloaded.", NamedTextColor.GREEN));
            });
    }
    
    private CommandAPICommand migrateCommand() {
        return new CommandAPICommand("migrate")
            .withPermission("villages.admin.migrate")
            .withArguments(new StringArgument("from"))
            .withArguments(new StringArgument("to"))
            .executes((sender, args) -> {
                String from = (String) args.get("from");
                String to = (String) args.get("to");
                handleMigrateCommand(sender, from, to);
            });
    }
    
    private CommandAPICommand backupCommand() {
        return new CommandAPICommand("backup")
            .withPermission("villages.admin.backup")
            .executes((sender, args) -> {
                handleBackupCommand(sender);
            });
    }
    
    // ==================== Debug Commands ====================
    
    private CommandAPICommand debugCommand() {
        return new CommandAPICommand("debug")
            .withPermission("villages.admin.debug")
            .executes((sender, args) -> {
                handleDebugStatusCommand(sender);
            });
    }
    
    private CommandAPICommand debugOnCommand() {
        return new CommandAPICommand("debug")
            .withSubcommand(new CommandAPICommand("on")
                .withPermission("villages.admin.debug")
                .executes((sender, args) -> {
                    handleDebugToggleCommand(sender, true);
                }));
    }
    
    private CommandAPICommand debugOffCommand() {
        return new CommandAPICommand("debug")
            .withSubcommand(new CommandAPICommand("off")
                .withPermission("villages.admin.debug")
                .executes((sender, args) -> {
                    handleDebugToggleCommand(sender, false);
                }));
    }
    
    private CommandAPICommand debugStorageCommand() {
        return new CommandAPICommand("debug")
            .withSubcommand(new CommandAPICommand("storage")
                .withPermission("villages.admin.debug")
                .executes((sender, args) -> {
                    handleDebugCategoryToggle(sender, "storage");
                }));
    }
    
    private CommandAPICommand debugRegionsCommand() {
        return new CommandAPICommand("debug")
            .withSubcommand(new CommandAPICommand("regions")
                .withPermission("villages.admin.debug")
                .executes((sender, args) -> {
                    handleDebugCategoryToggle(sender, "regions");
                }));
    }
    
    private CommandAPICommand debugBoundariesCommand() {
        return new CommandAPICommand("debug")
            .withSubcommand(new CommandAPICommand("boundaries")
                .withPermission("villages.admin.debug")
                .executes((sender, args) -> {
                    handleDebugCategoryToggle(sender, "boundaries");
                }));
    }
    
    private CommandAPICommand debugEntrancesCommand() {
        return new CommandAPICommand("debug")
            .withSubcommand(new CommandAPICommand("entrances")
                .withPermission("villages.admin.debug")
                .executes((sender, args) -> {
                    handleDebugCategoryToggle(sender, "entrances");
                }));
    }
    
    // ==================== Command Handlers ====================
    
    private void handleNameCommand(Player player, String name) {
        logger.debugCommand("Executing /village name command by " + player.getName() + " with name: " + name);
        
        boolean regenerateName = name == null || name.trim().isEmpty();
        if (regenerateName) {
            logger.debugCommand("Empty name provided, generating a new name for " + player.getName());
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            logger.debugCommand("Found village " + village.getId() + " for naming by " + player.getName());
            String resolvedName = name != null ? name.trim() : "";
            if (regenerateName) {
                VillageNameGenerator nameGenerator = plugin.getNameGenerator();
                if (nameGenerator == null) {
                    logger.warning(LogCategory.COMMAND, "Village name command failed: name generator not available for " + player.getName());
                    player.sendMessage(Component.text("Name generator is not available.", NamedTextColor.RED));
                    return;
                }

                String generatedName = nameGenerator.generateName(village, true);
                if (generatedName == null || generatedName.trim().isEmpty()) {
                    logger.warning(LogCategory.COMMAND, "Village name command failed: could not generate a name for " + village.getId());
                    player.sendMessage(Component.text("Could not generate a new village name.", NamedTextColor.RED));
                    return;
                }

                resolvedName = generatedName.trim();
            }

            village.setName(resolvedName);
            final String finalName = resolvedName;

            storageManager.saveVillage(village).thenRun(() -> {
                // Update entrance welcome signs
                signPlacer.updateSignsAtEntrances(village);

                // Update bell signs with the new name
                Location bellLoc = village.getBellLocation();
                if (bellLoc != null && bellLoc.getWorld() != null) {
                    signManager.placeSignsAroundBell(bellLoc.getBlock(), village.getId(), finalName);
                }

                logger.info(LogCategory.COMMAND, "Village " + village.getId() + " named to '" + finalName + "' by " + player.getName());
                player.sendMessage(Component.text("Village named: ", NamedTextColor.GREEN)
                    .append(Component.text(finalName, NamedTextColor.YELLOW)));
            });
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Village name command failed: no village found near " + player.getName() + " at " + player.getLocation());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleInfoCommand(CommandSender sender) {
        logger.debugCommand("Executing /village info command by " + sender.getName());
        
        String version = plugin.getPluginMeta().getVersion();
        int villageCount = storageManager.getVillageCount().join();
        String storageType = storageManager.getActiveType().name();
        String regionProvider = regionManager.getProviderName();
        
        logger.debugCommand("Info command result: " + villageCount + " villages, storage: " + storageType + ", region: " + regionProvider);
        
        Component message = Component.text()
            .append(Component.text("=== Villages Plugin Info ===\n", NamedTextColor.GOLD))
            .append(Component.text("Version: ", NamedTextColor.GRAY))
            .append(Component.text(version, NamedTextColor.WHITE))
            .append(Component.text("\n"))
            .append(Component.text("Tracked Villages: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(villageCount), NamedTextColor.WHITE))
            .append(Component.text("\n"))
            .append(Component.text("Storage: ", NamedTextColor.GRAY))
            .append(Component.text(storageType, NamedTextColor.WHITE))
            .append(Component.text("\n"))
            .append(Component.text("Region Provider: ", NamedTextColor.GRAY))
            .append(Component.text(regionProvider, NamedTextColor.WHITE))
            .build();
        
        sender.sendMessage(message);
    }
    
    private void handleBorderShowCommand(Player player, int duration) {
        logger.debugCommand("Executing /village border show command by " + player.getName() + " with duration: " + duration);
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            if (!village.hasBoundary()) {
                logger.warning(LogCategory.COMMAND, "Border show command failed: village " + village.getId() + " has no boundary");
                player.sendMessage(Component.text("Village has no calculated boundary.", NamedTextColor.RED));
                return;
            }
            
            VillageBoundary boundary = village.getBoundary();
            logger.debugCommand("Showing boundary for village " + village.getId() + " to " + player.getName() + " for " + duration + " seconds");
            
            // Show particles along the boundary
            showBoundaryParticles(player, boundary, duration);
            
            player.sendMessage(Component.text("Showing village boundary for " + duration + " seconds.", NamedTextColor.GREEN));
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Border show command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void showBoundaryParticles(Player player, VillageBoundary boundary, int duration) {
        World world = player.getWorld();
        
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                // Draw particles every 5 ticks
                if (ticks % 5 == 0) {
                    int y = (int) player.getLocation().getY();
                    
                    // Draw edges
                    for (int x = boundary.getMinX(); x <= boundary.getMaxX(); x += 2) {
                        spawnParticle(world, x, y, boundary.getMinZ());
                        spawnParticle(world, x, y, boundary.getMaxZ());
                    }
                    for (int z = boundary.getMinZ(); z <= boundary.getMaxZ(); z += 2) {
                        spawnParticle(world, boundary.getMinX(), y, z);
                        spawnParticle(world, boundary.getMaxX(), y, z);
                    }
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void spawnParticle(World world, int x, int y, int z) {
        world.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
    }
    
    private void handleBorderRecalculateCommand(Player player) {
        logger.debugCommand("Executing /village border recalculate command by " + player.getName());
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            Location bellLoc = village.getBellLocation();
            if (bellLoc == null) {
                logger.warning(LogCategory.COMMAND, "Border recalculate command failed: no bell location for village " + village.getId());
                player.sendMessage(Component.text("Could not find village bell.", NamedTextColor.RED));
                return;
            }
            
            logger.debugCommand("Recalculating boundary for village " + village.getId() + " by " + player.getName());
            VillageBoundary newBoundary = boundaryCalculator.calculateAndPopulate(village);
            
            if (newBoundary != null) {
                village.setBoundary(newBoundary);
                storageManager.saveVillage(village).thenRun(() -> {
                    logger.info(LogCategory.COMMAND, "Village " + village.getId() + " boundary recalculated by " + player.getName() + 
                        " - Size: " + newBoundary.getWidth() + " x " + newBoundary.getHeight() + " x " + newBoundary.getDepth());
                    player.sendMessage(Component.text("Village boundary recalculated.", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("Size: " + newBoundary.getWidth() + " x " + 
                        newBoundary.getHeight() + " x " + newBoundary.getDepth(), NamedTextColor.GRAY));
                });
            } else {
                logger.warning(LogCategory.COMMAND, "Border recalculate command failed: boundary calculation returned null for village " + village.getId());
                player.sendMessage(Component.text("Failed to calculate boundary.", NamedTextColor.RED));
            }
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Border recalculate command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceAddCommand(Player player) {
        logger.debugCommand("Executing /village entrance add command by " + player.getName() + " at " + player.getLocation());
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            Optional<VillageEntrance> entrance = entranceMarker.markEntrance(player, village);
            
            if (entrance.isPresent()) {
                VillageEntrance e = entrance.get();
                logger.debugCommand("Entrance marked for village " + village.getId() + " at " + e.getX() + ", " + e.getY() + ", " + e.getZ());
                
                storageManager.saveVillage(village).thenRun(() -> {
                    logger.info(LogCategory.COMMAND, "Entrance added to village " + village.getId() + " by " + player.getName() + 
                        " at " + e.getX() + ", " + e.getY() + ", " + e.getZ() + " facing " + e.getFacing());
                    player.sendMessage(Component.text("Entrance marked at: " + 
                        e.getX() + ", " + e.getY() + ", " + e.getZ() + 
                        " facing " + e.getFacing(), NamedTextColor.GREEN));
                    
                    // Place welcome sign
                    signPlacer.placeSignAtEntrance(village, e, player.getWorld());
                });
            } else {
                logger.warning(LogCategory.COMMAND, "Entrance add command failed: could not mark entrance for village " + village.getId() + " by " + player.getName());
                player.sendMessage(Component.text("Could not mark entrance. " +
                    "Make sure you're near the village boundary.", NamedTextColor.RED));
            }
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Entrance add command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceRemoveCommand(Player player) {
        logger.debugCommand("Executing /village entrance remove command by " + player.getName() + " at " + player.getLocation());
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            Optional<VillageEntrance> removed = entranceMarker.removeNearestEntrance(player, village, 10);
            
            if (removed.isPresent()) {
                VillageEntrance e = removed.get();
                logger.debugCommand("Entrance removed for village " + village.getId() + " at " + e.getX() + ", " + e.getY() + ", " + e.getZ());
                
                storageManager.saveVillage(village).thenRun(() -> {
                    logger.info(LogCategory.COMMAND, "Entrance removed from village " + village.getId() + " by " + player.getName() + 
                        " at " + e.getX() + ", " + e.getY() + ", " + e.getZ());
                    player.sendMessage(Component.text("Entrance removed at: " + 
                        e.getX() + ", " + e.getY() + ", " + e.getZ(), NamedTextColor.GREEN));
                    
                    // Remove welcome sign
                    signPlacer.removeSignAtEntrance(e, player.getWorld());
                });
            } else {
                logger.warning(LogCategory.COMMAND, "Entrance remove command failed: no entrance found within 10 blocks for village " + village.getId());
                player.sendMessage(Component.text("No entrance found within 10 blocks.", NamedTextColor.RED));
            }
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Entrance remove command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceListCommand(Player player) {
        logger.debugCommand("Executing /village entrance list command by " + player.getName());
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            List<VillageEntrance> entrances = village.getEntrances();
            
            logger.debugCommand("Listing " + entrances.size() + " entrances for village " + village.getId());
            
            if (entrances.isEmpty()) {
                player.sendMessage(Component.text("Village has no entrances.", NamedTextColor.YELLOW));
                return;
            }
            
            player.sendMessage(Component.text("=== Village Entrances ===", NamedTextColor.GOLD));
            
            for (int i = 0; i < entrances.size(); i++) {
                VillageEntrance e = entrances.get(i);
                String type = e.isAutoDetected() ? "[Auto]" : "[Manual]";
                player.sendMessage(Component.text((i + 1) + ". " + type + " ", NamedTextColor.GRAY)
                    .append(Component.text(e.getX() + ", " + e.getY() + ", " + e.getZ(), NamedTextColor.WHITE))
                    .append(Component.text(" facing " + e.getFacing(), NamedTextColor.GRAY)));
            }
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Entrance list command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceDetectCommand(Player player) {
        logger.debugCommand("Executing /village entrance detect command by " + player.getName());
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            logger.debugCommand("Detecting entrances for village " + village.getId());
            List<VillageEntrance> detected = entranceDetector.detectAndUpdate(village);
            
            storageManager.saveVillage(village).thenRun(() -> {
                int autoCount = (int) detected.stream().filter(VillageEntrance::isAutoDetected).count();
                logger.info(LogCategory.COMMAND, "Entrance detection completed for village " + village.getId() + 
                    " by " + player.getName() + " - detected " + autoCount + " entrances");
                player.sendMessage(Component.text("Detected " + autoCount + " entrances.", NamedTextColor.GREEN));
                
                // Place signs at new entrances
                signPlacer.placeSignsAtEntrances(village);
            });
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Entrance detect command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleRegionCreateCommand(Player player) {
        logger.debugCommand("Executing /village region create command by " + player.getName());
        
        if (!regionManager.isAvailable()) {
            logger.warning(LogCategory.COMMAND, "Region create command failed: no region plugin available");
            player.sendMessage(Component.text("No region plugin available.", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            if (!village.hasBoundary()) {
                logger.warning(LogCategory.COMMAND, "Region create command failed: village " + village.getId() + " has no boundary");
                player.sendMessage(Component.text("Village has no boundary. Recalculate first.", NamedTextColor.RED));
                return;
            }
            
            logger.debugCommand("Creating region for village " + village.getId() + " by " + player.getName());
            regionManager.createRegionWithDefaults(village, village.getBoundary())
                .thenAccept(regionId -> {
                    if (regionId.isPresent()) {
                        village.setRegionId(regionId.get());
                        storageManager.saveVillage(village);
                        logger.info(LogCategory.COMMAND, "Region created for village " + village.getId() + 
                            " by " + player.getName() + " - region ID: " + regionId.get());
                        player.sendMessage(Component.text("Region created: " + regionId.get(), NamedTextColor.GREEN));
                    } else {
                        logger.warning(LogCategory.COMMAND, "Region create command failed: region creation returned empty for village " + village.getId());
                        player.sendMessage(Component.text("Failed to create region.", NamedTextColor.RED));
                    }
                });
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Region create command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleRegionDeleteCommand(Player player) {
        logger.debugCommand("Executing /village region delete command by " + player.getName());
        
        if (!regionManager.isAvailable()) {
            logger.warning(LogCategory.COMMAND, "Region delete command failed: no region plugin available");
            player.sendMessage(Component.text("No region plugin available.", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            logger.debugCommand("Deleting region for village " + village.getId() + " by " + player.getName());
            regionManager.deleteRegion(village).thenAccept(deleted -> {
                if (deleted) {
                    village.setRegionId(null);
                    storageManager.saveVillage(village);
                    logger.info(LogCategory.COMMAND, "Region deleted for village " + village.getId() + " by " + player.getName());
                    player.sendMessage(Component.text("Region deleted.", NamedTextColor.GREEN));
                } else {
                    logger.warning(LogCategory.COMMAND, "Region delete command failed: no region found for village " + village.getId());
                    player.sendMessage(Component.text("No region found to delete.", NamedTextColor.RED));
                }
            });
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Region delete command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleRegionFlagsCommand(Player player, String flag, String value) {
        logger.debugCommand("Executing /village region flags command by " + player.getName() + 
            " with flag: " + flag + ", value: " + value);
        
        if (!regionManager.isAvailable()) {
            logger.warning(LogCategory.COMMAND, "Region flags command failed: no region plugin available");
            player.sendMessage(Component.text("No region plugin available.", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            if (flag == null) {
                // List flags
                logger.debugCommand("Listing all flags for village " + village.getId());
                regionManager.getProvider().getAllFlags(village).thenAccept(flags -> {
                    logger.debugCommand("Found " + flags.size() + " flags for village " + village.getId());
                    if (flags.isEmpty()) {
                        player.sendMessage(Component.text("No flags set on this region.", NamedTextColor.YELLOW));
                        return;
                    }
                    
                    player.sendMessage(Component.text("=== Region Flags ===", NamedTextColor.GOLD));
                    for (var entry : flags.entrySet()) {
                        player.sendMessage(Component.text(entry.getKey() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(entry.getValue(), NamedTextColor.WHITE)));
                    }
                });
            } else if (value == null) {
                // Get specific flag
                logger.debugCommand("Getting flag '" + flag + "' for village " + village.getId());
                regionManager.getFlag(village, flag).thenAccept(flagValue -> {
                    if (flagValue.isPresent()) {
                        logger.debugCommand("Flag '" + flag + "' = '" + flagValue.get() + "' for village " + village.getId());
                        player.sendMessage(Component.text(flag + ": ", NamedTextColor.GRAY)
                            .append(Component.text(flagValue.get(), NamedTextColor.WHITE)));
                    } else {
                        logger.debugCommand("Flag '" + flag + "' not set for village " + village.getId());
                        player.sendMessage(Component.text("Flag not set: " + flag, NamedTextColor.YELLOW));
                    }
                });
            } else {
                // Set flag
                logger.debugCommand("Setting flag '" + flag + "' = '" + value + "' for village " + village.getId());
                regionManager.setFlag(village, flag, value).thenAccept(success -> {
                    if (success) {
                        logger.info(LogCategory.COMMAND, "Flag '" + flag + "' set to '" + value + 
                            "' for village " + village.getId() + " by " + player.getName());
                        player.sendMessage(Component.text("Flag set: " + flag + " = " + value, NamedTextColor.GREEN));
                    } else {
                        logger.warning(LogCategory.COMMAND, "Region flags command failed: could not set flag '" + 
                            flag + "' for village " + village.getId());
                        player.sendMessage(Component.text("Failed to set flag.", NamedTextColor.RED));
                    }
                });
            }
            
        }, () -> {
            logger.warning(LogCategory.COMMAND, "Region flags command failed: no village found near " + player.getName());
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleMigrateCommand(org.bukkit.command.CommandSender sender, String from, String to) {
        logger.debugCommand("Executing /village migrate command by " + sender.getName() + " from " + from + " to " + to);
        
        try {
            StorageManager.StorageType fromType = StorageManager.StorageType.fromId(from);
            StorageManager.StorageType toType = StorageManager.StorageType.fromId(to);
            
            logger.info(LogCategory.COMMAND, "Starting migration from " + fromType + " to " + toType + " by " + sender.getName());
            sender.sendMessage(Component.text("Starting migration from " + fromType + " to " + toType + "...", NamedTextColor.YELLOW));
            
            storageManager.migrateData(fromType, toType).thenAccept(count -> {
                logger.info(LogCategory.COMMAND, "Migration completed by " + sender.getName() + " - migrated " + count + " villages");
                sender.sendMessage(Component.text("Migration complete! Migrated " + count + " villages.", NamedTextColor.GREEN));
            }).exceptionally(ex -> {
                logger.severe(LogCategory.COMMAND, "Migration failed by " + sender.getName() + ": " + ex.getMessage(), ex);
                sender.sendMessage(Component.text("Migration failed: " + ex.getMessage(), NamedTextColor.RED));
                return null;
            });
            
        } catch (Exception e) {
            logger.warning(LogCategory.COMMAND, "Migration command failed: invalid storage type - from: " + from + ", to: " + to);
            sender.sendMessage(Component.text("Invalid storage type. Use: yaml, sqlite, mysql", NamedTextColor.RED));
        }
    }
    
    private void handleBackupCommand(org.bukkit.command.CommandSender sender) {
        logger.debugCommand("Executing /village backup command by " + sender.getName());
        
        String timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String backupPath = plugin.getDataFolder().getPath() + "/backups/villages_" + timestamp;
        
        logger.info(LogCategory.COMMAND, "Creating backup by " + sender.getName() + " to: " + backupPath);
        storageManager.backup(backupPath).thenRun(() -> {
            logger.info(LogCategory.COMMAND, "Backup created successfully by " + sender.getName() + " at: " + backupPath);
            sender.sendMessage(Component.text("Backup created: " + backupPath, NamedTextColor.GREEN));
        }).exceptionally(ex -> {
            logger.severe(LogCategory.COMMAND, "Backup failed by " + sender.getName() + ": " + ex.getMessage(), ex);
            sender.sendMessage(Component.text("Backup failed: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }
    
    private void handleDebugStatusCommand(org.bukkit.command.CommandSender sender) {
        logger.debugCommand("Executing /village debug command by " + sender.getName());
        
        ConfigManager config = plugin.getConfigManager();
        
        Component message = Component.text()
            .append(Component.text("=== Debug Status ===\n", NamedTextColor.GOLD))
            .append(Component.text("Debug: ", NamedTextColor.GRAY))
            .append(Component.text(config.isDebugEnabled() ? "ENABLED" : "DISABLED", 
                config.isDebugEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(Component.text("\n"))
            .append(Component.text("Verbose: ", NamedTextColor.GRAY))
            .append(Component.text(config.isVerbose() ? "ON" : "OFF", 
                config.isVerbose() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
            .append(Component.text("\n"))
            .append(Component.text("Categories:\n", NamedTextColor.GRAY))
            .append(Component.text("  Storage: ", NamedTextColor.GRAY))
            .append(Component.text(config.shouldLogStorage() ? "ON" : "OFF", 
                config.shouldLogStorage() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
            .append(Component.text("\n"))
            .append(Component.text("  Regions: ", NamedTextColor.GRAY))
            .append(Component.text(config.shouldLogRegions() ? "ON" : "OFF", 
                config.shouldLogRegions() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
            .append(Component.text("\n"))
            .append(Component.text("  Boundaries: ", NamedTextColor.GRAY))
            .append(Component.text(config.shouldLogBoundaries() ? "ON" : "OFF", 
                config.shouldLogBoundaries() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
            .append(Component.text("\n"))
            .append(Component.text("  Entrances: ", NamedTextColor.GRAY))
            .append(Component.text(config.shouldLogEntrances() ? "ON" : "OFF", 
                config.shouldLogEntrances() ? NamedTextColor.GREEN : NamedTextColor.GRAY))
            .build();
        
        sender.sendMessage(message);
    }
    
    private void handleDebugToggleCommand(org.bukkit.command.CommandSender sender, boolean enable) {
        logger.debugCommand("Executing /village debug " + (enable ? "on" : "off") + " command by " + sender.getName());
        
        ConfigManager config = plugin.getConfigManager();
        config.setDebugEnabled(enable);
        
        if (enable) {
            sender.sendMessage(Component.text("Debug logging ENABLED", NamedTextColor.GREEN));
            logger.info(LogCategory.COMMAND, "Debug logging enabled by " + sender.getName());
        } else {
            sender.sendMessage(Component.text("Debug logging DISABLED", NamedTextColor.YELLOW));
            logger.info(LogCategory.COMMAND, "Debug logging disabled by " + sender.getName());
        }
    }
    
    private void handleDebugCategoryToggle(org.bukkit.command.CommandSender sender, String category) {
        logger.debugCommand("Executing /village debug " + category + " command by " + sender.getName());
        
        ConfigManager config = plugin.getConfigManager();
        
        // Ensure debug is enabled first
        if (!config.isDebugEnabled()) {
            logger.warning(LogCategory.COMMAND, "Debug category toggle failed: debug is disabled");
            sender.sendMessage(Component.text("Debug is disabled. Enable with /village debug on", NamedTextColor.YELLOW));
            return;
        }
        
        boolean newState;
        String categoryName;
        
        switch (category.toLowerCase()) {
            case "storage" -> {
                newState = !config.shouldLogStorage();
                config.setLogStorage(newState);
                categoryName = "Storage";
            }
            case "regions" -> {
                newState = !config.shouldLogRegions();
                config.setLogRegions(newState);
                categoryName = "Regions";
            }
            case "boundaries" -> {
                newState = !config.shouldLogBoundaries();
                config.setLogBoundaries(newState);
                categoryName = "Boundaries";
            }
            case "entrances" -> {
                newState = !config.shouldLogEntrances();
                config.setLogEntrances(newState);
                categoryName = "Entrances";
            }
            default -> {
                logger.warning(LogCategory.COMMAND, "Debug category toggle failed: unknown category '" + category + "'");
                sender.sendMessage(Component.text("Unknown category: " + category, NamedTextColor.RED));
                return;
            }
        }
        
        logger.info(LogCategory.COMMAND, "Debug category '" + categoryName + "' logging " + (newState ? "enabled" : "disabled") + " by " + sender.getName());
        sender.sendMessage(Component.text(categoryName + " logging: ", NamedTextColor.GRAY)
            .append(Component.text(newState ? "ENABLED" : "DISABLED", 
                newState ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Finds the nearest village to a player.
     */
    private Optional<Village> findNearestVillage(Player player) {
        Location loc = player.getLocation();
        
        // First check if player is inside a village boundary
        Optional<Village> atLocation = storageManager.findVillageAt(
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ()
        ).join();
        
        if (atLocation.isPresent()) {
            return atLocation;
        }
        
        // If not inside, find nearest by loading all villages in world
        List<Village> villages = storageManager.loadVillagesInWorld(loc.getWorld()).join();
        
        Village nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (Village village : villages) {
            int dx = village.getBellX() - loc.getBlockX();
            int dz = village.getBellZ() - loc.getBlockZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist < nearestDist && dist <= 100) {
                nearestDist = dist;
                nearest = village;
            }
        }
        
        return Optional.ofNullable(nearest);
    }
}
