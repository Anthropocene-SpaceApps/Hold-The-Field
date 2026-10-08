package org.anthropocene.htf.world;

import org.joml.Vector3f;

/** Walking / flying / swimming body with simple column-height collision (Minecraft-like feel). */
public final class Player {
    public static final float EYE = 1.62f, RADIUS = 0.3f, HEIGHT = 1.8f, STEP = 0.6f;
    private static final float GRAVITY = 28f, JUMP = 8.6f;

    public final Vector3f pos = new Vector3f(4f, 1f, 24f);    // feet
    public final Vector3f vel = new Vector3f();
    public float yaw, pitch = -0.12f;
    public boolean flying, onGround, swimming, sprinting;
    public float walkedDistance;

    /** What the player body asks of the world. */
    public interface Collider {
        double solidTop(int x, int z);
        double waterSurface(double x, double z);   // NaN when there is no water
    }

    public void teleport(float x, float y, float z, float yaw, float pitch) {
        pos.set(x, y, z); vel.set(0); this.yaw = yaw; this.pitch = pitch;
    }

    public void look(double dx, double dy, double sensitivity, boolean invertY) {
        double k = 0.0012 + sensitivity * 0.0044;
        yaw -= (float) (dx * k);
        pitch -= (float) (dy * k * (invertY ? -1 : 1));
        pitch = Math.max(-1.55f, Math.min(1.55f, pitch));
    }

    private double groundAt(Collider c, float x, float z) {
        double g = -64;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
            g = Math.max(g, c.solidTop((int) Math.floor(x + sx * RADIUS), (int) Math.floor(z + sz * RADIUS)));
        return g;
    }

    /** Is the body free to stand at (x, z) at its current feet height? */
    private boolean free(Collider c, float x, float z, float feetY) {
        return groundAt(c, x, z) <= feetY + STEP;
    }

    public void update(float dt, Collider world, float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
        dt = Math.min(dt, 0.05f);
        double surface = world.waterSurface(pos.x, pos.z);
        swimming = !Double.isNaN(surface) && pos.y < surface - 0.5 && !flying;
        sprinting = sprint && forward > 0 && !swimming;

        float speed = flying ? (sprint ? 20f : 10.8f) : swimming ? 2.2f : sprinting ? 5.6f : sneak ? 1.3f : 4.3f;
        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        // forward vector on the ground plane is (-sin, -cos); right is (cos, -sin)
        float wx = -sin * forward + cos * strafe, wz = -cos * forward - sin * strafe;
        float len = (float) Math.hypot(wx, wz);
        if (len > 1) { wx /= len; wz /= len; }
        float tx = wx * speed, tz = wz * speed;
        float accel = onGround || flying || swimming ? 14f : 3f;
        vel.x += (tx - vel.x) * Math.min(1, accel * dt);
        vel.z += (tz - vel.z) * Math.min(1, accel * dt);

        if (flying) {
            float ty = (jump ? 1 : 0) - (sneak ? 1 : 0);
            vel.y += (ty * (sprint ? 14f : 8f) - vel.y) * Math.min(1, 10 * dt);
        } else if (swimming) {
            vel.y += ((jump ? 3.2f : -1.2f) - vel.y) * Math.min(1, 4 * dt);
        } else {
            vel.y -= GRAVITY * dt;
            if (jump && onGround) { vel.y = JUMP; onGround = false; }
            vel.y = Math.max(vel.y, -50f);
        }

        float nx = pos.x + vel.x * dt, nz = pos.z + vel.z * dt;
        if (flying) { pos.x = nx; pos.z = nz; }
        else {
            if (free(world, nx, pos.z, pos.y)) pos.x = nx; else vel.x = 0;
            if (free(world, pos.x, nz, pos.y)) pos.z = nz; else vel.z = 0;
        }
        pos.y += vel.y * dt;

        double ground = groundAt(world, pos.x, pos.z);
        onGround = false;
        if (pos.y <= ground) {
            pos.y = (float) ground;
            if (vel.y < 0) vel.y = 0;
            onGround = true;
        }
        if (flying && onGround && sneak) flying = false;     // sneak on the ground lands you
        pos.x = Math.max(Terrain.X0 + 1, Math.min(Terrain.X1 - 2, pos.x));
        pos.z = Math.max(Terrain.Z0 + 1, Math.min(Terrain.Z1 - 2, pos.z));
        pos.y = Math.max(-4f, Math.min(90f, pos.y));
        if (onGround && len > 0.1f) walkedDistance += (float) Math.hypot(vel.x, vel.z) * dt;
    }

    public Vector3f eye(boolean bob) {
        float b = bob && onGround ? (float) Math.sin(walkedDistance * 2.2) * 0.035f : 0f;
        return new Vector3f(pos.x, pos.y + (EYE - (0)) + b, pos.z);
    }
}
