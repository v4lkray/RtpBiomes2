package dev.rtpbiomes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RtpListener implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};

    private static final class MenuHolder implements InventoryHolder {
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final RTPPlugin plugin;
    private final WorldManager worlds;
    private final NamespacedKey botKey;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public RtpListener(RTPPlugin plugin, WorldManager worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.botKey = new NamespacedKey(plugin, "rtpbot");
    }

    // ------------------------------------------------------------ menu

    public void openMenu(Player p) {
        MenuHolder h = new MenuHolder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Random Teleport - choose a biome", NamedTextColor.DARK_GRAY));
        h.inv = inv;

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        BiomeType[] all = BiomeType.values();
        for (int i = 0; i < all.length; i++) {
            BiomeType t = all[i];
            ItemStack it = new ItemStack(t.icon);
            ItemMeta m = it.getItemMeta();
            m.displayName(Component.text(t.display, t.color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(
                    Component.text("Click to teleport", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Players here: " + worlds.playerCount(t), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(m);
            inv.setItem(SLOTS[i], it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        BiomeType[] all = BiomeType.values();
        for (int i = 0; i < all.length; i++) {
            if (e.getRawSlot() == SLOTS[i]) {
                p.closeInventory();
                if (cooldown(p)) worlds.teleport(p, all[i]);
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastUse.remove(e.getPlayer().getUniqueId());
    }

    private boolean cooldown(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 1500) return false;
        lastUse.put(p.getUniqueId(), now);
        return true;
    }

    // ------------------------------------------------------------ bots

    public boolean isBot(Entity e) {
        return e.getPersistentDataContainer().has(botKey, PersistentDataType.STRING);
    }

    private BiomeType botType(Entity e) {
        return BiomeType.byId(e.getPersistentDataContainer().get(botKey, PersistentDataType.STRING));
    }

    public void spawnBot(Player p, BiomeType t) {
        Entity e = p.getWorld().spawnEntity(p.getLocation(), t.bot);
        e.getPersistentDataContainer().set(botKey, PersistentDataType.STRING, t.id);
        e.customName(Component.text(t.display, t.color, TextDecoration.BOLD));
        e.setCustomNameVisible(true);
        e.setInvulnerable(true);
        e.setPersistent(true);
        e.setSilent(true);
        if (e instanceof LivingEntity le) {
            le.setAI(false);
            le.setRemoveWhenFarAway(false);
            le.setCollidable(false);
            le.setCanPickupItems(false);
            AttributeInstance kb = le.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
        }
        if (e instanceof Ageable a) {
            a.setAdult();
            a.setAgeLock(true);
        }
    }

    public int removeNearestBot(Player p) {
        for (Entity e : p.getNearbyEntities(4, 4, 4)) {
            if (isBot(e)) { e.remove(); return 1; }
        }
        return 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!isBot(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        BiomeType t = botType(e.getRightClicked());
        Player p = e.getPlayer();
        if (t != null && cooldown(p)) worlds.teleport(p, t);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!isBot(e.getEntity())) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p) {
            BiomeType t = botType(e.getEntity());
            if (t != null && cooldown(p)) worlds.teleport(p, t);
        }
    }

    // ------------------------------------------------------------ rtp world rules

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent e) {
        if (worlds.isRtpWorld(e.getLocation().getWorld())) e.setCancelled(true); // ZERO mobs
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (worlds.isRtpWorld(e.getFrom().getWorld())) e.setCancelled(true); // keep the worlds isolated
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent e) {
        if (worlds.isRtpWorld(e.getFrom().getWorld())) e.setCancelled(true);
    }
}
