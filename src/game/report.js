// Season report as a text file and a CSV (port of game/ReportExporter.java), downloaded in the browser.
import { summarize } from '../sim/engine.js';
import { isDrought } from '../sim/config.js';
import { BRAND } from '../brand.js';
import { MODE_RAHIM } from './session.js';

export function reportText(session, baseline, noWarning, withScout) {
  const st = session.state, cfg = session.cfg, sum = summarize(st), dry = isDrought(cfg);
  const pad = (s, n) => String(s).padEnd(n);
  const lines = [
    `${BRAND.name.toUpperCase()} - SEASON REPORT`, '='.repeat(BRAND.name.length + 16), '',
    `Season:      ${session.season.id}${session.season.sample ? '  (SAMPLE DATA, not real measurements)' : '  (NASA POWER daily data)'}`,
    `Mode:        ${session.mode === MODE_RAHIM ? "Rahim's way (no warning, no actions)" : 'Scout mode'}`,
    `Variety:     ${cfg.varieties[session.variety].label}, transplanted ${st.transplant}`, '',
    'RESULT', '------',
    `Harvest saved:          ${Math.round(st.yieldPct * 100)}%`,
    `Same field, no warning: ${Math.round(baseline.yieldPct * 100)}%`,
    `${dry ? 'Crop lost to drought:   ' : 'Crop lost to flood:     '}${sum.lost ? 'yes' : 'no'}`,
  ];
  if (sum.warningDate) lines.push(`First scout warning:    ${sum.warningDate}`);
  if (sum.floodDate) lines.push(`${dry ? 'First crop stress:      ' : 'Water over embankment:  '}${sum.floodDate}`);
  if (sum.leadDays != null) lines.push(`Warning lead time:      ${sum.leadDays} day(s)`);
  lines.push(`Actions taken:          ${sum.actions}`, '');
  lines.push('EVERY PLAN ON THE SAME REAL WEATHER (share of the harvest kept)', '-'.repeat(63));
  lines.push(`${pad('Plan', 34)} ${pad('No warning', 14)} Acting on scout`);
  noWarning.forEach((a, i) => {
    lines.push(`${pad(`${cfg.varieties[a.variety].label}, ${a.label}`, 34)} ${pad(`${Math.round(a.yieldPct * 100)}%`, 14)} ${Math.round(withScout[i].yieldPct * 100)}%`);
  });
  lines.push('', `How to read this: it is a learning game built on a simplified model (${dry ? 'one soil layer, one rain point,' : 'one water bucket, one upstream rain point,'}`,
    'linear crop growth). Yield potentials are game parameters, not agronomic advice. Use it to discuss timing and warning,',
    'not to predict a real harvest.', '', `Data: ${session.season.source}. ${BRAND.name} by ${BRAND.team}, NASA Space Apps Challenge 2026.`);
  return lines.join('\n');
}

export function reportCsv(session) {
  const st = session.state, dry = isDrought(session.cfg);
  const rows = [dry
    ? 'date,rain_upstream_mm,rain_farm_mm,tmax_c,soil_wetness,soil_moisture_with_irrigation,crop_stress_line'
    : 'date,rain_upstream_mm,rain_farm_mm,tmax_c,soil_wetness,water_level_m,embankment_m'];
  for (let i = st.startIndex; i <= st.i && i < session.season.days.length; i++) {
    const d = session.season.days[i], p = st.history[Math.min(st.history.length - 1, i - st.startIndex)];
    rows.push([d.date, d.rainUp.toFixed(1), d.rainFarm.toFixed(1), d.tmax.toFixed(1), d.soil.toFixed(3), p.level.toFixed(3), p.bund.toFixed(2)].join(','));
  }
  return rows.join('\n') + '\n';
}

export function download(name, text, type = 'text/plain') {
  const url = URL.createObjectURL(new Blob([text], { type: `${type};charset=utf-8` }));
  const a = Object.assign(document.createElement('a'), { href: url, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}
