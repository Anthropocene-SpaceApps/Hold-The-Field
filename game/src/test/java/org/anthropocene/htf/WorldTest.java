package org.anthropocene.htf;

import org.anthropocene.htf.core.KeyAction;
import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.gfx.MeshBuilder;
import org.anthropocene.htf.world.Player;
import org.anthropocene.htf.world.Terrain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    static Terrain terrain;
    @BeforeAll static void build(@TempDir Path dir) {
        System.setProperty("htf.home", dir.toString());
        terrain = new Terrain();
    }

    private static final Player.Collider WORLD = new Player.Collider() {
        public double solidTop(int x, int z) { return terrain.solidTop(x, z); }
        public double waterSurface(double x, double z) { return Double.NaN; }
    };

    @Test void layoutMatchesTheDesign() {
        assertEquals(1, terrain.height(3, 3), "field is flat at y=1");
        assertEquals(Terrain.LAKE_FLOOR, terrain.height(0, -8), "the haor basin is below the field");
        assertEquals(Terrain.MOUND_TOP, terrain.height(15, 5), "homestead mound");
        assertTrue(terrain.height(-10, -60) > 5, "hills rise towards the north");
        assertTrue(terrain.height(4, -60) < 2, "the river valley is carved through them");
        assertTrue(terrain.height(-30, 0) > 10, "the haor is walled in on its sides");
    }

    @Test void terrainIsDeterministicAndMeshIsBuilt() {
        Terrain other = new Terrain();
        for (int x = Terrain.X0; x < Terrain.X1; x += 7) for (int z = Terrain.Z0; z < Terrain.Z1; z += 5)
            assertEquals(terrain.height(x, z), other.height(x, z));
        MeshBuilder mb = new MeshBuilder();
        terrain.buildStatic(mb);
        assertTrue(mb.vertexCount() > 50_000);
    }

    @Test void playerFallsAndStandsOnTheGround() {
        Player p = new Player();
        p.teleport(4.5f, 12f, 14.5f, 0, 0);
        for (int i = 0; i < 200; i++) p.update(1 / 60f, WORLD, 0, 0, false, false, false);
        assertTrue(p.onGround);
        assertEquals(1.0, p.pos.y, 0.01);
    }

    @Test void playerCannotWalkThroughTheHutWall() {
        Player p = new Player();
        p.teleport(15.5f, Terrain.MOUND_TOP, 10.0f, 0, 0);          // in front of the door, facing north (-z)
        for (int i = 0; i < 120; i++) p.update(1 / 60f, WORLD, 1, 0, false, false, false);
        assertTrue(p.pos.z >= Terrain.HUT_Z1 - 0.5f, "stopped at the wall, z=" + p.pos.z);
    }

    @Test void playerCannotClimbTheMoundWithoutHelp() {
        Player p = new Player();
        p.teleport(10.5f, 1f, 6f, -(float) Math.PI / 2, 0);          // west of the mound, walking east (+x)
        for (int i = 0; i < 120; i++) p.update(1 / 60f, WORLD, 1, 0, i % 20 == 0, false, false);
        assertTrue(p.pos.x < Terrain.MOUND_X0 + 0.2f, "stopped at the mound, x=" + p.pos.x);
    }

    @Test void flyingIgnoresGravity() {
        Player p = new Player();
        p.teleport(4.5f, 20f, 14.5f, 0, 0);
        p.flying = true;
        for (int i = 0; i < 60; i++) p.update(1 / 60f, WORLD, 0, 0, false, false, false);
        assertTrue(p.pos.y > 19f);
    }

    @Test void settingsSanitiseAndBindKeys() {
        Settings s = new Settings();
        assertEquals(KeyAction.FORWARD.defaultKey, s.key(KeyAction.FORWARD));
        s.bind(KeyAction.FORWARD, 65);
        assertEquals(65, s.key(KeyAction.FORWARD));
        s.resetKeys();
        assertEquals(KeyAction.FORWARD.defaultKey, s.key(KeyAction.FORWARD));
    }
}
