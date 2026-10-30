package dev.rtpbiomes;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

public enum BiomeType {
    PLAINS("plains", "Plains", Biome.PLAINS, Material.GRASS_BLOCK, EntityType.HORSE, NamedTextColor.GREEN),
    DESERT("desert", "Desert", Biome.DESERT, Material.SAND, EntityType.CAMEL, NamedTextColor.YELLOW),
    BADLANDS("badlands", "Badlands", Biome.BADLANDS, Material.RED_SAND, EntityType.ARMADILLO, NamedTextColor.GOLD),
    SNOW("snow", "Snow", Biome.SNOWY_PLAINS, Material.SNOW_BLOCK, EntityType.POLAR_BEAR, NamedTextColor.WHITE),
    MUSHROOM("mushroom", "Mushroom", Biome.MUSHROOM_FIELDS, Material.RED_MUSHROOM_BLOCK, EntityType.MOOSHROOM, NamedTextColor.RED),
    CHERRY("cherry", "Cherry Blossom", Biome.CHERRY_GROVE, Material.CHERRY_LEAVES, EntityType.PIG, NamedTextColor.LIGHT_PURPLE);

    public final String id;
    public final String display;
    public final Biome biome;
    public final Material icon;
    public final EntityType bot;
    public final NamedTextColor color;

    BiomeType(String id, String display, Biome biome, Material icon, EntityType bot, NamedTextColor color) {
        this.id = id;
        this.display = display;
        this.biome = biome;
        this.icon = icon;
        this.bot = bot;
        this.color = color;
    }

    public static BiomeType byId(String s) {
        if (s == null) return null;
        for (BiomeType t : values()) {
            if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
