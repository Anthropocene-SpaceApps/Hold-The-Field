// The 3D farm (Three.js, vendored in vendor/three). Reads the sim state, never changes it.
// Built to stay light: one instanced mesh for all the rice, Lambert/Phong materials, no shadow maps,
// everything generated in code (no textures or models to download).
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/OrbitControls.js';
import { noise2, fbm, ridged, rand } from './noise.js';

export const VS = 1.6;                       // vertical scale: 1 m of water or rice = 1.6 world units
const FW = 48, FD = 32;                      // rice field size (world units), centred on the origin
const LAKE_EDGE = -FD / 2 - 3;               // haor lake starts just north of the field
const UP_POINT = new THREE.Vector3(70, 40, -430);   // where the upstream rain is measured (Meghalaya hills)

const PAL = {
  skyTop: new THREE.Color('#5b8fc7'), skyHorizon: new THREE.Color('#d6e2ea'),
  stormTop: new THREE.Color('#2d3741'), stormHorizon: new THREE.Color('#7f8b94'),
  dryTop: new THREE.Color('#5f9ad3'), dryHorizon: new THREE.Color('#e3e0cf'),
  green: new THREE.Color('#5f9e3c'), lush: new THREE.Color('#78ad45'), gold: new THREE.Color('#d9b443'),
  straw: new THREE.Color('#bba46c'), deadFlood: new THREE.Color('#6b6450'), deadDry: new THREE.Color('#9b8a62'),
  soilWet: new THREE.Color('#4b3a2b'), soilDry: new THREE.Color('#b99d74'),
};

