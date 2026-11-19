package dev.moontentacles;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

record Settings(
        int tentacles,
        int segments,
        double length,
        double thickness,
        double sway,
        double inertia,
        int interpolation,
        float viewRange,
        double glowFrom,
        boolean hideFromOwner,
        List<Material> materials) {

    static Settings load(FileConfiguration c, Logger log) {
        List<Material> mats = new ArrayList<>();
        for (String name : c.getStringList("materials")) {
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) {
                log.warning("Ignoring unknown block in materials: " + name);
                continue;
            }
            mats.add(m);
        }
        if (mats.isEmpty()) {
            mats.add(Material.WHITE_CONCRETE);
        }
        return new Settings(
                Math.max(1, Math.min(8, c.getInt("tentacles", 4))),
                Math.max(4, Math.min(40, c.getInt("segments", 14))),
                Math.max(0.5, c.getDouble("length", 1.75)),
                Math.max(0.02, c.getDouble("thickness", 0.09)),
                Math.max(0.0, c.getDouble("sway", 1.0)),
                Math.max(0.0, c.getDouble("inertia", 0.15)),
                Math.max(0, Math.min(59, c.getInt("interpolation-ticks", 2))),
                (float) Math.max(0.1, c.getDouble("view-range", 1.5)),
                Math.max(0.0, Math.min(1.0, c.getDouble("glow-from", 0.55))),
                c.getBoolean("hide-from-owner", false),
                List.copyOf(mats));
    }
}
