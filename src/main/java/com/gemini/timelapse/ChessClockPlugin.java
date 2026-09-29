package com.gemini.timelapse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class ChessClockPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Location button1Loc;
    private Location button2Loc;
    private TextDisplay display1;
    private TextDisplay display2;

    private boolean gameActive = false;
    private int defaultTime = 300; 
    private int player1Time;
    private int player2Time;
    
    private int activeTurn = 0; 
    private BukkitRunnable clockTask;
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("chessclock").setExecutor(this);
        getLogger().info("ChessClock loaded. Foam-noodle weapons equipped.");
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
            case "time":
                if (args.length < 2) {
                    player.sendMessage("Usage: /chessclock time <seconds>");
                    return true;
                }
                try {
                    defaultTime = Integer.parseInt(args[1]);
                    player.sendMessage("Match time updated to " + defaultTime + " seconds.");
                } catch (NumberFormatException e) {
                    player.sendMessage("Please provide a valid number.");
                }
                break;
            case "start":
                if (button1Loc == null || button2Loc == null) {
                    player.sendMessage("Set both buttons first!");
                    return true;
                }
                startGame();
                player.sendMessage("The clocks are ticking. Good luck.");
                break;
            case "stop":
                stopGame();
                player.sendMessage("Game aborted.");
                break;
            default:
                player.sendMessage("Unknown command.");
                break;
        }
        return true;
    }

    private void startGame() {
        stopGame(); 
        
        player1Time = defaultTime;
        player2Time = defaultTime;
        activeTurn = 1;
        gameActive = true;

        display1 = spawnDisplay(button1Loc, "<blue>Waiting...</blue>");
        display2 = spawnDisplay(button2Loc, "<red>Waiting...</red>");
        
        updateDisplay(display1, player1Time, "blue");
        updateDisplay(display2, player2Time, "red");

        // Equip all online players with the customized tools
        for (Player p : getServer().getOnlinePlayers()) {
            p.getInventory().addItem(createArenaTool(Material.IRON_PICKAXE));
            p.getInventory().addItem(createArenaTool(Material.IRON_AXE));
            p.getInventory().addItem(createArenaTool(Material.IRON_SHOVEL));
        }

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
    
    private ItemStack createArenaTool(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
            meta.addEnchant(Enchantment.FORTUNE, 3, true);

            // Create keys for our custom attribute modifiers
            NamespacedKey damageKey = new NamespacedKey(this, material.name().toLowerCase() + "_dmg");
            NamespacedKey speedKey = new NamespacedKey(this, material.name().toLowerCase() + "_spd");

            // Adding 0 bonus damage leaves the player with their base fist damage of 1.
            AttributeModifier damageMod = new AttributeModifier(damageKey, 0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
            // Adding 100 to attack speed entirely removes the swing cooldown.
            AttributeModifier speedMod = new AttributeModifier(speedKey, 100.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);

            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageMod);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, speedMod);

            item.setItemMeta(meta);
        }
        return item;
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
        Location spawnLoc = loc.clone().add(0.5, 2.0, 0.5); 
        TextDisplay display = loc.getWorld().spawn(spawnLoc, TextDisplay.class);
        
        display.text(mm.deserialize(initialText));
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        
        display.setTransformation(new Transformation(
                new Vector3f(),
                new AxisAngle4f(),
                new Vector3f(3.0f, 3.0f, 3.0f),
                new AxisAngle4f()
        ));
        
        return display;
    }

    private void updateDisplay(TextDisplay display, int timeRemaining, String color) {
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);
        display.text(mm.deserialize("<" + color + "><b>" + timeStr + "</b></" + color + ">")); 
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
