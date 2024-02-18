package org.kobo.uhcsmp;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

public final class UHCSMP extends JavaPlugin implements Listener {

    private static World latestUhcWorld;
    private boolean uhcWorldExists = false;
    private boolean killingAllPlayers = false;
    private static boolean resettingWorld = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand uhcsmpCommand = getCommand("uhcsmp");
        if (uhcsmpCommand != null) {
            uhcsmpCommand.setExecutor(new CommandUHCSMPStart());
            uhcsmpCommand.setPermission("uhcsmp.command.uhcsmpstart");
        }

        Bukkit.getServer().getScheduler().runTaskLater(this, () -> {
            checkForExistingWorlds();
            createUhcWorldIfNeeded();
        }, 100);
    }

    private void checkForExistingWorlds() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith("uhcWorld")) {
                latestUhcWorld = world;
                uhcWorldExists = true;
            }
        }
    }

    private void createUhcWorldIfNeeded() {
        Plugin multiverseCore = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (multiverseCore != null && multiverseCore.isEnabled() && !uhcWorldExists) {
            makeUhcWorld();
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getWorld().getName().equals("world") || resettingWorld) {
            return;
        }

        if (event.getRespawnReason().equals(PlayerRespawnEvent.RespawnReason.END_PORTAL)) {
            event.setRespawnLocation(new Location(latestUhcWorld, 0, latestUhcWorld.getHighestBlockYAt(0, 0), 0));
            return;
        }

        resettingWorld = true;
        announceNewWorldCreation();
        resetWorldAfterDelay();
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        if (event.getPlayer().isDead()){
            return;
        }

        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.NETHER) {
            executeMultiverseCommand("modify", "set", "respawnWorld", "world", "world_nether");
        } else if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) {
            event.getPlayer().getWorld().setDifficulty(Difficulty.HARD);
            executeMultiverseCommand("modify", "set", "respawnWorld", "world", "world_the_end");
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent e) {
        World fromWorld = e.getFrom().getWorld();
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (fromWorld != null) {
                switch (fromWorld.getEnvironment()) {
                    case NORMAL:
                        Objects.requireNonNull(Objects.requireNonNull(e.getTo()).getWorld()).setDifficulty(Difficulty.HARD);
                        e.getTo().setWorld(e.getTo().getWorld()); // todo refactor
                        break;
                    case NETHER:
                        Location newTo = e.getFrom().multiply(1 / 8.0D);
                        newTo.setWorld(latestUhcWorld);
                        e.setTo(newTo);
                        break;
                    default:
                }
            }
        }
    }

    private void announceNewWorldCreation() {
        Bukkit.getServer().broadcastMessage("Initializing new UHC run...");
        Bukkit.getServer().broadcastMessage("You will be teleported momentarily.");
    }

    private void resetWorldAfterDelay() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            deleteWorlds();
            createNewWorldAfterDelay();
            resettingWorld = false;
        }, 300L);
    }

    private void deleteWorlds() {
        uhcWorldExists = false;
        executeMultiverseCommand("delete", latestUhcWorld.getName());
        executeMultiverseCommand("confirm");
        deleteNetherEndSubdirectories();

        executeMultiverseCommand("unload", "world_nether");
        executeMultiverseCommand("delete", "world_nether");
        executeMultiverseCommand("confirm");

        executeMultiverseCommand("unload", "world_the_end");
        executeMultiverseCommand("delete", "world_the_end");
        executeMultiverseCommand("confirm");

        //        executeMultiverseCommand("delete", "world_the_end");
//        executeMultiverseCommand("confirm");
    }

    private void createNewWorldAfterDelay() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            makeUhcWorld();
            teleportPlayersAndSetupAfterDelay();
        }, 80L);
    }

    private void teleportPlayersAndSetupAfterDelay() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            teleportAllPlayersToUhc();
            setScoreboardObjectives();
            executeMultiverseCommand("create", " world_nether", "nether");
            executeMultiverseCommand("create", " world_the_end", "end");
        }, 100L);

    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        if (deadPlayer.getWorld().getName().equals("world")) {
            return;
        }

        killAllOtherPlayers(deadPlayer);
    }

    private void killAllOtherPlayers(Player deadPlayer) {
        if (killingAllPlayers) {
            return;
        }
        killingAllPlayers = true;

        for (Player player : latestUhcWorld.getPlayers()) {
            if (!player.equals(deadPlayer)) {
                player.setHealth(0);
            }
        }

        killingAllPlayers = false;
    }

    private static void setScoreboardObjectives() {
        // May not work consistently
        executeCommand("execute in " + latestUhcWorld.getName() + " run scoreboard objectives add health health");
        executeCommand("execute in " + latestUhcWorld.getName() + " run scoreboard objectives setdisplay list health");
    }

    private void setRespawnToWorld() {
        executeMultiverseCommand("modify", "set", "respawnWorld", "world", latestUhcWorld.getName());
    }

    private void setGameRules() {
        latestUhcWorld.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    private boolean seekAndSetUhcWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().startsWith("uhcWorld")) {
                latestUhcWorld = world;
                uhcWorldExists = true;
                latestUhcWorld.setDifficulty(Difficulty.HARD);
                return true;
            }
        }
        return false;
    }

    public void makeUhcWorld() {
        getLogger().info("Creating uhc world");
        String uhcWorldName = "uhcWorld" + System.currentTimeMillis();
        executeMultiverseCommand("create", uhcWorldName, "normal");
        if (seekAndSetUhcWorld()) {
            setRespawnToWorld();
            setGameRules();
        }
    }

    public static boolean teleportAllPlayersToUhc() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals("world")) {
                teleportPlayerToUhc(player, true);
            }
        }
        Bukkit.getServer().broadcastMessage("GLHF!");
        setScoreboardObjectives();
        return true;
    }

    private static void teleportPlayerToUhc(Player player, boolean resetPlayerState) {
        Location spawnLocation = new Location(latestUhcWorld, 0, latestUhcWorld.getHighestBlockYAt(0, 0), 0);
        player.teleport(spawnLocation);
        if (resetPlayerState){
            resetPlayerState(player);
        }
    }

    private static void resetPlayerState(Player player) {
        player.setHealth(20);
        player.setFoodLevel(20);
        player.getInventory().clear();
        player.setExp(0);
        player.setLevel(0);
        player.setGameMode(GameMode.SURVIVAL);
        player.getEnderChest().clear();
        player.setSaturation(5);
    }

    private static void executeCommand(String command) {
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
    }

    private void executeMultiverseCommand(String... args) {
        PluginCommand mvCommand = getServer().getPluginCommand("mv");
        if (mvCommand != null) {
            mvCommand.execute(Bukkit.getConsoleSender(), "mv", args);
        } else {
            getLogger().warning("The 'mv' command is not registered!");
        }
    }

    public boolean deleteNetherEndSubdirectories() {
        boolean netherDeleted = deleteWorldSubdirectory("world_nether", "DIM-1");
        boolean endDeleted = deleteWorldSubdirectory("world_the_end", "DIM1");
        return netherDeleted && endDeleted;
    }

    private boolean deleteWorldSubdirectory(String worldName, String subdirectoryName) {
        Path subdirectoryPath = Bukkit.getWorldContainer().toPath().resolve(worldName).resolve(subdirectoryName);
        try {
            if (Files.exists(subdirectoryPath)) {
                Files.walk(subdirectoryPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                getLogger().info("Successfully deleted " + subdirectoryName + " from " + worldName);
                return true;
            } else {
                getLogger().warning("Subdirectory " + subdirectoryName + " in world " + worldName + " does not exist.");
                return false;
            }
        } catch (IOException e) {
            getLogger().severe("Failed to delete subdirectory " + subdirectoryName + " from world " + worldName + ": " + e.getMessage());
            return false;
        }
    }
    public static boolean getResettingWorld(){
        return resettingWorld;
    }
}