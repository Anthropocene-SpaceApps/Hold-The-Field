package org.anthropocene.htf.world;

import org.joml.Vector3f;

/** First-person body: walking with gravity, wading, swimming and a free-fly mode. Metric units. */
public final class Player {
    public static final float EYE = 1.65f, RADIUS = 0.35f, STEP = 0.6f;
    private static final float GRAVITY = 22f, JUMP = 6.2f;

    public final Vector3f pos = new Vector3f(0, 0, 40);          // feet
    public final Vector3f vel = new Vector3f();
    public float yaw, pitch = -0.05f;
    public boolean flying, onGround, swimming, sprinting;
    public float walkedDistance;

    /** What the body asks of the world. */
    public interface Collider {
        double groundHeight(double x, double z);
        boolean blocked(double x, double z, double radius, double feetY);
        double waterDepth(double x, double z);
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

    public void update(float dt, Collider w, float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
        dt = Math.min(dt, 0.05f);
        double depth = w.waterDepth(pos.x, pos.z);
        swimming = depth > 1.25 && !flying;
        sprinting = sprint && forward > 0 && !swimming;
        float wade = (float) (1 - Math.min(depth, 1.1) * 0.38);

        float speed = flying ? (sprint ? 28f : 14f) : swimming ? 1.8f : sprinting ? 6.2f * wade : sneak ? 1.5f : 3.5f * wade;
        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        float wx = -sin * forward + cos * strafe, wz = -cos * forward - sin * strafe;
        float len = (float) Math.hypot(wx, wz);
        if (len > 1) { wx /= len; wz /= len; }
        float accel = onGround || flying || swimming ? 12f : 3f;
        vel.x += (wx * speed - vel.x) * Math.min(1, accel * dt);
        vel.z += (wz * speed - vel.z) * Math.min(1, accel * dt);

        double groundHere = w.groundHeight(pos.x, pos.z);
        if (flying) {
            float ty = (jump ? 1 : 0) - (sneak ? 1 : 0);
            vel.y += (ty * (sprint ? 20f : 10f) - vel.y) * Math.min(1, 8 * dt);
        } else if (swimming) {
            double surface = groundHere + depth;
            float target = jump ? 2.2f : sneak ? -2.2f : (float) ((surface - 1.15 - pos.y) * 1.8);
            vel.y += (target - vel.y) * Math.min(1, 5 * dt);
        } else {
            vel.y -= GRAVITY * dt;
            if (jump && onGround) { vel.y = JUMP; onGround = false; }
            vel.y = Math.max(vel.y, -40f);
        }

        float nx = pos.x + vel.x * dt, nz = pos.z + vel.z * dt;
        if (flying) { pos.x = nx; pos.z = nz; }
        else {
            if (!w.blocked(nx, pos.z, RADIUS, pos.y)) pos.x = nx; else vel.x = 0;
            if (!w.blocked(pos.x, nz, RADIUS, pos.y)) pos.z = nz; else vel.z = 0;
        }
        pos.y += vel.y * dt;

        double ground = w.groundHeight(pos.x, pos.z);
        onGround = false;
        if (pos.y <= ground) {
            pos.y = (float) ground;
            if (vel.y < 0) vel.y = 0;
            onGround = true;
        }
        if (flying && onGround && sneak) flying = false;
        pos.x = Math.max(Landscape.X0 + 5, Math.min(Landscape.X1 - 5, pos.x));
        pos.z = Math.max(Landscape.Z0 + 5, Math.min(Landscape.Z1 - 5, pos.z));
        pos.y = Math.max(-6f, Math.min(900f, pos.y));
        if (onGround && len > 0.1f) walkedDistance += (float) Math.hypot(vel.x, vel.z) * dt;
    }

    public Vector3f eye(boolean bob) {
        float b = bob && onGround ? (float) Math.sin(walkedDistance * 2.0) * 0.03f : 0f;
        return new Vector3f(pos.x, pos.y + EYE + b, pos.z);
    }
}