export function createWorld(canvas, { mobile = false } = {}) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: !mobile, powerPreference: 'high-performance' });
  let pixelRatio = Math.min(window.devicePixelRatio || 1, mobile ? 1.5 : 2);
  renderer.setPixelRatio(pixelRatio);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.05;

  const scene = new THREE.Scene();
  scene.fog = new THREE.Fog(PAL.skyHorizon.clone(), 90, 720);
  const camera = new THREE.PerspectiveCamera(50, 1, 0.5, 2600);
  camera.position.set(44, 20, 52);

  const controls = new OrbitControls(camera, canvas);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.target.set(0, 0, -6);
  controls.minDistance = 12;
  controls.maxDistance = 190;
  controls.maxPolarAngle = 1.42;
  controls.zoomSpeed = 0.8;
  controls.rotateSpeed = 0.6;

  // ---------------------------------------------------------------- sky, light
  const skyUniforms = { top: { value: PAL.skyTop.clone() }, horizon: { value: PAL.skyHorizon.clone() }, flash: { value: 0 } };
  const sky = new THREE.Mesh(new THREE.SphereGeometry(2000, 24, 12), new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false, uniforms: skyUniforms,
    vertexShader: 'varying vec3 vP; void main(){ vP = position; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: `uniform vec3 top; uniform vec3 horizon; uniform float flash; varying vec3 vP;
      void main(){ float h = clamp(normalize(vP).y, -0.2, 1.0); vec3 c = mix(horizon, top, pow(max(h,0.0), 0.55));
      gl_FragColor = vec4(c + flash, 1.0); }`,
  }));
  scene.add(sky);
  const hemi = new THREE.HemisphereLight('#dfeaf5', '#5d5a3c', 1.25);
  const sun = new THREE.DirectionalLight('#fff3dc', 2.1);
  sun.position.set(-80, 120, 60);
  scene.add(hemi, sun);

  // ---------------------------------------------------------------- groups per scenario
  const root = new THREE.Group();
  scene.add(root);
  let hazard = null;
  let parts = {};

  // shared rain, particles, clouds
  const rain = makeRain(mobile ? 900 : 2200);
  scene.add(rain.mesh);
  const bursts = makeBursts(mobile ? 300 : 600);
  scene.add(bursts.points);
  const clouds = makeClouds(mobile ? 14 : 26);
  scene.add(clouds.group);

  // ---------------------------------------------------------------- rice (one instanced mesh)
  const rice = makeRice(mobile ? 0.78 : 0.56);
  scene.add(rice.mesh);

  // ---------------------------------------------------------------- Rahim, hut, sacks
  const rahim = makeRahim();
  rahim.group.position.set(FW / 2 + 3.2, 0, -FD / 2 + 6);
  scene.add(rahim.group);
  const sacks = makeSacks();
  scene.add(sacks.group);

  // ---------------------------------------------------------------- display state (eased toward sim targets)
  const view = {
    lakeY: -0.5, fieldWaterY: -0.4, bundH: 0.6 * VS, growth: 0.3, tint: PAL.green.clone(), droop: 0,
    rainFarm: 0, rainUp: 0, storm: 0, dryness: 0, moisture: 0.6, tankFrac: 0.6, harvested: false,
    soil: PAL.soilWet.clone(), pose: 'idle', overlay: 0, lightning: 0,
  };
  const target = { ...view, tint: view.tint.clone(), soil: view.soil.clone() };
  let mode = 'title';
  let satT = 0;                                 // 0 = ground camera, 1 = satellite camera
  const savedCam = { pos: new THREE.Vector3(), target: new THREE.Vector3() };

  function setScenario(hz) {
    if (hz === hazard) return;
    hazard = hz;
    root.clear();
    for (const p of Object.values(parts)) p.dispose?.();
    parts = hz === 'drought' ? buildDrought(root) : buildFlood(root);
    view.lakeY = target.lakeY = -0.5;
    view.fieldWaterY = target.fieldWaterY = -0.4;
    sacks.set(0);
  }

  /** Point the world at today's sim state (called whenever the sim state or day changes). */
  function setState(st, day, cfg, data) {
    if (!st || !day) return;
    const dry = hazard === 'drought';
    target.rainFarm = Math.min(1, day.rainFarm / 35);
    target.rainUp = dry ? 0 : Math.min(1, day.rainUp / 140);
    target.storm = Math.min(1, Math.max(target.rainFarm, dry ? 0 : target.rainUp * 0.85));
    if (dry) {
      target.moisture = st.moisture;
      const thr = cfg.varieties[st.variety].stressSoil;
      target.dryness = Math.max(0, Math.min(1, (thr + 0.12 - st.moisture) / 0.24));
      target.tankFrac = Math.max(0, Math.min(1, st.tank / cfg.tankMax));
      target.bundH = 0.32;
      target.soil.copy(PAL.soilWet).lerp(PAL.soilDry, target.dryness);
    } else {
      target.lakeY = Math.max(-0.5, st.level * VS);
      target.bundH = st.bund * VS;
      target.fieldWaterY = st.flooded ? st.level * VS : -0.4;
      target.soil.copy(PAL.soilWet);
    }
    // crop look
    const m = st.maturity;
    target.harvested = st.harvested;
    if (!st.alive) {
      target.tint.copy(dry ? PAL.deadDry : PAL.deadFlood);
      target.growth = 0.25 + 0.55 * Math.min(1, m * 1.4);
      target.droop = 1;
    } else if (st.harvested) {
      target.tint.copy(PAL.straw);
      target.growth = 0.1;
      target.droop = 0;
    } else {
      const c = m < 0.45 ? PAL.green.clone().lerp(PAL.lush, m / 0.45)
        : m < 0.8 ? PAL.lush.clone().lerp(PAL.gold, (m - 0.45) / 0.35 * 0.55)
          : PAL.lush.clone().lerp(PAL.gold, 0.55 + (m - 0.8) / 0.2 * 0.45);
      if (dry) c.lerp(PAL.straw, Math.min(0.85, st.stressLoad * 0.9 + (st.stressed ? 0.12 : 0)));
      target.tint.copy(c);
      target.growth = 0.25 + 0.75 * Math.min(1, m * 1.35);
      target.droop = dry ? Math.min(0.6, st.stressLoad * 0.7) : 0;
    }
    target.pose = !st.alive ? 'sad' : st.harvested && st.yieldPct > 0.4 ? 'cheer' : st.status === 'warning' && !st.harvested ? 'warn' : 'idle';
    sacks.set(st.harvested && st.alive ? Math.round(st.yieldPct * 14) : 0);
    // satellite overlay colours
    if (parts.setOverlay) parts.setOverlay(day, st, data);
  }

  function burst(kind) {
    const at = kind === 'bund' ? new THREE.Vector3(rand() * 20 - 10, view.bundH + 0.3, -FD / 2)
      : kind === 'water' ? new THREE.Vector3(0, 1, 0) : new THREE.Vector3(0, 1.2, 0);
    const col = kind === 'bund' ? '#8a6a45' : kind === 'water' ? '#6fb6ff' : kind === 'flood' ? '#7fa6c2' : '#f2cc55';
    bursts.spawn(at, new THREE.Color(col), kind === 'harvest' ? 90 : 60, kind === 'harvest' ? 16 : 12);
  }

  // ---------------------------------------------------------------- camera modes
  function setMode(m) {
    if (m === mode) return;
    if (m === 'satellite') { savedCam.pos.copy(camera.position); savedCam.target.copy(controls.target); }
    if (mode === 'satellite' && m !== 'satellite') { camera.position.copy(savedCam.pos); controls.target.copy(savedCam.target); }
    mode = m;
    controls.autoRotate = m === 'title';
    controls.autoRotateSpeed = 0.35;
    controls.maxDistance = m === 'satellite' ? 900 : 190;
    controls.maxPolarAngle = m === 'satellite' ? 1.2 : 1.42;
    if (m === 'satellite') {
      const flood = hazard !== 'drought';
      controls.target.set(flood ? 35 : 0, 0, flood ? -290 : 0);
      camera.position.set(flood ? 40 : 10, flood ? 850 : 230, flood ? 220 : 110);
    } else if (m === 'play' || m === 'title') {
      if (m === 'title') { camera.position.set(60, 26, 70); controls.target.set(0, 4, -40); }
    }
  }
  function resetCamera() { camera.position.set(44, 20, 52); controls.target.set(0, 0, -6); }

  // ---------------------------------------------------------------- per-frame
  let t = 0, flashT = 0;
  function frame(dt) {
    dt = Math.min(dt, 0.1);
    t += dt;
    const k = 1 - Math.exp(-dt * 2.6);
    for (const key of ['lakeY', 'fieldWaterY', 'bundH', 'growth', 'droop', 'rainFarm', 'rainUp', 'storm', 'dryness', 'moisture', 'tankFrac']) {
      view[key] += (target[key] - view[key]) * k;
    }
    view.tint.lerp(target.tint, k);
    view.soil.lerp(target.soil, k);
    satT += ((mode === 'satellite' ? 1 : 0) - satT) * (1 - Math.exp(-dt * 4));

    // sky and weather
    const dry = hazard === 'drought';
    const top = (dry ? PAL.dryTop : PAL.skyTop).clone().lerp(PAL.stormTop, view.storm * 0.9);
    const hor = (dry ? PAL.dryHorizon : PAL.skyHorizon).clone().lerp(PAL.stormHorizon, view.storm * 0.85);
    skyUniforms.top.value.copy(top);
    skyUniforms.horizon.value.copy(hor);
    scene.fog.color.copy(hor);
    scene.fog.near = 70 - view.storm * 30;
    scene.fog.far = (dry ? 820 : 720) - view.storm * 260 + satT * 2600;
    scene.fog.near = scene.fog.near + satT * 700;
    hemi.intensity = 1.25 - view.storm * 0.45;
    sun.intensity = 2.1 - view.storm * 1.6;
    // lightning when the hills are drenched
    if (view.rainUp > 0.75 && rand() < dt * 0.25) flashT = 0.35;
    flashT = Math.max(0, flashT - dt);
    const flash = flashT > 0 ? (Math.sin(flashT * 60) > 0 ? 0.35 : 0.05) * (flashT / 0.35) : 0;
    skyUniforms.flash.value = flash;
    hemi.intensity += flash * 3;

    rain.update(dt, camera, controls.target, view.rainFarm * (1 - satT));
    clouds.update(dt, view.storm, view.rainUp, satT);
    bursts.update(dt);
    rice.update(t, view, mode === 'title' ? 1 : 0.6 + view.storm);
    rahim.update(t, dt, target.pose, camera);
    parts.update?.(t, dt, view, satT);
    rice.mesh.visible = true;

    controls.update();
    if (mode !== 'satellite') clampTarget();
    renderer.render(scene, camera);
  }

  function clampTarget() {
    const tg = controls.target;
    tg.x = Math.max(-80, Math.min(80, tg.x));
    tg.z = Math.max(-120, Math.min(70, tg.z));
    tg.y = Math.max(0, Math.min(20, tg.y));
    if (camera.position.y < 2) camera.position.y = 2;
  }

  function resize() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (!w || !h) return;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.fov = w / h < 0.9 ? 64 : 50;
    camera.updateProjectionMatrix();
  }

  /** Lower the resolution if the device is struggling (called by the frame-time monitor). */
  function degrade() {
    if (pixelRatio <= 0.75) return false;
    pixelRatio = Math.max(0.75, pixelRatio - 0.35);
    renderer.setPixelRatio(pixelRatio);
    resize();
    return true;
  }

  const ray = new THREE.Raycaster();
  function pickRahim(x, y) {
    const r = canvas.getBoundingClientRect();
    ray.setFromCamera(new THREE.Vector2(((x - r.left) / r.width) * 2 - 1, -((y - r.top) / r.height) * 2 + 1), camera);
    return ray.intersectObject(rahim.hit, false).length > 0;
  }

  /** World position to CSS pixels in the canvas, or null when behind the camera. */
  function project(v) {
    const p = v.clone().project(camera);
    if (p.z > 1) return null;
    return { x: (p.x + 1) / 2 * canvas.clientWidth, y: (1 - p.y) / 2 * canvas.clientHeight };
  }
  const anchors = () => ({ farm: new THREE.Vector3(0, 3, 0), upstream: UP_POINT.clone() });

  return { setScenario, setState, setMode, resetCamera, frame, resize, degrade, pickRahim, project, anchors, burst, get mode() { return mode; } };

  // ================================================================= scenario builders

  function buildFlood(group) {
    const out = {};
    // ground: lowland around the field, lake bed to the north
    const plots = [[-62, 4, 36, 30], [62, 34, 30, 24], [-10, 44, 40, 22]];
    const ground = terrain(1600, 1600, mobile ? 90 : 140, (x, z) => {
      const lake = smooth(LAKE_EDGE + 1, LAKE_EDGE - 7, z);
      const flat = flatMask(x, z, plots);
      const base = 0.1 + fbm(x * 0.02, z * 0.02, 3) * 0.7;
      return (base * (1 - flat) - 0.1 * flat) * (1 - lake) + (-3.2 + fbm(x * 0.01, z * 0.01, 2)) * lake;
    }, (x, z, y, c) => {
      const g = 0.5 + 0.5 * noise2(x * 0.05, z * 0.05);
      c.setRGB(0.36 + g * 0.08, 0.52 + g * 0.1, 0.22 + g * 0.05);
      if (y < -0.6) c.setRGB(0.32, 0.36, 0.3);
    });
    group.add(ground);
    // the Meghalaya hills upstream
    const hills = terrain(1800, 520, mobile ? 120 : 180, (x, z) => {
      const rise = smooth(130, -60, z);                  // local z: +260 is the near (south) edge
      const r = ridged(x * 0.006, z * 0.008, 5);
      return (r * 150 + fbm(x * 0.02, z * 0.02, 3) * 18) * rise - 6;
    }, (x, z, y, c) => {
      const h = Math.max(0, Math.min(1, y / 140));
      c.setRGB(0.3 + h * 0.25, 0.42 + h * 0.18, 0.28 + h * 0.2);
    });
    hills.position.set(0, 0, -470);
    group.add(hills);
    // lake water and field water
    const lake = water(1700, 380, mobile ? 40 : 70, mobile ? 10 : 16);
    lake.mesh.position.set(0, -0.5, LAKE_EDGE - 190);
    group.add(lake.mesh);
    const fieldWater = water(FW, FD, 16, 10, '#5b7880', 0.78);
    fieldWater.mesh.position.set(0, -0.4, 0);
    group.add(fieldWater.mesh);
    // paddy soil, bund, neighbours, hut, trees, boat
    const soil = new THREE.Mesh(new THREE.PlaneGeometry(FW, FD), new THREE.MeshLambertMaterial({ color: PAL.soilWet }));
    soil.rotation.x = -Math.PI / 2;
    soil.position.y = 0.01;
    group.add(soil);
    const bund = makeBund(1.7, false);
    group.add(bund.group);
    neighbours(group, plots, '#7ea54a');
    group.add(makeHut(FW / 2 + 12, 9));
    trees(group, 60, (x, z) => z > LAKE_EDGE + 4 && !inRect(x, z, -30, -24, 30, 24) && !inRect(x, z, 26, -14, 52, 30) && !nearPlot(x, z, plots) && Math.hypot(x, z) < 260);
    const boat = makeBoat();
    boat.position.set(-30, -0.5, LAKE_EDGE - 30);
    group.add(boat);
    // satellite overlays: rain over the hills, soil wetness on the farm, flow line, pins
    const rainLayer = overlayDisc(320, '#3b7bff');
    rainLayer.position.set(UP_POINT.x, 70, UP_POINT.z);
    group.add(rainLayer);
    const soilLayer = overlayDisc(70, '#c09040');
    soilLayer.position.set(0, 6, 0);
    group.add(soilLayer);
    const flow = flowLine(UP_POINT, new THREE.Vector3(0, 4, 0));
    group.add(flow.line);
    const pins = [pin(UP_POINT, '#7fb8ff'), pin(new THREE.Vector3(0, 3, 0), '#e6be4b')];
    pins.forEach((p) => group.add(p));

    out.setOverlay = (day) => {
      rainLayer.material.color.setHSL(0.62 - Math.min(1, day.rainUp / 160) * 0.62, 0.9, 0.5);
      soilLayer.material.color.copy(new THREE.Color('#c09040').lerp(new THREE.Color('#1a59d9'), day.soil));
    };
    out.update = (time, dt, v, sat) => {
      lake.mesh.position.y = v.lakeY;
      lake.wave(time, 0.12 + v.storm * 0.25);
      const flooded = v.fieldWaterY > 0.02;
      fieldWater.mesh.visible = flooded;
      fieldWater.mesh.position.y = Math.max(0.02, v.fieldWaterY);
      if (flooded) fieldWater.wave(time, 0.06 + v.storm * 0.1);
      bund.setHeight(v.bundH);
      boat.position.y = v.lakeY + 0.05 + Math.sin(time * 1.3) * 0.06;
      boat.rotation.z = Math.sin(time * 0.9) * 0.04;
      soil.material.color.copy(v.soil);
      const o = Math.max(0, sat - 0.2) / 0.8;
      rainLayer.material.opacity = o * (0.15 + v.rainUp * 0.6);
      soilLayer.material.opacity = o * 0.55;
      rainLayer.visible = soilLayer.visible = flow.line.visible = o > 0.01;
      pins.forEach((p) => { p.visible = o > 0.01; p.scale.setScalar(1 + o * 5); });
      flow.line.material.opacity = o;
      flow.update(dt);
    };
    return out;
  }

  function buildDrought(group) {
    const out = {};
    const plots = [[-60, 2, 36, 30], [60, 36, 30, 24], [-8, 46, 42, 22], [8, -48, 46, 28]];
    const flats = [...plots, [-36, 27, 16, 16]];          // + the village tank
    const ground = terrain(1600, 1600, mobile ? 90 : 140, (x, z) => {
      const flat = flatMask(x, z, flats);
      return (0.1 + fbm(x * 0.015, z * 0.015, 3) * 1.4) * (1 - flat) - 0.1 * flat;
    }, (x, z, y, c) => {
      const g = 0.5 + 0.5 * noise2(x * 0.04, z * 0.04);
      c.setRGB(0.56 + g * 0.1, 0.52 + g * 0.08, 0.3 + g * 0.05);
    });
    group.add(ground);
    const soil = new THREE.Mesh(new THREE.PlaneGeometry(FW, FD), new THREE.MeshLambertMaterial({ color: PAL.soilWet }));
    soil.rotation.x = -Math.PI / 2;
    soil.position.y = 0.01;
    group.add(soil);
    const cracks = new THREE.Mesh(new THREE.PlaneGeometry(FW, FD), new THREE.MeshBasicMaterial({ map: crackTexture(), transparent: true, opacity: 0, depthWrite: false, color: '#3a2a1c' }));
    cracks.rotation.x = -Math.PI / 2;
    cracks.position.y = 0.03;
    group.add(cracks);
    const sheen = new THREE.Mesh(new THREE.PlaneGeometry(FW, FD), new THREE.MeshPhongMaterial({ color: '#5d86a0', transparent: true, opacity: 0, shininess: 90, specular: '#cfe3ef', depthWrite: false }));
    sheen.rotation.x = -Math.PI / 2;
    sheen.position.y = 0.05;
    group.add(sheen);
    const bund = makeBund(0.9, true);
    bund.setHeight(0.32);
    group.add(bund.group);
    const nb = neighbours(group, plots, '#8aa04a');
    group.add(makeHut(FW / 2 + 12, 9));
    trees(group, 70, (x, z) => !inRect(x, z, -30, -24, 30, 24) && !inRect(x, z, 26, -14, 56, 36) && !inRect(x, z, -46, 16, -26, 38) && !nearPlot(x, z, plots) && Math.hypot(x, z) < 300, true);
    // the village tank
    const tank = makeTank();
    tank.group.position.set(-36, 0, 27);
    group.add(tank.group);
    const soilLayer = overlayDisc(80, '#c09040');
    soilLayer.position.set(0, 6, 0);
    group.add(soilLayer);
    const pinFarm = pin(new THREE.Vector3(0, 3, 0), '#e6be4b');
    group.add(pinFarm);
    out.setOverlay = (day) => {
      soilLayer.material.color.copy(new THREE.Color('#c09040').lerp(new THREE.Color('#1a59d9'), day.soil));
    };
    const lush = new THREE.Color('#b9d98a'), parched = new THREE.Color('#ffffff');
    out.update = (time, dt, v, sat) => {
      ground.material.color.copy(lush).lerp(parched, v.dryness);
      soil.material.color.copy(v.soil);
      cracks.material.opacity = Math.max(0, v.dryness - 0.15) * 1.0;
      sheen.material.opacity = Math.max(0, (v.moisture - 0.62) * 1.6);
      for (const m of nb) m.material.color.copy(new THREE.Color('#8aa04a').lerp(new THREE.Color('#b8a06a'), v.dryness * 0.8));
      tank.setLevel(v.tankFrac, time);
      const o = Math.max(0, sat - 0.2) / 0.8;
      soilLayer.visible = pinFarm.visible = o > 0.01;
      soilLayer.material.opacity = o * 0.55;
      pinFarm.scale.setScalar(1 + o * 5);
    };
    return out;
  }

  // ================================================================= builders

  function terrain(w, d, seg, height, color) {
    const g = new THREE.PlaneGeometry(w, d, seg, Math.max(4, Math.round(seg * d / w)));
    g.rotateX(-Math.PI / 2);
    const pos = g.attributes.position, cols = new Float32Array(pos.count * 3), c = new THREE.Color();
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i), z = pos.getZ(i);
      const y = height(x, z);
      pos.setY(i, y);
      color(x, z, y, c);
      c.convertSRGBToLinear();
      cols.set([c.r, c.g, c.b], i * 3);
    }
    g.setAttribute('color', new THREE.BufferAttribute(cols, 3));
    g.computeVertexNormals();
    return new THREE.Mesh(g, new THREE.MeshLambertMaterial({ vertexColors: true }));
  }

  function water(w, d, sx, sz, color = '#3d6d8c', opacity = 0.9) {
    const g = new THREE.PlaneGeometry(w, d, sx, sz);
    g.rotateX(-Math.PI / 2);
    const base = Float32Array.from(g.attributes.position.array);
    const mat = new THREE.MeshPhongMaterial({ color, specular: '#bcd6e6', shininess: 70, transparent: true, opacity, depthWrite: opacity > 0.85 });
    const mesh = new THREE.Mesh(g, mat);
    let last = -1;
    return {
      mesh,
      wave(time, amp) {
        if (time - last < 1 / 30) return;     // waves at 30 fps are plenty
        last = time;
        const p = g.attributes.position;
        for (let i = 0; i < p.count; i++) {
          const x = base[i * 3], z = base[i * 3 + 2];
          p.array[i * 3 + 1] = amp * (Math.sin(x * 0.11 + time * 1.4) + Math.sin(z * 0.17 - time * 1.1) * 0.7);
        }
        p.needsUpdate = true;
        g.computeVertexNormals();
      },
    };
  }

  function makeBund(width, low) {
    const group = new THREE.Group();
    const mat = new THREE.MeshLambertMaterial({ color: low ? '#9a7c55' : '#6f5a3e' });
    const topMat = new THREE.MeshLambertMaterial({ color: low ? '#8f9a52' : '#6d8a3c' });
    const sides = [[0, -FD / 2, FW + width * 2, 0], [0, FD / 2, FW + width * 2, 0], [-FW / 2, 0, FD, Math.PI / 2], [FW / 2, 0, FD, Math.PI / 2]];
    const meshes = [];
    for (const [x, z, len, rot] of sides) {
      const g = new THREE.BoxGeometry(len, 1, width, 1, 1, 1);
      g.translate(0, 0.5, 0);
      const p = g.attributes.position;
      for (let i = 0; i < p.count; i++) if (p.getY(i) > 0.5) p.setZ(i, p.getZ(i) * 0.5);
      g.computeVertexNormals();
      const m = new THREE.Mesh(g, [mat, mat, topMat, mat, mat, mat]);
      m.position.set(x, 0, z);
      m.rotation.y = rot;
      group.add(m);
      meshes.push(m);
    }
    return { group, setHeight: (h) => { for (const m of meshes) m.scale.y = Math.max(0.05, h); } };
  }

  function neighbours(group, rects, color) {
    const tex = rowsTexture();
    const out = [];
    for (const [x, z, w, d] of rects) {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), new THREE.MeshLambertMaterial({ color, map: tex }));
      m.rotation.x = -Math.PI / 2;
      m.position.set(x, 0.0, z);
      group.add(m);
      out.push(m);
    }
    return out;
  }

  function makeHut(x, z) {
    const g = new THREE.Group();
    const walls = new THREE.Mesh(new THREE.BoxGeometry(6, 2.8, 4.6), new THREE.MeshLambertMaterial({ color: '#b48a5e' }));
    walls.position.y = 1.4;
    const roof = new THREE.Mesh(new THREE.ConeGeometry(5, 2.6, 4), new THREE.MeshLambertMaterial({ color: '#c9a65a', flatShading: true }));
    roof.position.y = 4.1;
    roof.rotation.y = Math.PI / 4;
    roof.scale.set(1.15, 1, 0.9);
    const door = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 1.9), new THREE.MeshLambertMaterial({ color: '#4a3424' }));
    door.position.set(-3.01, 0.95, 0);
    door.rotation.y = -Math.PI / 2;
    const plinth = new THREE.Mesh(new THREE.BoxGeometry(7, 0.4, 5.6), new THREE.MeshLambertMaterial({ color: '#8d6c4a' }));
    plinth.position.y = 0.2;
    g.add(plinth, walls, roof, door);
    for (const [hx, hz, s] of [[5, -3, 1], [6.5, 1.5, 0.8]]) {
      const hay = new THREE.Mesh(new THREE.ConeGeometry(1.3 * s, 2.6 * s, 10), new THREE.MeshLambertMaterial({ color: '#d4b25c' }));
      hay.position.set(hx, 1.3 * s, hz);
      g.add(hay);
    }
    g.position.set(x, 0, z);
    return g;
  }

  function trees(group, n, ok, dry = false) {
    const trunkG = new THREE.CylinderGeometry(0.25, 0.4, 1, 6);
    trunkG.translate(0, 0.5, 0);
    const crownG = new THREE.IcosahedronGeometry(1, 0);
    const trunkM = new THREE.MeshLambertMaterial({ color: '#5a4430' });
    const crownM = new THREE.MeshLambertMaterial({ color: dry ? '#6f8a3a' : '#3f7a35', flatShading: true });
    const trunks = new THREE.InstancedMesh(trunkG, trunkM, n), crowns = new THREE.InstancedMesh(crownG, crownM, n);
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), p = new THREE.Vector3();
    let i = 0, guard = 0;
    while (i < n && guard++ < 4000) {
      const x = (rand() - 0.5) * 520, z = (rand() - 0.5) * 420 + 30;
      if (!ok(x, z)) continue;
      const h = 5 + rand() * 6;
      trunks.setMatrixAt(i, m.compose(p.set(x, 0, z), q.identity(), s.set(1, h, 1)));
      const r = 2.2 + rand() * 2.2;
      crowns.setMatrixAt(i, m.compose(p.set(x, h + r * 0.4, z), q.setFromEuler(new THREE.Euler(0, rand() * 6, 0)), s.set(r, r * 0.85, r)));
      i++;
    }
    trunks.count = crowns.count = i;
    group.add(trunks, crowns);
  }

  function makeBoat() {
    const g = new THREE.Group();
    const hullG = new THREE.CylinderGeometry(0.9, 0.5, 7, 8, 1, false, 0, Math.PI);
    hullG.rotateZ(Math.PI / 2);
    hullG.rotateX(Math.PI);
    const hull = new THREE.Mesh(hullG, new THREE.MeshLambertMaterial({ color: '#5b3f28', side: THREE.DoubleSide }));
    const roof = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 2.6, 10, 1, true, 0, Math.PI), new THREE.MeshLambertMaterial({ color: '#7b6440', side: THREE.DoubleSide }));
    roof.rotation.z = Math.PI / 2;
    roof.position.y = 0.4;
    g.add(hull, roof);
    return g;
  }

  function makeTank() {
    const group = new THREE.Group();
    const bank = new THREE.Mesh(new THREE.TorusGeometry(6.2, 0.9, 6, 24), new THREE.MeshLambertMaterial({ color: '#8b7350' }));
    bank.rotation.x = Math.PI / 2;
    bank.position.y = 0.2;
    const bed = new THREE.Mesh(new THREE.CircleGeometry(6, 24), new THREE.MeshLambertMaterial({ color: '#6d5a40' }));
    bed.rotation.x = -Math.PI / 2;
    bed.position.y = -0.05;
    const w = new THREE.Mesh(new THREE.CircleGeometry(5.8, 24), new THREE.MeshPhongMaterial({ color: '#3f6f8a', shininess: 80, specular: '#cfe3ef', transparent: true, opacity: 0.92 }));
    w.rotation.x = -Math.PI / 2;
    group.add(bank, bed, w);
    return { group, setLevel: (f, time) => { w.position.y = -0.03 + f * 0.45 + Math.sin(time * 1.5) * 0.01; w.visible = f > 0.02; } };
  }

  function overlayDisc(r, color) {
    const tex = radialTexture();
    const m = new THREE.Mesh(new THREE.CircleGeometry(r, 48), new THREE.MeshBasicMaterial({ color, map: tex, transparent: true, opacity: 0, depthWrite: false, fog: false }));
    m.rotation.x = -Math.PI / 2;
    m.visible = false;
    return m;
  }

  function flowLine(a, b) {
    const pts = [];
    for (let i = 0; i <= 40; i++) {
      const tt = i / 40, p = a.clone().lerp(b, tt);
      p.y += Math.sin(tt * Math.PI) * 30;
      pts.push(p);
    }
    const g = new THREE.BufferGeometry().setFromPoints(pts);
    const mat = new THREE.LineDashedMaterial({ color: '#ffffff', dashSize: 12, gapSize: 8, transparent: true, opacity: 0, fog: false, depthTest: false });
    const line = new THREE.Line(g, mat);
    line.computeLineDistances();
    line.visible = false;
    const dist = g.attributes.lineDistance;
    const base = Float32Array.from(dist.array);
    let off = 0;
    return { line, update(dt) { off += dt * 40; for (let i = 0; i < base.length; i++) dist.array[i] = base[i] - off; dist.needsUpdate = true; } };
  }

  function pin(at, color) {
    const g = new THREE.Group();
    const head = new THREE.Mesh(new THREE.SphereGeometry(0.9, 12, 8), new THREE.MeshBasicMaterial({ color, fog: false }));
    head.position.y = 2.6;
    const stem = new THREE.Mesh(new THREE.ConeGeometry(0.5, 2.2, 8), new THREE.MeshBasicMaterial({ color, fog: false }));
    stem.rotation.x = Math.PI;
    stem.position.y = 1.1;
    g.add(head, stem);
    g.position.copy(at);
    g.visible = false;
    return g;
  }
}

// ===================================================================== reusable pieces

function makeRice(spacing) {
  // one clump = 5 blades; height 1 (scaled in the shader by maturity), coloured darker at the base
  const pos = [], col = [];
  for (let b = 0; b < 5; b++) {
    const a = (b / 5) * Math.PI * 2 + rand() * 0.6, lean = 0.12 + rand() * 0.18, w = 0.035 + rand() * 0.02, h = 0.8 + rand() * 0.3;
    const dx = Math.cos(a), dz = Math.sin(a), px = -dz * w, pz = dx * w;
    const tip = [dx * lean, h, dz * lean];
    pos.push(px, 0, pz, -px, 0, -pz, ...tip);
    col.push(0.55, 0.6, 0.55, 0.55, 0.6, 0.55, 1.15, 1.12, 1.0);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  g.computeVertexNormals();
  const uniforms = { uTime: { value: 0 }, uHeight: { value: 0.5 }, uWind: { value: 1 }, uDroop: { value: 0 } };
  const mat = new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide, color: PAL.green.clone() });
  mat.onBeforeCompile = (sh) => {
    Object.assign(sh.uniforms, uniforms);
    sh.vertexShader = 'uniform float uTime; uniform float uHeight; uniform float uWind; uniform float uDroop;\n' + sh.vertexShader.replace(
      '#include <begin_vertex>',
      `#include <begin_vertex>
       float hh = position.y;
       transformed.y *= uHeight;
       vec2 ip = vec2(instanceMatrix[3].x, instanceMatrix[3].z);
       float sway = sin(uTime * 1.6 + ip.x * 0.21 + ip.y * 0.13) + 0.5 * sin(uTime * 2.7 + ip.x * 0.5);
       transformed.x += sway * 0.07 * uWind * hh * uHeight;
       transformed.xz += normalize(position.xz + 0.0001) * uDroop * hh * hh * 0.55 * uHeight;
       transformed.y -= uDroop * hh * hh * 0.35 * uHeight;`,
    );
  };
  const nx = Math.floor(FW / spacing), nz = Math.floor(FD / spacing);
  const mesh = new THREE.InstancedMesh(g, mat, nx * nz);
  const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), p = new THREE.Vector3(), c = new THREE.Color();
  let i = 0;
  for (let ix = 0; ix < nx; ix++) for (let iz = 0; iz < nz; iz++) {
    const x = -FW / 2 + spacing * (ix + 0.5) + (rand() - 0.5) * spacing * 0.25;
    const z = -FD / 2 + spacing * (iz + 0.5) + (rand() - 0.5) * spacing * 0.5;
    const sc = 0.85 + rand() * 0.3;
    mesh.setMatrixAt(i, m.compose(p.set(x, 0, z), q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), rand() * 6.28), s.set(sc, sc * VS, sc)));
    const v = 0.86 + rand() * 0.22;
    mesh.setColorAt(i, c.setRGB(v, v * (0.95 + rand() * 0.1), v));
    i++;
  }
  mesh.frustumCulled = false;
  return {
    mesh,
    update(time, v, wind) {
      uniforms.uTime.value = time;
      uniforms.uHeight.value = v.growth;
      uniforms.uWind.value = wind;
      uniforms.uDroop.value = v.droop;
      mat.color.copy(v.tint);
    },
  };
}

function makeRahim() {
  const group = new THREE.Group();
  const skin = new THREE.MeshLambertMaterial({ color: '#8a5d3d' });
  const shirt = new THREE.MeshLambertMaterial({ color: '#e5e1d3' });
  const lungi = new THREE.MeshLambertMaterial({ color: '#2f4e86' });
  const cap = new THREE.MeshLambertMaterial({ color: '#f2f0ea' });
  const beard = new THREE.MeshLambertMaterial({ color: '#5a5650' });
  const S = 1.3;
  const body = new THREE.Group();
  const legs = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.26, 0.95, 10), lungi);
  legs.position.y = 0.5;
  const torso = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.24, 0.62, 10), shirt);
  torso.position.y = 1.28;
  const head = new THREE.Group();
  head.position.y = 1.78;
  const face = new THREE.Mesh(new THREE.SphereGeometry(0.15, 14, 10), skin);
  face.scale.set(0.95, 1.12, 1);
  const hat = new THREE.Mesh(new THREE.SphereGeometry(0.15, 12, 6, 0, Math.PI * 2, 0, Math.PI / 2), cap);
  hat.position.y = 0.05;
  const b = new THREE.Mesh(new THREE.SphereGeometry(0.11, 10, 8), beard);
  b.position.set(0, -0.09, 0.07);
  b.scale.set(1, 0.8, 0.7);
  head.add(face, hat, b);
  const arm = (side) => {
    const g = new THREE.Group();
    g.position.set(side * 0.28, 1.55, 0);
    const upper = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 0.62, 8), shirt);
    upper.position.y = -0.31;
    const hand = new THREE.Mesh(new THREE.SphereGeometry(0.07, 8, 6), skin);
    hand.position.y = -0.65;
    g.add(upper, hand);
    return g;
  };
  const armL = arm(-1), armR = arm(1);
  body.add(legs, torso, head, armL, armR);
  group.add(body);
  group.scale.setScalar(S);
  const hit = new THREE.Mesh(new THREE.CylinderGeometry(0.9, 0.9, 2.6, 8), new THREE.MeshBasicMaterial({ visible: false }));
  hit.position.y = 1.2;
  group.add(hit);
  let yaw = -Math.PI * 0.75;
  return {
    group, hit,
    update(t, dt, pose, camera) {
      const dx = camera.position.x - group.position.x, dz = camera.position.z - group.position.z;
      const near = dx * dx + dz * dz < 40 * 40;
      const want = near ? Math.atan2(dx, dz) : -Math.PI * 0.8;
      yaw += Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw)) * Math.min(1, dt * 2.5);
      group.rotation.y = yaw;
      const breathe = Math.sin(t * 1.7) * 0.03;
      body.rotation.x = pose === 'sad' ? 0.22 : 0;
      head.rotation.x = pose === 'sad' ? 0.45 : Math.sin(t * 0.7) * 0.05;
      if (pose === 'cheer') {
        armL.rotation.z = -2.6 + Math.sin(t * 6) * 0.15;
        armR.rotation.z = 2.6 + Math.sin(t * 6 + 1) * 0.15;
      } else if (pose === 'warn') {
        armL.rotation.z = -0.1;
        armR.rotation.z = 2.5 + Math.sin(t * 9) * 0.4;
      } else {
        armL.rotation.z = -0.08 - breathe;
        armR.rotation.z = 0.08 + breathe;
      }
    },
  };
}

function makeSacks() {
  const group = new THREE.Group();
  const g = new THREE.SphereGeometry(0.55, 10, 8);
  g.scale(1, 0.8, 0.75);
  const mat = new THREE.MeshLambertMaterial({ color: '#d8c391' });
  const all = [];
  for (let i = 0; i < 14; i++) {
    const m = new THREE.Mesh(g, mat);
    const row = Math.floor(i / 5), col = i % 5;
    m.position.set(FW / 2 + 7 + col * 1.05 + (row % 2) * 0.5, 0.45 + row * 0.75, 16 + row * 0.15);
    m.visible = false;
    group.add(m);
    all.push(m);
  }
  return { group, set: (n) => all.forEach((m, i) => { m.visible = i < n; }) };
}

function makeRain(n) {
  const g = new THREE.BufferGeometry();
  const arr = new Float32Array(n * 6);
  const seeds = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) seeds.set([rand() * 120 - 60, rand() * 50, rand() * 120 - 60], i * 3);
  g.setAttribute('position', new THREE.BufferAttribute(arr, 3));
  const mat = new THREE.LineBasicMaterial({ color: '#cfe0ef', transparent: true, opacity: 0 });
  const mesh = new THREE.LineSegments(g, mat);
  mesh.frustumCulled = false;
  let fall = 0;
  return {
    mesh,
    update(dt, camera, target, intensity) {
      mat.opacity = Math.min(0.55, intensity * 0.7);
      mesh.visible = intensity > 0.03;
      if (!mesh.visible) return;
      fall += dt * 34;
      const cx = (camera.position.x + target.x) / 2, cz = (camera.position.z + target.z) / 2;
      const active = Math.floor(n * Math.min(1, 0.25 + intensity));
      for (let i = 0; i < n; i++) {
        const o = i * 6;
        if (i >= active) { arr.fill(0, o, o + 6); continue; }
        const x = cx + seeds[i * 3], z = cz + seeds[i * 3 + 2];
        const y = 50 - ((seeds[i * 3 + 1] + fall) % 50);
        arr[o] = x; arr[o + 1] = y; arr[o + 2] = z;
        arr[o + 3] = x + 0.25; arr[o + 4] = y - 1.3; arr[o + 5] = z;
      }
      g.attributes.position.needsUpdate = true;
    },
  };
}

function makeBursts(n) {
  const g = new THREE.BufferGeometry();
  const pos = new Float32Array(n * 3), colr = new Float32Array(n * 3);
  const vel = new Float32Array(n * 3), life = new Float32Array(n);
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.BufferAttribute(colr, 3));
  const points = new THREE.Points(g, new THREE.PointsMaterial({ size: 0.45, vertexColors: true, transparent: true, opacity: 0.95 }));
  points.frustumCulled = false;
  let next = 0;
  return {
    points,
    spawn(at, color, count, speed) {
      for (let k = 0; k < count; k++) {
        const i = next++ % n;
        pos.set([at.x + (rand() - 0.5) * 8, at.y, at.z + (rand() - 0.5) * 3], i * 3);
        vel.set([(rand() - 0.5) * speed, rand() * speed, (rand() - 0.5) * speed], i * 3);
        colr.set([color.r, color.g, color.b], i * 3);
        life[i] = 1 + rand() * 0.6;
      }
      g.attributes.color.needsUpdate = true;
    },
    update(dt) {
      let any = false;
      for (let i = 0; i < n; i++) {
        if (life[i] <= 0) continue;
        any = true;
        life[i] -= dt;
        vel[i * 3 + 1] -= 22 * dt;
        for (let a = 0; a < 3; a++) pos[i * 3 + a] += vel[i * 3 + a] * dt;
        if (life[i] <= 0) pos[i * 3 + 1] = -999;
      }
      points.visible = any;
      if (any) g.attributes.position.needsUpdate = true;
    },
  };
}

function makeClouds(n) {
  const group = new THREE.Group();
  const tex = cloudTexture();
  const list = [];
  for (let i = 0; i < n; i++) {
    const mat = new THREE.SpriteMaterial({ map: tex, color: '#ffffff', transparent: true, opacity: 0.7, depthWrite: false });
    const s = new THREE.Sprite(mat);
    const overHills = i < n * 0.5;
    s.position.set((rand() - 0.5) * 900, overHills ? 120 + rand() * 60 : 150 + rand() * 80, overHills ? -260 - rand() * 300 : (rand() - 0.5) * 900);
    const w = 120 + rand() * 160;
    s.scale.set(w, w * 0.45, 1);
    group.add(s);
    list.push({ s, overHills, speed: 2 + rand() * 3 });
  }
  return {
    group,
    update(dt, storm, rainUp, sat = 0) {
      group.visible = sat < 0.95;
      for (const c of list) {
        c.s.position.x += c.speed * dt * (1 + storm);
        if (c.s.position.x > 480) c.s.position.x = -480;
        const dark = c.overHills ? Math.max(storm, rainUp) : storm;
        c.s.material.color.setScalar(1 - dark * 0.62);
        c.s.material.opacity = (0.55 + dark * 0.4) * (1 - sat);
      }
    },
  };
}

// ---------------------------------------------------------------- generated textures

function canvasTexture(w, h, draw, repeat = false) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; }
  return t;
}

function cloudTexture() {
  return canvasTexture(256, 128, (x, w, h) => {
    for (let i = 0; i < 18; i++) {
      const cx = w * (0.15 + rand() * 0.7), cy = h * (0.45 + rand() * 0.25), r = 20 + rand() * 40;
      const g = x.createRadialGradient(cx, cy, 0, cx, cy, r);
      g.addColorStop(0, 'rgba(255,255,255,0.75)');
      g.addColorStop(1, 'rgba(255,255,255,0)');
      x.fillStyle = g;
      x.fillRect(0, 0, w, h);
    }
  });
}

function radialTexture() {
  return canvasTexture(128, 128, (x, w, h) => {
    const g = x.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
    g.addColorStop(0, 'rgba(255,255,255,1)');
    g.addColorStop(0.6, 'rgba(255,255,255,0.6)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = g;
    x.fillRect(0, 0, w, h);
  });
}

function rowsTexture() {
  return canvasTexture(128, 128, (x, w, h) => {
    x.fillStyle = '#ffffff';
    x.fillRect(0, 0, w, h);
    for (let y = 0; y < h; y += 6) {
      x.fillStyle = `rgba(40,60,20,${0.25 + rand() * 0.15})`;
      x.fillRect(0, y, w, 2);
    }
  }, true);
}

function crackTexture() {
  return canvasTexture(512, 512, (x, w, h) => {
    x.clearRect(0, 0, w, h);
    x.strokeStyle = 'rgba(255,255,255,0.95)';
    x.lineCap = 'round';
    for (let i = 0; i < 70; i++) {
      let px = rand() * w, py = rand() * h;
      x.lineWidth = 0.8 + rand() * 1.8;
      x.beginPath();
      x.moveTo(px, py);
      let a = rand() * Math.PI * 2;
      for (let s = 0; s < 10; s++) {
        a += (rand() - 0.5) * 1.3;
        px += Math.cos(a) * 14; py += Math.sin(a) * 14;
        x.lineTo(px, py);
      }
      x.stroke();
    }
  });
}

// ---------------------------------------------------------------- small helpers

function smooth(e0, e1, v) {
  const t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
  return t * t * (3 - 2 * t);
}

function fieldMask(x, z, margin) {
  const dx = Math.max(0, Math.abs(x) - FW / 2), dz = Math.max(0, Math.abs(z) - FD / 2);
  return 1 - smooth(0, margin, Math.hypot(dx, dz));
}

function inRect(x, z, x0, z0, x1, z1) { return x > x0 && x < x1 && z > z0 && z < z1; }

/** 1 inside the rice field and the neighbours' plots (flattened terrain), easing to 0 a few metres out. */
function flatMask(x, z, plots) {
  let m = fieldMask(x, z, 6);
  for (const [px, pz, w, d] of plots) {
    const dx = Math.max(0, Math.abs(x - px) - w / 2), dz = Math.max(0, Math.abs(z - pz) - d / 2);
    m = Math.max(m, 1 - smooth(0, 5, Math.hypot(dx, dz)));
  }
  return m;
}

function nearPlot(x, z, plots) {
  return plots.some(([px, pz, w, d]) => Math.abs(x - px) < w / 2 + 6 && Math.abs(z - pz) < d / 2 + 6);
}
