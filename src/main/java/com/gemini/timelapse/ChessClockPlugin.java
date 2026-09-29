package com.gemini.timelapse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ChessClockPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Location button1Loc;
    private Location button2Loc;
    private TextDisplay display1;
    private TextDisplay display2;

    private boolean gameActive = false;
    private int player1Time = 300; // 5 minutes in seconds
    private int player2Time = 300;
    
    // 1 = Player 1's turn to act (their clock is ticking)
    private int activeTurn = 0; 
    private BukkitRunnable clockTask;
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("chessclock").setExecutor(this);
        getLogger().info("ChessClock loaded. Ready to induce anxiety.");
    }

    @Override
    public void onDisable() {
        stopGame(); 
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) return false;

        Block target = player.getTargetBlockExact(5);

        switch (args[0].toLowerCase()) {
            case "set1":
                if (target != null && target.getType().toString().contains("BUTTON")) {
                    button1Loc = target.getLocation();
                    player.sendMessage("Button 1 registered.");
                } else {
                    player.sendMessage("You must be looking at a button!");
                }
                break;
            case "set2":
                if (target != null && target.getType().toString().contains("BUTTON")) {
                    button2Loc = target.getLocation();
                    player.sendMessage("Button 2 registered.");
                } else {
                    player.sendMessage("You must be looking at a button!");
                }
                break;
            case "start":
                if (button1Loc == null || button2Loc == null) {
                    player.sendMessage("Set both buttons first!");
                    return true;
                }
                startGame();
                player.sendMessage("The clocks are ticking...");
                break;
            case "stop":
                stopGame();
                player.sendMessage("Game aborted.");
                break;
        }
        return true;
    }

    private void startGame() {
        stopGame(); 
        
        player1Time = 300;
        player2Time = 300;
        activeTurn = 1;
        gameActive = true;

        display1 = spawnDisplay(button1Loc, "<blue>05:00</blue>");
        display2 = spawnDisplay(button2Loc, "<red>05:00</red>");

        clockTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    this.cancel();
                    return;
                }

                if (activeTurn == 1) {
                    player1Time--;
                    updateDisplay(display1, player1Time, "blue");
                    if (player1Time <= 0) endGame(1);
                } else if (activeTurn == 2) {
                    player2Time--;
                    updateDisplay(display2, player2Time, "red");
                    if (player2Time <= 0) endGame(2);
                }
            }
        };
        clockTask.runTaskTimer(this, 20L, 20L); 
    }

    private void stopGame() {
        gameActive = false;
        if (clockTask != null) clockTask.cancel();
        if (display1 != null) display1.remove();
        if (display2 != null) display2.remove();
    }

    private void endGame(int loser) {
        stopGame();
        getServer().broadcast(mm.deserialize("<gold><b>Player " + loser + "'s clock hit zero. Game Over.</b></gold>"));
    }

    private TextDisplay spawnDisplay(Location loc, String initialText) {
        Location spawnLoc = loc.clone().add(0.5, 1.5, 0.5);
        TextDisplay display = loc.getWorld().spawn(spawnLoc, TextDisplay.class);
        display.text(mm.deserialize(initialText));
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        return display;
    }

    private void updateDisplay(TextDisplay display, int timeRemaining, String color) {
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);
        display.text(mm.deserialize("<" + color + ">" + timeStr + "</" + color + ">"));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!gameActive || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (clicked.getLocation().equals(button1Loc)) {
            activeTurn = 2;
        } else if (clicked.getLocation().equals(button2Loc)) {
            activeTurn = 1;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (button1Loc == null || button2Loc == null) return;

        Block broken = event.getBlock();
        
        if (broken.getLocation().equals(button1Loc) || broken.getLocation().equals(button2Loc)) {
            event.setCancelled(true);
            return;
        }

        Block attachedTo1 = button1Loc.getBlock().getRelative(BlockFace.DOWN); 
        Block attachedTo2 = button2Loc.getBlock().getRelative(BlockFace.DOWN);

        if (broken.getLocation().equals(attachedTo1.getLocation()) || broken.getLocation().equals(attachedTo2.getLocation())) {
            event.setCancelled(true);
        }
    }
}
