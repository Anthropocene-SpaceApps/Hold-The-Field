package org.anthropocene.htf;

import org.anthropocene.htf.core.KeyAction;
import org.anthropocene.htf.core.Settings;
import org.anthropocene.htf.world.Atmosphere;
import org.anthropocene.htf.world.Farmstead;
import org.anthropocene.htf.world.Landscape;
import org.anthropocene.htf.world.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    static Landscape land;
    static Farmstead farm;

    @BeforeAll static void build() {
        land = new Landscape();
        farm = new Farmstead(land);
    }

    /** Collider over the bare landscape, the embankment at its reference height and the farmstead obstacles. */
    private static final Player.Collider WORLD = new Player.Collider() {
        public double groundHeight(double x, double z) {
            double y = land.height(x, z);
            if (Math.abs(z - Landscape.DIKE_Z) < 4.2 && x > Landscape.DIKE_X0 && x < Landscape.DIKE_X1) y += Landscape.dikeFraction((float) x, (float) z) * Landscape.DIKE_REF_H;
            return y;
        }
        public boolean blocked(double x, double z, double r, double feetY) {
            if (groundHeight(x, z) > feetY + Player.STEP) return true;
            for (Farmstead.Obstacle o : farm.obstacles()) {
                if (o.circle() ? Math.hypot(x - o.x(), z - o.z()) < o.hx() + r : Math.abs(x - o.x()) < o.hx() + r && Math.abs(z - o.z()) < o.hz() + r) return true;
            }
            return false;
        }
        public double waterDepth(double x, double z) { return 0; }
    };

    @Test void layoutMatchesTheDesign() {
        assertEquals(0, land.height(0, 0), 0.15, "the paddy plot is flat near zero");
        assertTrue(land.height(Landscape.HOME_X, Landscape.HOME_Z) > 1.0, "the homestead stands on a raised mound");
        assertTrue(land.height(Landscape.POND_X, Landscape.POND_Z) < -0.5, "the pond is a hollow");
        assertTrue(land.height(-200, -60) < 0.8 && land.height(-200, -60) > -1.0, "the haor basin is low flat ground");
        assertTrue(land.height(-150, -700) > 60, "the hills rise towards the north");
        float rx = Landscape.riverX(-600);
        assertTrue(land.height(rx, -600) < land.height(rx + 60, -600) - 5, "the river runs in a valley");
    }

    @Test void landscapeIsDeterministic() {
        Landscape other = new Landscape();
        for (float x = Landscape.X0; x < Landscape.X1; x += 97) for (float z = Landscape.Z0; z < Landscape.Z1; z += 83)
            assertEquals(land.height(x, z), other.height(x, z), 1e-6);
    }

    @Test void embankmentFootprintHasACrownAndTaperedEnds() {
        assertEquals(1f, Landscape.dikeFraction(0, Landscape.DIKE_Z), 0.1f);
        assertEquals(0f, Landscape.dikeFraction(0, Landscape.DIKE_Z + 10), 1e-6f);
        assertTrue(Landscape.dikeFraction(Landscape.DIKE_X1 - 2, Landscape.DIKE_Z) < 0.2f, "the ends fade into the ground");
    }

    @Test void playerFallsAndStandsOnTheGround() {
        Player p = new Player();
        p.teleport(10, 8, 42, 0, 0);
        for (int i = 0; i < 200; i++) p.update(1 / 60f, WORLD, 0, 0, false, false, false);
        assertTrue(p.onGround);
        assertEquals(land.height(10, 42), p.pos.y, 0.05);
    }

    @Test void playerCannotWalkThroughTheHouse() {
        Player p = new Player();
        p.teleport(Farmstead.HOUSE_X - 8, land.height(Farmstead.HOUSE_X - 8, Farmstead.HOUSE_Z), Farmstead.HOUSE_Z, -(float) Math.PI / 2, 0);   // west of the house, walking east
        for (int i = 0; i < 400; i++) p.update(1 / 60f, WORLD, 1, 0, false, false, false);
        assertTrue(p.pos.x < Farmstead.HOUSE_X - 2.0f, "stopped by the walls, x=" + p.pos.x);
    }

    @Test void playerCanWalkOverTheEmbankment() {
        Player p = new Player();
        p.teleport(-60, land.height(-60, Landscape.DIKE_Z + 8), Landscape.DIKE_Z + 8, 0, 0);          // south of the embankment, facing north
        for (int i = 0; i < 600; i++) p.update(1 / 60f, WORLD, 1, 0, false, false, false);
        assertTrue(p.pos.z < Landscape.DIKE_Z - 4, "crossed the embankment, z=" + p.pos.z);
    }

    @Test void flyingIgnoresGravity() {
        Player p = new Player();
        p.teleport(10, 30, 42, 0, 0);
        p.flying = true;
        for (int i = 0; i < 60; i++) p.update(1 / 60f, WORLD, 0, 0, false, false, false);
        assertTrue(p.pos.y > 29f);
    }

    @Test void atmosphereBrightensByDayDarkensInStormsAndHasMoonlight() {
        Atmosphere a = new Atmosphere();
        a.update(10, 2, 0, 0);          // noon
        float noon = a.sunColor.length();
        assertTrue(a.sunDir.y > 0.8f);
        a.update(10, 2, 1, 0);          // noon, thunderstorm
        assertTrue(a.sunColor.length() < noon * 0.3f);
        a.hour = 1f;
        a.update(0, 4, 0, 0);           // deep night in cycle mode
        assertTrue(a.sunColor.length() < 0.5f * noon * 0.2f + 0.2f);
        assertTrue(a.sunDir.y > 0, "the light direction stays above the horizon (moon)");
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
