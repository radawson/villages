package org.clockworx.villages.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.clockworx.villages.VillagesPlugin;
import org.clockworx.villages.managers.SignManager;
import org.clockworx.villages.managers.VillageManager;

import java.util.UUID;

/**
 * Handles the /village command using CommandAPI.
 * 
 * This command allows OP players to name villages by standing in the village's chunk.
 * The command finds the nearest bell block in the player's current chunk and sets its name.
 * 
 * Command structure:
 * /village name <name>
 * 
 * Uses CommandAPI's builder pattern to create a clean command tree with automatic
 * argument parsing, validation, and tab completion.
 */
public class VillageCommand {
    
    private final VillagesPlugin plugin;
    private final VillageManager villageManager;
    private final SignManager signManager;
    
    /**
     * Creates a new VillageCommand handler.
     * 
     * @param plugin The plugin instance
     * @param villageManager The village manager for UUID and name operations
     * @param signManager The sign manager for updating signs
     */
    public VillageCommand(VillagesPlugin plugin, VillageManager villageManager, SignManager signManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.signManager = signManager;
    }
    
    /**
     * Registers the /village command with all its subcommands.
     * 
     * This method should be called during plugin initialization (in onEnable).
     * CommandAPI handles the registration and integration with Minecraft's command system.
     */
    public void register() {
        new CommandAPICommand("village")
            .withPermission("villages.name")
            .withSubcommand(new CommandAPICommand("name")
                .withArguments(new GreedyStringArgument("name"))
                .executesPlayer((player, args) -> {
                    String name = (String) args.get("name");
                    handleNameCommand(player, name);
                }))
            .register();
    }
    
    /**
     * Handles the /village name <name> command execution.
     * 
     * This method:
     * 1. Validates the player has permission (checked by CommandAPI)
     * 2. Gets the player's current chunk
     * 3. Finds all bell blocks in that chunk
     * 4. Selects the nearest bell to the player
     * 5. Validates the bell has a UUID (should always exist)
     * 6. Sets the village name in PDC
     * 7. Updates the signs around the bell
     * 8. Sends feedback to the player
     * 
     * @param player The player executing the command
     * @param name The name to assign to the village
     */
    private void handleNameCommand(Player player, String name) {
        // Validate name is not empty
        if (name == null || name.trim().isEmpty()) {
            player.sendMessage(Component.text("Village name cannot be empty!", NamedTextColor.RED));
            return;
        }
        
        // Get the player's current chunk
        var chunk = player.getLocation().getChunk();
        
        // Find all bell blocks in the chunk
        Block nearestBell = findNearestBellInChunk(chunk, player.getLocation().getBlock());
        
        if (nearestBell == null) {
            player.sendMessage(Component.text("No village bell found in your current chunk. " +
                "Stand in a chunk that contains a village bell.", NamedTextColor.RED));
            return;
        }
        
        // Validate the bell has a UUID (should always exist, but safety check)
        UUID villageUuid = villageManager.getVillageUuid(nearestBell);
        if (villageUuid == null) {
            // This shouldn't happen, but if it does, create the UUID first
            villageUuid = villageManager.getOrCreateVillageUuid(nearestBell);
            plugin.getLogger().warning("Bell at " + nearestBell.getLocation() + 
                " had no UUID, created one: " + villageUuid);
        }
        
        // Set the village name in PDC
        villageManager.setVillageName(nearestBell, name.trim());
        
        // Update the signs around the bell to show the new name
        signManager.placeSignsAroundBell(nearestBell, villageUuid, name.trim());
        
        // Send success message to the player
        player.sendMessage(Component.text("Village named: ", NamedTextColor.GREEN)
            .append(Component.text(name.trim(), NamedTextColor.YELLOW)));
        
        plugin.getLogger().info("Player " + player.getName() + " named village at " + 
            nearestBell.getLocation() + " to: " + name.trim());
    }
    
    /**
     * Finds the nearest bell block in the given chunk to the reference block.
     * 
     * This method:
     * 1. Iterates through all blocks in the chunk
     * 2. Finds all bell blocks
     * 3. Returns the nearest one to the reference block (player's location)
     * 
     * @param chunk The chunk to search in
     * @param referenceBlock The block to measure distance from (usually player's location)
     * @return The nearest bell block, or null if none found
     */
    private Block findNearestBellInChunk(org.bukkit.Chunk chunk, Block referenceBlock) {
        Block nearestBell = null;
        double nearestDistance = Double.MAX_VALUE;
        
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        
        // Iterate through all blocks in the chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    
                    // Check if this block is a bell
                    if (block.getType() == Material.BELL) {
                        // Calculate distance from reference block
                        double distance = referenceBlock.getLocation().distance(block.getLocation());
                        
                        // Update nearest if this is closer
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestBell = block;
                        }
                    }
                }
            }
        }
        
        return nearestBell;
    }
}
