package com.gemini.timelapse;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class TimeLapsePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey watchKey;

    @Override
    public void onEnable() {
        // We use PDC to track our custom items safely
        watchKey = new NamespacedKey(this, "timelapse_item");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("timelapse").setExecutor(this);
        
        getLogger().info("TimeLapse is alive! Time is now yours to command.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            ItemStack watch = new ItemStack(Material.CLOCK);
            ItemMeta meta = watch.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Chronos Watch");
            
            // Tagging the item internally
            meta.getPersistentDataContainer().set(watchKey, PersistentDataType.STRING, "chronos_watch");
            watch.setItemMeta(meta);
            
            player.getInventory().addItem(watch);
            player.sendMessage(ChatColor.GRAY + "You have received the " + ChatColor.LIGHT_PURPLE + "Chronos Watch" + ChatColor.GRAY + ".");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        
        // Verify this is actually our custom watch, not just any old clock
        String customId = item.getItemMeta().getPersistentDataContainer().get(watchKey, PersistentDataType.STRING);
        if ("chronos_watch".equals(customId)) {
            event.setCancelled(true);
            activateChronosWatch(event.getPlayer());
        }
    }

    private void activateChronosWatch(Player player) {
        Location loc = player.getLocation();
        
        // The heavy clock tick sound
        player.getWorld().playSound(loc, Sound.BLOCK_BELL_RESONATE, 2.0f, 0.5f);
        
        // Creating the expanding temporal shockwave visual
        new BukkitRunnable() {
            double radius = 0.5;
            @Override
            public void run() {
                if (radius > 10.0) {
                    this.cancel();
                    return;
                }
                for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / 10) {
                    for (double phi = 0; phi <= Math.PI; phi += Math.PI / 10) {
                        double x = radius * Math.cos(theta) * Math.sin(phi);
                        double y = radius * Math.cos(phi) + 1;
                        double z = radius * Math.sin(theta) * Math.sin(phi);
                        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(x, y, z), 1, 0, 0, 0, 0);
                    }
                }
                radius += 1.5;
            }
        }.runTaskTimer(this, 0L, 1L);

        // Freeze entities in a 10 block radius
        for (Entity entity : player.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
            if (entity.equals(player)) continue; // Don't freeze yourself!
            
            if (entity instanceof LivingEntity living) {
                // Pin them to the floor with extreme slowness and negative jump boost
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255, false, false, false));
                living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 250, false, false, false)); 
                
                // If it's a mob, lobotomize them temporarily using Paper's API
                if (living instanceof Mob mob) {
                    mob.setAware(false);
                    
                    // Restore their awareness after 5 seconds (100 ticks)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!mob.isDead()) {
                                mob.setAware(true);
                            }
                        }
                    }.runTaskLater(this, 100L);
                }
            }
        }
        
        player.sendMessage(ChatColor.DARK_PURPLE + "Time fractures around you.");
    }
}
