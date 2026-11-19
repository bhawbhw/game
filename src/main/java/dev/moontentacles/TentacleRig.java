package dev.moontentacles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The tentacles of one player. Every segment is a BlockDisplay stretched and rotated
 * to join two points on a curve. All segments sit at the player's feet and are positioned
 * purely through their transformation, so the whole rig moves with the player and every
 * tick only the curve is recomputed.
 */
final class TentacleRig {

    private static final String TAG = "moontentacles";

    /** One model pixel of the player in blocks (1.8 blocks over a 32 pixel model). */
    private static final double PX = 0.05625;

    /** Attachment points on the back, in model pixels: left/right, up, back. */
    private static final double[][] ANCHORS = {
            {-3, 22, 0}, {3, 22, 0},
            {-2.5, 17, 0}, {2.5, 17, 0},
            {-1, 25, 0}, {1, 25, 0},
            {-3.5, 13, 0}, {3.5, 13, 0}
    };

    /** Starting direction of each tentacle in local space: left, up, forward. */
    private static final double[][] DIRS = {
            {-0.8, 0.5, -0.7}, {0.8, 0.5, -0.7},
            {-0.9, 0.1, -0.6}, {0.9, 0.1, -0.6},
            {-0.35, 0.9, -0.5}, {0.35, 0.9, -0.5},
            {-0.7, -0.3, -0.7}, {0.7, -0.3, -0.7}
    };

    private final Plugin plugin;
    private final Settings s;
    private final Player player;
    private final World world;
    private final List<BlockDisplay> segs = new ArrayList<>();

    private double lastX, lastY, lastZ;
    private double segX, segY, segZ;
    private float bodyYaw;
    private final Vector3d vel = new Vector3d();
    private long ticks;

    TentacleRig(Plugin plugin, Settings settings, Player player) {
        this.plugin = plugin;
        this.s = settings;
        this.player = player;
        this.world = player.getWorld();
        Location l = player.getLocation();
        this.lastX = l.getX();
        this.lastY = l.getY();
        this.lastZ = l.getZ();
        this.bodyYaw = l.getYaw();
        spawn();
    }

    World world() {
        return world;
    }

    private void spawn() {
        Location l = player.getLocation();
        Location base = new Location(world, l.getX(), l.getY(), l.getZ(), 0f, 0f);
        segX = l.getX();
        segY = l.getY();
        segZ = l.getZ();

        final float cullW = (float) (2 * (s.length() + 1.0));
        final float cullH = (float) (s.length() + 2.5);

        for (int i = 0; i < s.tentacles(); i++) {
            for (int k = 0; k < s.segments(); k++) {
                double u = (k + 0.5) / s.segments();
                int mi = Math.min(s.materials().size() - 1, (int) (u * s.materials().size()));
                Material mat = s.materials().get(mi);
                boolean glow = u >= s.glowFrom();

                BlockDisplay d = world.spawn(base, BlockDisplay.class, e -> {
                    e.setBlock(mat.createBlockData());
                    e.setPersistent(false);
                    e.addScoreboardTag(TAG);
                    e.setInterpolationDuration(s.interpolation());
                    e.setTeleportDuration(s.interpolation());
                    e.setViewRange(s.viewRange());
                    e.setDisplayWidth(cullW);
                    e.setDisplayHeight(cullH);
                    e.setShadowRadius(0f);
                    if (glow) {
                        e.setBrightness(new Display.Brightness(15, 15));
                    }
                    e.setTransformation(new Transformation(
                            new Vector3f(), new Quaternionf(), new Vector3f(0f, 0f, 0f), new Quaternionf()));
                });
                if (s.hideFromOwner()) {
                    player.hideEntity(plugin, d);
                }
                segs.add(d);
            }
        }
    }

    void remove() {
        for (Entity e : segs) {
            e.remove();
        }
        segs.clear();
    }

    private static float wrap(float a) {
        a = (a + 180f) % 360f;
        if (a < 0) a += 360f;
        return a - 180f;
    }

    private static double clamp(double v, double lim) {
        return Math.max(-lim, Math.min(lim, v));
    }

