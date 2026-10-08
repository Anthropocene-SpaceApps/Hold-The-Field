package org.anthropocene.htf.gfx;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** First-person camera: position + yaw/pitch (radians). yaw 0 looks towards -z (north). */
public final class Camera {
    public final Vector3f pos = new Vector3f();
    public float yaw, pitch;
    public float fovDeg = 70, near = 0.08f, far = 420f;
    public final Matrix4f proj = new Matrix4f(), view = new Matrix4f(), viewProj = new Matrix4f();

    public Vector3f forward() {
        float cp = (float) Math.cos(pitch);
        return new Vector3f((float) -Math.sin(yaw) * cp, (float) Math.sin(pitch), (float) -Math.cos(yaw) * cp);
    }

    public void update(int fbW, int fbH) {
        float aspect = fbH == 0 ? 1 : (float) fbW / fbH;
        proj.identity().perspective((float) Math.toRadians(fovDeg), aspect, near, far);
        Vector3f f = forward();
        view.identity().lookAt(pos, new Vector3f(pos).add(f), new Vector3f(0, 1, 0));
        viewProj.set(proj).mul(view);
    }
}
