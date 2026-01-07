package org.clockworx.villages.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
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
import org.clockworx.villages.regions.RegionManager;
import org.clockworx.villages.signs.WelcomeSignPlacer;
import org.clockworx.villages.storage.StorageManager;

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
            .executesPlayer((player, args) -> {
                handleInfoCommand(player);
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
                plugin.reloadPluginConfig();
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
        if (name == null || name.trim().isEmpty()) {
            player.sendMessage(Component.text("Village name cannot be empty!", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            village.setName(name.trim());
            
            storageManager.saveVillage(village).thenRun(() -> {
                // Update entrance welcome signs
                signPlacer.updateSignsAtEntrances(village);
                
                // Update bell signs with the new name
                Location bellLoc = village.getBellLocation();
                if (bellLoc != null && bellLoc.getWorld() != null) {
                    signManager.placeSignsAroundBell(bellLoc.getBlock(), village.getId(), name.trim());
                }
                
                player.sendMessage(Component.text("Village named: ", NamedTextColor.GREEN)
                    .append(Component.text(name.trim(), NamedTextColor.YELLOW)));
            });
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleInfoCommand(Player player) {
        String version = plugin.getPluginMeta().getVersion();
        int villageCount = storageManager.getVillageCount().join();
        String storageType = storageManager.getActiveType().name();
        String regionProvider = regionManager.getProviderName();
        
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
        
        player.sendMessage(message);
    }
    
    private void handleBorderShowCommand(Player player, int duration) {
        findNearestVillage(player).ifPresentOrElse(village -> {
            if (!village.hasBoundary()) {
                player.sendMessage(Component.text("Village has no calculated boundary.", NamedTextColor.RED));
                return;
            }
            
            VillageBoundary boundary = village.getBoundary();
            
            // Show particles along the boundary
            showBoundaryParticles(player, boundary, duration);
            
            player.sendMessage(Component.text("Showing village boundary for " + duration + " seconds.", NamedTextColor.GREEN));
            
        }, () -> {
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
        findNearestVillage(player).ifPresentOrElse(village -> {
            Location bellLoc = village.getBellLocation();
            if (bellLoc == null) {
                player.sendMessage(Component.text("Could not find village bell.", NamedTextColor.RED));
                return;
            }
            
            VillageBoundary newBoundary = boundaryCalculator.calculateAndPopulate(village);
            
            if (newBoundary != null) {
                village.setBoundary(newBoundary);
                storageManager.saveVillage(village).thenRun(() -> {
                    player.sendMessage(Component.text("Village boundary recalculated.", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("Size: " + newBoundary.getWidth() + " x " + 
                        newBoundary.getHeight() + " x " + newBoundary.getDepth(), NamedTextColor.GRAY));
                });
            } else {
                player.sendMessage(Component.text("Failed to calculate boundary.", NamedTextColor.RED));
            }
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceAddCommand(Player player) {
        findNearestVillage(player).ifPresentOrElse(village -> {
            Optional<VillageEntrance> entrance = entranceMarker.markEntrance(player, village);
            
            if (entrance.isPresent()) {
                storageManager.saveVillage(village).thenRun(() -> {
                    VillageEntrance e = entrance.get();
                    player.sendMessage(Component.text("Entrance marked at: " + 
                        e.getX() + ", " + e.getY() + ", " + e.getZ() + 
                        " facing " + e.getFacing(), NamedTextColor.GREEN));
                    
                    // Place welcome sign
                    signPlacer.placeSignAtEntrance(village, e, player.getWorld());
                });
            } else {
                player.sendMessage(Component.text("Could not mark entrance. " +
                    "Make sure you're near the village boundary.", NamedTextColor.RED));
            }
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceRemoveCommand(Player player) {
        findNearestVillage(player).ifPresentOrElse(village -> {
            Optional<VillageEntrance> removed = entranceMarker.removeNearestEntrance(player, village, 10);
            
            if (removed.isPresent()) {
                storageManager.saveVillage(village).thenRun(() -> {
                    VillageEntrance e = removed.get();
                    player.sendMessage(Component.text("Entrance removed at: " + 
                        e.getX() + ", " + e.getY() + ", " + e.getZ(), NamedTextColor.GREEN));
                    
                    // Remove welcome sign
                    signPlacer.removeSignAtEntrance(e, player.getWorld());
                });
            } else {
                player.sendMessage(Component.text("No entrance found within 10 blocks.", NamedTextColor.RED));
            }
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceListCommand(Player player) {
        findNearestVillage(player).ifPresentOrElse(village -> {
            List<VillageEntrance> entrances = village.getEntrances();
            
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
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleEntranceDetectCommand(Player player) {
        findNearestVillage(player).ifPresentOrElse(village -> {
            List<VillageEntrance> detected = entranceDetector.detectAndUpdate(village);
            
            storageManager.saveVillage(village).thenRun(() -> {
                int autoCount = (int) detected.stream().filter(VillageEntrance::isAutoDetected).count();
                player.sendMessage(Component.text("Detected " + autoCount + " entrances.", NamedTextColor.GREEN));
                
                // Place signs at new entrances
                signPlacer.placeSignsAtEntrances(village);
            });
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleRegionCreateCommand(Player player) {
        if (!regionManager.isAvailable()) {
            player.sendMessage(Component.text("No region plugin available.", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            if (!village.hasBoundary()) {
                player.sendMessage(Component.text("Village has no boundary. Recalculate first.", NamedTextColor.RED));
                return;
            }
            
            regionManager.createRegionWithDefaults(village, village.getBoundary())
                .thenAccept(regionId -> {
                    if (regionId.isPresent()) {
                        village.setRegionId(regionId.get());
                        storageManager.saveVillage(village);
                        player.sendMessage(Component.text("Region created: " + regionId.get(), NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Failed to create region.", NamedTextColor.RED));
                    }
                });
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleRegionDeleteCommand(Player player) {
        if (!regionManager.isAvailable()) {
            player.sendMessage(Component.text("No region plugin available.", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            regionManager.deleteRegion(village).thenAccept(deleted -> {
                if (deleted) {
                    village.setRegionId(null);
                    storageManager.saveVillage(village);
                    player.sendMessage(Component.text("Region deleted.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("No region found to delete.", NamedTextColor.RED));
                }
            });
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleRegionFlagsCommand(Player player, String flag, String value) {
        if (!regionManager.isAvailable()) {
            player.sendMessage(Component.text("No region plugin available.", NamedTextColor.RED));
            return;
        }
        
        findNearestVillage(player).ifPresentOrElse(village -> {
            if (flag == null) {
                // List flags
                regionManager.getProvider().getAllFlags(village).thenAccept(flags -> {
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
                regionManager.getFlag(village, flag).thenAccept(flagValue -> {
                    if (flagValue.isPresent()) {
                        player.sendMessage(Component.text(flag + ": ", NamedTextColor.GRAY)
                            .append(Component.text(flagValue.get(), NamedTextColor.WHITE)));
                    } else {
                        player.sendMessage(Component.text("Flag not set: " + flag, NamedTextColor.YELLOW));
                    }
                });
            } else {
                // Set flag
                regionManager.setFlag(village, flag, value).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("Flag set: " + flag + " = " + value, NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Failed to set flag.", NamedTextColor.RED));
                    }
                });
            }
            
        }, () -> {
            player.sendMessage(Component.text("No village found nearby.", NamedTextColor.RED));
        });
    }
    
    private void handleMigrateCommand(org.bukkit.command.CommandSender sender, String from, String to) {
        try {
            StorageManager.StorageType fromType = StorageManager.StorageType.fromId(from);
            StorageManager.StorageType toType = StorageManager.StorageType.fromId(to);
            
            sender.sendMessage(Component.text("Starting migration from " + fromType + " to " + toType + "...", NamedTextColor.YELLOW));
            
            storageManager.migrateData(fromType, toType).thenAccept(count -> {
                sender.sendMessage(Component.text("Migration complete! Migrated " + count + " villages.", NamedTextColor.GREEN));
            }).exceptionally(ex -> {
                sender.sendMessage(Component.text("Migration failed: " + ex.getMessage(), NamedTextColor.RED));
                return null;
            });
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Invalid storage type. Use: yaml, sqlite, mysql", NamedTextColor.RED));
        }
    }
    
    private void handleBackupCommand(org.bukkit.command.CommandSender sender) {
        String timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String backupPath = plugin.getDataFolder().getPath() + "/backups/villages_" + timestamp;
        
        storageManager.backup(backupPath).thenRun(() -> {
            sender.sendMessage(Component.text("Backup created: " + backupPath, NamedTextColor.GREEN));
        }).exceptionally(ex -> {
            sender.sendMessage(Component.text("Backup failed: " + ex.getMessage(), NamedTextColor.RED));
            return null;
        });
    }
    
    private void handleDebugStatusCommand(org.bukkit.command.CommandSender sender) {
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
        ConfigManager config = plugin.getConfigManager();
        config.setDebugEnabled(enable);
        
        if (enable) {
            sender.sendMessage(Component.text("Debug logging ENABLED", NamedTextColor.GREEN));
            plugin.getPluginLogger().info("Debug logging enabled by " + sender.getName());
        } else {
            sender.sendMessage(Component.text("Debug logging DISABLED", NamedTextColor.YELLOW));
            plugin.getLogger().info("Debug logging disabled by " + sender.getName());
        }
    }
    
    private void handleDebugCategoryToggle(org.bukkit.command.CommandSender sender, String category) {
        ConfigManager config = plugin.getConfigManager();
        
        // Ensure debug is enabled first
        if (!config.isDebugEnabled()) {
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
                sender.sendMessage(Component.text("Unknown category: " + category, NamedTextColor.RED));
                return;
            }
        }
        
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
    
    private Optional<Village> findVillageNear(Location loc, int radius) {
        List<Village> villages = storageManager.loadVillagesInWorld(loc.getWorld()).join();
        
        for (Village village : villages) {
            int dx = village.getBellX() - loc.getBlockX();
            int dz = village.getBellZ() - loc.getBlockZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist <= radius) {
                return Optional.of(village);
            }
        }
        
        return Optional.empty();
    }
}