    void update() {
        if (segs.isEmpty() || !segs.get(0).isValid()) {
            remove();
            spawn();
        }

        Location loc = player.getLocation();
        ticks++;
        double t = ticks / 20.0;

        double dx = loc.getX() - lastX;
        double dy = loc.getY() - lastY;
        double dz = loc.getZ() - lastZ;
        lastX = loc.getX();
        lastY = loc.getY();
        lastZ = loc.getZ();

        // Approximate vanilla body yaw: follows the head while moving, trails it by up to 50 degrees when still.
        float diff = wrap(loc.getYaw() - bodyYaw);
        if (Math.hypot(dx, dz) > 0.02) {
            bodyYaw += diff * 0.35f;
        } else if (Math.abs(diff) > 50f) {
            bodyYaw += diff - Math.signum(diff) * 50f;
        }
        bodyYaw = wrap(bodyYaw);

        double yawR = Math.toRadians(bodyYaw);
        double c = Math.cos(yawR);
        double sn = Math.sin(yawR);
        // left = (c, 0, sn), forward = (-sn, 0, c)
        double vLeft = clamp(dx * c + dz * sn, 0.5);
        double vFwd = clamp(-dx * sn + dz * c, 0.5);
        double vUp = clamp(dy, 0.5);
        vel.x += (vLeft - vel.x) * 0.25;
        vel.y += (vUp - vel.y) * 0.25;
        vel.z += (vFwd - vel.z) * 0.25;

        boolean sneaking = player.isSneaking();
        double offY = sneaking ? -0.25 : 0.0;
        double offZ = sneaking ? 0.2 : 0.0;

        int n = s.segments();
        double seg = s.length() / n;
        double k36 = 36.0 / n;
        double sway = s.sway();
        double drag = s.inertia();

        // keep all segments at the player's feet
        if (Math.abs(loc.getX() - segX) > 1.0e-4 || Math.abs(loc.getY() - segY) > 1.0e-4
                || Math.abs(loc.getZ() - segZ) > 1.0e-4) {
            Location base = new Location(world, loc.getX(), loc.getY(), loc.getZ(), 0f, 0f);
            for (BlockDisplay d : segs) {
                d.teleport(base);
            }
            segX = loc.getX();
            segY = loc.getY();
            segZ = loc.getZ();
        }

        Vector3d[] pts = new Vector3d[n + 1];
        for (int i = 0; i < s.tentacles(); i++) {
            double[] a = ANCHORS[i];
            double[] dd = DIRS[i];
            double side = a[0] < 0 ? -1 : 1;
            double phase = i * 1.7;

            // local space: x = left, y = up, z = forward
            Vector3d p = new Vector3d(a[0] * PX, a[1] * PX + offY, -0.09 + offZ);
            Vector3d d = new Vector3d(dd[0], dd[1], dd[2]).normalize();

            for (int j = 0; j <= n; j++) {
                double u = (double) j / n;
                pts[j] = toWorldRelative(p, c, sn);

                d.y -= 0.045 * (0.3 + u * 1.4) * k36;
                d.x += side * 0.012 * k36;
                d.x += Math.sin(t * 2.2 - u * 5 + phase) * 0.05 * sway * u * k36;
                d.z += Math.cos(t * 1.9 - u * 4 + phase) * 0.05 * sway * u * k36;
                d.y += Math.sin(t * 1.7 - u * 4 + phase) * 0.025 * sway * u * k36;
                d.x -= vel.x * drag * u * k36;
                d.y -= vel.y * drag * u * 0.5 * k36;
                d.z -= vel.z * drag * u * k36;
                d.normalize();
                p.fma(seg, d);
            }

            for (int k = 0; k < n; k++) {
                Vector3d from = pts[k];
                Vector3d to = pts[k + 1];
                double dirX = to.x - from.x;
                double dirY = to.y - from.y;
                double dirZ = to.z - from.z;
                double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                if (len < 1.0e-6) {
                    continue;
                }
                double u = (k + 0.5) / n;
                double radius = s.thickness() * (1.0 - u * 0.82) + 0.012;
                double stretched = len * 1.18;

                Vector3f dir = new Vector3f((float) (dirX / len), (float) (dirY / len), (float) (dirZ / len));
                Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), dir);

                Vector3f centreLocal = new Vector3f((float) radius, (float) (stretched / 2.0), (float) radius).rotate(q);
                Vector3f translation = new Vector3f(
                        (float) ((from.x + to.x) / 2.0) - centreLocal.x,
                        (float) ((from.y + to.y) / 2.0) - centreLocal.y,
                        (float) ((from.z + to.z) / 2.0) - centreLocal.z);
                Vector3f scale = new Vector3f((float) (radius * 2.0), (float) stretched, (float) (radius * 2.0));

                BlockDisplay bd = segs.get(i * n + k);
                bd.setTransformation(new Transformation(translation, q, scale, new Quaternionf()));
                bd.setInterpolationDelay(0);
            }
        }
    }

    /** Local (left, up, forward) offset to a world-axis offset from the player's feet. */
    private static Vector3d toWorldRelative(Vector3d local, double c, double sn) {
        double wx = local.x * c + local.z * -sn;
        double wz = local.x * sn + local.z * c;
        return new Vector3d(wx, local.y, wz);
    }
}
