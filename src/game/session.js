// One run of a season: the sim state, the clock and the actions taken (port of game/Session.java).
import { createState, step, act, autoplay, isOver } from '../sim/engine.js';
import { configForSeason } from '../sim/config.js';

export const SPEEDS = [1, 2, 4, 8];          // simulated days per real second
export const MODE_SCOUT = 'scout', MODE_RAHIM = 'rahim';

export class Session {
  constructor(season, mode, variety, plantDate) {
    this.season = season;
    this.cfg = configForSeason(season);
    this.mode = mode;
    this.variety = mode === MODE_RAHIM ? 'long' : variety;
    this.plantDate = plantDate || this.cfg.defaultTransplant;
    this.state = createState(season, this.cfg, this.variety, this.plantDate);
    this.speedIdx = 0;
    this.paused = true;
    this.acc = 0;
    this.processed = 0;
    this.endShown = false;
    this.endTimer = 0;
  }

  get day() { return this.season.days[this.state.i]; }
  get over() { return isOver(this.state); }
  actionsAllowed() { return this.mode === MODE_SCOUT; }

  /** Advance the clock; returns true when at least one day passed. */
  tick(dt) {
    if (this.paused || this.over) return false;
    this.acc += dt * SPEEDS[this.speedIdx];
    let moved = false;
    while (this.acc >= 1 && !this.over) {
      this.acc -= 1;
      this.state = step(this.state, this.season, this.cfg);
      moved = true;
    }
    return moved;
  }

  act(type) {
    if (!this.actionsAllowed()) return { ok: false, reason: "Rahim's way: you can only watch this season" };
    const r = act(this.state, type, this.cfg);
    if (r.ok) this.state = r.state;
    return r;
  }

  /** Events produced since the last call. */
  drainEvents() {
    const fresh = this.state.events.slice(this.processed);
    this.processed = this.state.events.length;
    return fresh;
  }

  /** The other way to farm the same season, for the debrief comparison. */
  baseline() {
    const rahim = this.mode === MODE_RAHIM;
    return autoplay(this.season, this.cfg, rahim ? 'scout' : 'rahim', rahim ? 'short' : 'long', this.state.transplant);
  }
}
