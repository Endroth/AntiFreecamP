package br.antifreecam;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AntiFreecam extends JavaPlugin implements Listener {

    private int interval;
    private double maxDistance;
    private double nearDistance;
    private double scanRadius;
    private boolean hidePlayers;
    private boolean hideMobs;
    private boolean hideItems;

    // Para cada jogador, as entidades que estao escondidas dele agora
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        interval = Math.max(1, getConfig().getInt("interval-ticks", 5));
        maxDistance = getConfig().getDouble("max-distance", 64);
        nearDistance = getConfig().getDouble("near-distance", 8);
        scanRadius = Math.max(maxDistance, getConfig().getDouble("scan-radius", 96));
        hidePlayers = getConfig().getBoolean("hide-players", true);
        hideMobs = getConfig().getBoolean("hide-mobs", true);
        hideItems = getConfig().getBoolean("hide-items", false);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, interval);
    }

    @Override
    public void onDisable() {
        // Mostra tudo de volta ao desligar
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<UUID> set = hidden.get(p.getUniqueId());
            if (set == null) continue;
            for (UUID id : set) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) p.showEntity(this, e);
            }
        }
        hidden.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hidden.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<UUID> set = hidden.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());

            if (p.hasPermission("antifreecam.bypass")) {
                showAll(p, set);
                continue;
            }

            // Limpa entidades que nao existem mais
            Iterator<UUID> it = set.iterator();
            while (it.hasNext()) {
                Entity e = Bukkit.getEntity(it.next());
                if (e == null || !e.isValid()) it.remove();
            }

            Location eye = p.getEyeLocation();
            for (Entity e : p.getNearbyEntities(scanRadius, scanRadius, scanRadius)) {
                if (!shouldCheck(e)) continue;

                boolean visible = canSee(eye, e);
                boolean isHidden = set.contains(e.getUniqueId());

                if (!visible && !isHidden) {
                    p.hideEntity(this, e);
                    set.add(e.getUniqueId());
                } else if (visible && isHidden) {
                    p.showEntity(this, e);
                    set.remove(e.getUniqueId());
                }
            }
        }
    }

    private void showAll(Player p, Set<UUID> set) {
        if (set.isEmpty()) return;
        for (UUID id : set) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) p.showEntity(this, e);
        }
        set.clear();
    }

    private boolean shouldCheck(Entity e) {
        if (e instanceof Player) return hidePlayers;
        if (e instanceof LivingEntity) return hideMobs;
        if (e instanceof Item) return hideItems;
        return false;
    }

    private boolean canSee(Location eye, Entity target) {
        if (!eye.getWorld().equals(target.getWorld())) return false;

        double dist = eye.distance(target.getLocation());
        if (dist <= nearDistance) return true;
        if (dist > maxDistance) return false;

        Location base = target.getLocation();
        double height = target.getHeight();

        // Testa 3 pontos do corpo (pes, meio, cabeca) para nao esconder quem esta parcialmente visivel
        double[] offsets = { 0.1, height * 0.5, height * 0.9 };
        for (double off : offsets) {
            Vector to = new Vector(base.getX(), base.getY() + off, base.getZ()).subtract(eye.toVector());
            double len = to.length();
            if (len < 0.001) return true;

            RayTraceResult hit = eye.getWorld().rayTraceBlocks(
                    eye, to.normalize(), len, FluidCollisionMode.NEVER, true);
            if (hit == null) return true; // nenhum bloco no caminho
        }
        return false;
    }
}
