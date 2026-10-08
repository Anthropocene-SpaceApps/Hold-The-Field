package org.anthropocene.htf.world;

import org.anthropocene.htf.gfx.PropBuilder;
import org.anthropocene.htf.gfx.StaticMesh;
import org.anthropocene.htf.sim.GameState;
import org.joml.Vector3f;

import static org.anthropocene.htf.gfx.PropBuilder.*;

/** Rahim, the farmer: a built-up figure that watches the sky, waves a warning, cheers or slumps. */
public final class Rahim {
    public static final float X = 36.5f, Z = -3f;
    public enum Pose { IDLE, WARN, CHEER, SAD }

    private final PropBuilder b = new PropBuilder();
    private StaticMesh mesh;
    private final Landscape land;
    private float yaw = (float) Math.PI * -0.5f, headYaw;
    public Pose pose = Pose.IDLE;

    public Rahim(Landscape land) { this.land = land; }

    public float groundY() { return land.height(X, Z); }

    public StaticMesh mesh() { return mesh; }

    public void update(double t, double dt, GameState st, Vector3f player) {
        pose = st == null ? Pose.IDLE
                : !st.alive ? Pose.SAD
                : st.harvested && st.yieldPct > 0.4 ? Pose.CHEER
                : st.status.equals("warning") && !st.harvested ? Pose.WARN : Pose.IDLE;
        // face the player when they are near, otherwise look over his field
        float dx = player.x - X, dz = player.z - Z;
        boolean near = dx * dx + dz * dz < 30 * 30;
        float want = near ? (float) Math.atan2(dx, dz) : (float) Math.PI * -0.5f;
        float diff = (float) Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw));
        yaw += diff * (float) Math.min(1, dt * 2.5);
        build(t);
    }

    private void build(double t) {
        b.clear();
        float gy = groundY();
        float lean = pose == Pose.SAD ? 0.16f : 0.0f;
        b.translate(X, gy, Z).rotY(yaw);
        float[] skin = {0.50f, 0.35f, 0.24f};
        // legs and feet
        for (int side = -1; side <= 1; side += 2) {
            b.push().translate(side * 0.1f, 0.02f, 0).cylinder(0.055f, 0.075f, 0.86f, 10, SKIN, skin[0], skin[1], skin[2], true, 1f).pop();
            b.push().translate(side * 0.1f, 0f, 0.07f).box(0.11f, 0.05f, 0.25f, SKIN, skin[0] * 0.9f, skin[1] * 0.9f, skin[2] * 0.9f, 1f).pop();
        }
        // checked lungi
        b.push().lathe(new float[][]{{0.30f, 0.265f}, {0.50f, 0.255f}, {0.85f, 0.215f}, {1.02f, 0.21f}}, 16, CHECK, 0.14f, 0.26f, 0.52f).pop();
        // torso group
        b.push().translate(0, 1.0f, 0).rotX(lean);
        b.lathe(new float[][]{{0f, 0.205f}, {0.15f, 0.20f}, {0.30f, 0.215f}, {0.40f, 0.20f}}, 16, CLOTH, 0.86f, 0.84f, 0.76f);
        // arms
        float breathe = (float) Math.sin(t * 1.7) * 0.02f;
        for (int side = -1; side <= 1; side += 2) {
            float raise;
            float elbow = 0f;
            if (pose == Pose.CHEER) raise = (float) Math.PI * 0.82f + (float) Math.sin(t * 6 + side) * 0.08f;
            else if (pose == Pose.WARN && side == 1) { raise = (float) Math.PI * 0.80f; elbow = (float) Math.sin(t * 9) * 0.55f; }
            else if (pose == Pose.SAD) raise = 0.03f;
            else raise = 0.07f + breathe + (side == 1 ? 0.03f : 0f);
            b.push().translate(side * 0.235f, 0.36f, 0).rotZ(side * raise);
            b.sphere(0.06f, 8, 6, CLOTH, 0.86f, 0.84f, 0.76f);
            b.push().translate(0, -0.30f, 0).cylinder(0.045f, 0.055f, 0.30f, 8, CLOTH, 0.86f, 0.84f, 0.76f, true, 1f).pop();
            b.translate(0, -0.30f, 0).rotZ(side * (elbow - 0.1f));
            b.push().translate(0, -0.27f, 0).cylinder(0.035f, 0.045f, 0.27f, 8, SKIN, skin[0], skin[1], skin[2], true, 1f).pop();
            b.push().translate(0, -0.30f, 0).sphere(0.04f, 6, 5, SKIN, skin[0], skin[1], skin[2]).pop();
            b.pop();
        }
        // neck and head
        b.push().translate(0, 0.38f, 0).cylinder(0.05f, 0.05f, 0.07f, 8, SKIN, skin[0], skin[1], skin[2], true, 1f).pop();
        b.push().translate(0, 0.47f, 0.02f).rotY(headYaw).rotX(pose == Pose.SAD ? 0.45f : (float) Math.sin(t * 0.7) * 0.04f);
        b.push().translate(0, 0.11f, 0).scale(0.92f, 1.12f, 1f).sphere(0.105f, 14, 10, SKIN, skin[0], skin[1], skin[2]).pop();
        b.push().translate(0, 0.14f, -0.01f).cylinder(0.103f, 0.10f, 0.055f, 14, FLAT, 0.93f, 0.93f, 0.90f, false, 1f)
                .translate(0, 0.055f, 0).lathe(new float[][]{{0f, 0.10f}, {0.03f, 0.085f}, {0.055f, 0.045f}, {0.07f, 0.0f}}, 14, CLOTH, 0.93f, 0.93f, 0.9f).pop();
        b.push().translate(0, 0.045f, 0.075f).scale(1.0f, 0.75f, 0.7f).sphere(0.075f, 10, 8, FLAT, 0.36f, 0.34f, 0.32f).pop();   // beard
        for (int side = -1; side <= 1; side += 2) b.push().translate(side * 0.04f, 0.13f, 0.095f).sphere(0.012f, 5, 4, FLAT, 0.03f, 0.02f, 0.02f).pop();
        b.push().translate(0, 0.095f, 0.105f).sphere(0.02f, 6, 5, SKIN, skin[0] * 0.92f, skin[1] * 0.9f, skin[2] * 0.9f).pop();
        b.pop();
        b.pop();
        float[] v = b.vertices();
        int[] idx = b.indices();
        if (mesh == null) mesh = StaticMesh.createDynamic(v, idx, PropBuilder.LAYOUT);
        else mesh.update(v, idx);
    }
}
