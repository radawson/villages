# Command System Guide (Fongi)

This document explains how to add new commands to the Villages plugin using CommandAPI and the underlying Brigadier command system.

## Overview

The Villages plugin uses **CommandAPI** (by Skepter) to create commands. CommandAPI is a stable, well-maintained library that simplifies working with Minecraft's Brigadier command system. It provides:

- Clean, type-safe command building
- Automatic argument parsing and validation
- Built-in tab completion
- Better error handling
- Active maintenance and Paper compatibility

## Understanding Brigadier

Brigadier is Minecraft's command parsing and execution framework. It uses a tree structure where commands are built from nodes:

### Core Concepts

1. **CommandNode**: The building block - represents a part of the command
   - `LiteralArgumentBuilder`: For literal strings like "village" or "name"
   - `RequiredArgumentBuilder`: For arguments that require input like `<name>` (StringArgumentType)
   - `OptionalArgumentBuilder`: For optional arguments

2. **Command Tree Structure**: Commands are built as a tree
   ```
   /village name <name>
   ```
   Becomes:
   ```
   root (Literal "village")
     └── Literal "name"
         └── Argument <name> (String)
             └── Executes command handler
   ```

3. **Argument Types**: Various types available
   - `StringArgumentType.word()`: Single word (no spaces)
   - `StringArgumentType.string()`: Quoted string (allows spaces)
   - `StringArgumentType.greedyString()`: Rest of input (allows spaces, no quotes needed)
   - `IntegerArgumentType.integer()`: Integer numbers
   - `DoubleArgumentType.doubleArg()`: Decimal numbers
   - `BoolArgumentType.bool()`: true/false
   - Custom types via `ArgumentType`

4. **Command Execution**: 
   - `CommandSource`: The sender (Player, Console, CommandBlock, etc.)
   - `CommandContext<T>`: Contains parsed arguments and source
   - `getArgument(name, type)`: Retrieves parsed argument values

5. **Suggestions**: Brigadier provides tab completion automatically based on the tree structure

## Using CommandAPI

CommandAPI wraps Brigadier with a cleaner, more intuitive API. Instead of working directly with Brigadier's verbose builders, you use CommandAPI's fluent builder pattern.

### Basic Command Structure

```java
new CommandAPICommand("commandname")
    .withPermission("permission.node")
    .withArguments(new GreedyStringArgument("argumentName"))
    .executesPlayer((player, args) -> {
        String value = (String) args.get("argumentName");
        // Handle command
    })
    .register();
```

### Current Implementation

The `/village name <name>` command is implemented in `VillageCommand.java`:

```java
new CommandAPICommand("village")
    .withPermission("villages.name")
    .withSubcommand(new CommandAPICommand("name")
        .withArguments(new GreedyStringArgument("name"))
        .executesPlayer((player, args) -> {
            String name = (String) args.get("name");
            handleNameCommand(player, name);
        }))
    .register();
```

### Adding New Subcommands

To add a new subcommand like `/village info`, you would add it to the root command:

```java
new CommandAPICommand("village")
    .withPermission("villages.name")
    .withSubcommand(new CommandAPICommand("name")
        .withArguments(new GreedyStringArgument("name"))
        .executesPlayer((player, args) -> {
            // Handle name command
        }))
    .withSubcommand(new CommandAPICommand("info")
        .executesPlayer((player, args) -> {
            // Handle info command
        }))
    .register();
```

### Common Argument Types in CommandAPI

CommandAPI provides argument classes that map to Brigadier's argument types:

- `GreedyStringArgument("name")` - Rest of input (allows spaces, no quotes needed)
- `StringArgument("name")` - Single word or quoted string
- `IntegerArgument("number")` - Integer numbers
- `DoubleArgument("number")` - Decimal numbers
- `BooleanArgument("flag")` - true/false
- `PlayerArgument("player")` - Player selector
- `LocationArgument("location")` - Coordinates or ~ notation

### Executors

CommandAPI provides different executors for different command sources:

- `.executesPlayer((player, args) -> { ... })` - Only players can execute
- `.executesConsole((sender, args) -> { ... })` - Only console can execute
- `.executesCommandBlock((block, args) -> { ... })` - Only command blocks can execute
- `.executes((sender, args) -> { ... })` - Any source can execute

### Permission Handling

Permissions can be set at any level:

