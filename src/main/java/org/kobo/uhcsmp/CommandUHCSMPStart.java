package org.kobo.uhcsmp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandUHCSMPStart implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("uhcsmp.command.uhcsmpstart")) {
            sender.sendMessage("You don't have permission to use this command!");
            return true;
        }

        if ("uhcsmp".equals(command.getName()) && args.length > 0 && "start".equals(args[0])) {
            if (sender instanceof Player && UHCSMP.getAllowRespawnEvents()) {
                if (UHCSMP.teleportAllPlayersToUhc()) {
                    // Message broadcasting is handled within the teleportAllPlayersToUhc method
                } else {
                    sender.sendMessage("Error: A UHC world is already being processed! Wait to get teleported or try again later.");
                }
                return true;
            }
        } else {
            sender.sendMessage("Usage: /uhcsmp start");
        }
        return false;
    }
}