```java
new CommandAPICommand("village")
    .withPermission("villages.use")  // Permission for root command
    .withSubcommand(new CommandAPICommand("name")
        .withPermission("villages.name")  // Permission for subcommand
        .executesPlayer(...))
    .register();
```

### Argument Suggestions

You can provide custom suggestions for arguments:

```java
new CommandAPICommand("village")
    .withSubcommand(new CommandAPICommand("info")
        .withArguments(new StringArgument("villageName")
            .replaceSuggestions(ArgumentSuggestions.strings(
                info -> new String[]{"Village1", "Village2", "Village3"}
            )))
        .executesPlayer(...))
    .register();
```

## Examples

### Example 1: Simple Command with One Argument

```java
new CommandAPICommand("village")
    .withSubcommand(new CommandAPICommand("list")
        .executesPlayer((player, args) -> {
            // List all villages
            player.sendMessage("Villages: ...");
        }))
    .register();
```

### Example 2: Command with Multiple Arguments

```java
new CommandAPICommand("village")
    .withSubcommand(new CommandAPICommand("rename")
        .withArguments(new GreedyStringArgument("oldName"))
        .withArguments(new GreedyStringArgument("newName"))
        .executesPlayer((player, args) -> {
            String oldName = (String) args.get("oldName");
            String newName = (String) args.get("newName");
            // Handle rename
        }))
    .register();
```

### Example 3: Command with Integer Argument

```java
new CommandAPICommand("village")
    .withSubcommand(new CommandAPICommand("setradius")
        .withArguments(new IntegerArgument("radius"))
        .executesPlayer((player, args) -> {
            int radius = (Integer) args.get("radius");
            // Handle radius setting
        }))
    .register();
```

## Best Practices

1. **Keep command logic separate**: Put command handlers in separate methods (like `handleNameCommand`)
2. **Validate inputs**: Always validate user input before processing (blank names can trigger regeneration)
3. **Provide feedback**: Send clear messages to players about success/failure
4. **Use appropriate argument types**: Choose the right argument type for your use case
5. **Set permissions**: Always set appropriate permissions for commands
6. **Handle errors gracefully**: Catch exceptions and provide user-friendly error messages

## CommandAPI Documentation

For more detailed information, see the official CommandAPI documentation:
- [CommandAPI Documentation](https://commandapi.jorel.dev/)
- [CommandAPI GitHub](https://github.com/JorelAli/CommandAPI)

## Current Commands Reference

As of version 0.2.1, the plugin includes:

### Basic Commands
- `/village name <name>` - Name a village (blank regenerates a new name)
- `/village info` - Show plugin and village info

### Border Commands
- `/village border show [duration]` - Visualize boundary with particles
- `/village border recalculate` - Force boundary recalculation

### Entrance Commands
- `/village entrance add` - Mark entrance at current location
- `/village entrance remove` - Remove nearest entrance
- `/village entrance list` - List all entrances
- `/village entrance detect` - Run automatic detection

### Region Commands
- `/village region create` - Create WorldGuard region
- `/village region delete` - Delete village region
- `/village region flags [flag] [value]` - View/set flags

### Admin Commands
- `/village reload` - Reload configuration
- `/village migrate <from> <to>` - Migrate storage
- `/village backup` - Create data backup

### Debug Commands
- `/village debug` - Show debug status
- `/village debug on/off` - Toggle debug mode
- `/village debug storage` - Toggle storage logging
- `/village debug regions` - Toggle region logging
- `/village debug boundaries` - Toggle boundary logging
- `/village debug entrances` - Toggle entrance logging

## Example: Debug Command Implementation

The debug commands show how to build toggle commands with subcommands:

```java
private CommandAPICommand debugCommand() {
    return new CommandAPICommand("debug")
        .withPermission("villages.admin.debug")
        .executes((sender, args) -> {
            handleDebugStatusCommand(sender);  // Show status
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

private CommandAPICommand debugStorageCommand() {
    return new CommandAPICommand("debug")
        .withSubcommand(new CommandAPICommand("storage")
            .withPermission("villages.admin.debug")
            .executes((sender, args) -> {
                handleDebugCategoryToggle(sender, "storage");
            }));
}
```

This pattern allows `/village debug` to show status, while `/village debug on` and `/village debug storage` are subcommands.

## Future Command Ideas

- `/village list` - List all named villages
- `/village rename <old> <new>` - Rename a village  
- `/village teleport <name>` - Teleport to a named village
- `/village debug verbose` - Toggle verbose mode

Each of these can be added as a new `.withSubcommand()` call on the root command.
