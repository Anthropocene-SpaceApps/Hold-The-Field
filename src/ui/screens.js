// Full-screen panels. Each mirrors a desktop screen (game/.../ui/*Screen.java); the prologue and the closing line
// of the debrief carry the video script's circular hook: "the sky always warns you before it takes" ... "Now, it can."
import { t, f, date, num, language, setLanguage } from '../i18n.js';
import { BRAND } from '../brand.js';
import { CATALOG, provenance } from '../data/seasons.js';
import { FLOOD, DROUGHT, isDrought } from '../sim/config.js';
import { threeDayUpstream, recentRain, maturityOn, planningWindow, summarize, addDays } from '../sim/engine.js';
import { MODE_SCOUT, MODE_RAHIM } from '../game/session.js';
import { ALL as ADVANCEMENTS, has } from '../game/advancements.js';
import { Chart } from './chart.js';
import { COLORS, icon } from './theme.js';
import { withAlpha } from './chart.js';

const host = () => document.getElementById('screen');
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const T = (s) => esc(t(s));
// Nodi's opening lines (challenge script F6), then the debrief's callback lines
const OPEN_BN = ['আমার বয়স তখন নয় কি দশ। বাবা বলত, আকাশের মতিগতি আসমান দেখেই বুঝি।', 'কিন্তু সেই রাতে আকাশ কিছুই বুঝতে দেয়নি।'];
const OPEN_EN = ['I was nine, maybe ten. My father used to say you can read the sky\'s mood just by looking up.', 'But that night, the sky let no one understand anything.'];
const NODI_BN = ['আমার বয়স তখন নয়। বাবা বলত, আকাশ কিছু নেওয়ার আগে জানিয়ে দেয়।', 'সেই রাতে আকাশ কিছুই বলেনি।'];
const NODI_EN = ['I was nine. My father used to say the sky always warns you before it takes.', 'That night, the sky said nothing.'];
const CLOSE_BN = 'বাবা ঠিকই বলত। আকাশ জানিয়ে দেয়।';
const CLOSE_EN = 'My father was right. The sky does warn you.';
const REPO = 'https://github.com/Anthropocene-SpaceApps/Hold-The-Field';

let current = null;          // { name, render }
export const currentScreen = () => current?.name ?? null;

export function close() {
  current = null;
  const h = host();
  h.innerHTML = '';
  h.className = 'screen-host';
}

/** Re-render the open screen (after a language change). */
export function refresh() { current?.render(); }

function mount(name, html, { scrim = true } = {}) {
  const h = host();
  h.className = `screen-host${scrim ? ' scrim' : ''}`;
  h.innerHTML = html;
  h.firstElementChild?.querySelector?.('button.primary, button')?.focus({ preventScroll: true });
  return h;
}

function langSwitch() {
  return `<div class="lang" role="group" aria-label="Language"><button data-lang="en" class="${language() === 'en' ? 'on' : ''}">English</button><button data-lang="bn" class="${language() === 'bn' ? 'on' : ''}">বাংলা</button></div>`;
}
function wireLang(h) {
  h.querySelectorAll('[data-lang]').forEach((b) => b.addEventListener('click', () => setLanguage(b.dataset.lang)));
}

// ===================================================================== title

export function titleScreen({ onPlay, onAbout, onAdvancements, dataState }) {
  const render = () => {
    const ds = dataState();
    const badge = ds === 'fetching' ? t('Fetching NASA POWER data…')
      : ds === 'live' ? t('NASA POWER daily data (live)') : ds === 'nasa' ? t('NASA POWER daily data') : t('SAMPLE DATA  -  real NASA data not loaded yet');
    const h = mount('title', `
      <div class="title-screen">
        <div class="title-top">${langSwitch()}</div>
        <div class="event">${T(BRAND.event)}</div>
        <h1>${esc(BRAND.nameParts[0])}<span>${esc(BRAND.nameParts[1])}</span></h1>
        <div class="bar"></div>
        <p class="tagline">${T(BRAND.tagline)}</p>
        <p class="blurb">${T('A farm\'s strategic partner built on real NASA data. The climate raids your farm, and you cannot let it win. Replay a real flash flood or drought, day by day.')}</p>
        <div class="title-menu">
          <button class="btn primary big" id="tPlay">${T('Play')}</button>
          <button class="btn" id="tAbout">${T('Data & Model')}</button>
          <button class="btn" id="tAdv">${T('Advancements')}</button>
        </div>
        <div class="title-foot">
          <span>${esc(BRAND.name)} ${esc(BRAND.version)}   |   ${T(BRAND.team)}   |   <a href="${REPO}/releases" target="_blank" rel="noopener">${T('Desktop edition')}</a> · <a href="${REPO}" target="_blank" rel="noopener">GitHub</a></span>
          <span class="data-badge ${ds}">${esc(badge)}</span>
        </div>
      </div>`, { scrim: false });
    wireLang(h);
    h.querySelector('#tPlay').onclick = onPlay;
    h.querySelector('#tAbout').onclick = onAbout;
    h.querySelector('#tAdv').onclick = onAdvancements;
  };
  current = { name: 'title', render };
  render();
}

// ===================================================================== prologue

// The video's cold open, built from the real season: the satellite saw it, nobody was listening.
// Lines are computed from the data file (NASA POWER), never typed in by hand.
export function satelliteLines(season, cfg, listening = false) {
  const days = season.days;
  const i = days.findIndex((d) => d.date >= '2017-03-31');
  const at = i < 0 ? days.length - 1 : i;
  const rain3 = threeDayUpstream(days, at);
  const ripe = addDays(cfg.defaultTransplant, cfg.varieties.long.fieldDays);
  const left = Math.max(0, Math.round((Date.parse(ripe) - Date.parse(days[at].date)) / 86400000));
  const stamp = date(days[at].date).toUpperCase();
  return [
    season.sample ? 'SAMPLE DATA · NOT A REAL MEASUREMENT' : 'NASA POWER · DAILY SATELLITE RAINFALL',
    stamp,
    f('RAINFALL, MEGHALAYA HILLS: {} MM IN 72 H', Math.round(rain3)),
    'DOWNSTREAM: SUNAMGANJ HAOR',
    listening ? 'BORO RICE: HARVESTED' : f('BORO RICE: {} DAYS TO HARVEST', left),
    listening ? 'SOMEONE IS LISTENING.' : 'NO ONE IS LISTENING.',
  ];
}

/** Type lines into an element one by one, like the satellite terminal in the film. */
export function typeLines(el, lines, { speed = 22, onLine } = {}) {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  el.textContent = '';
  if (reduce) { el.textContent = lines.join('\n'); return; }
  let li = 0, ci = 0;
  const tick = () => {
    if (!el.isConnected || li >= lines.length) return;
    if (ci === 0 && li > 0) el.textContent += '\n';
    el.textContent += lines[li][ci++] ?? '';
    if (ci >= lines[li].length) { li++; ci = 0; onLine?.(li); setTimeout(tick, 380); } else setTimeout(tick, speed);
  };
  tick();
}

export function prologueScreen({ season, cfg, onContinue, onBack, onLine }) {
  const seedbed = BRAND.seedbedDays, vs = (cfg || FLOOD).varieties;
  const longDays = vs.long.fieldDays + seedbed, shortDays = vs.short.fieldDays + seedbed;   // seed to harvest
  const render = () => {
    const h = mount('prologue', `
      <section class="card panel prologue" role="dialog" aria-label="Prologue">
        <pre class="sat" id="satText" aria-label="Satellite log"></pre>
        <div class="quote-bn" lang="bn">${OPEN_BN[0]}<br>${OPEN_BN[1]}</div>
        <div class="quote-en quote">“${esc(OPEN_EN[0])}<br>${esc(OPEN_EN[1])}”</div>
        <div class="who">${T('Nodi, Hashem\'s daughter, remembering 2017')}</div>
        <p>${f('You might think Hashem lost his harvest in April. But it was in December, when he chose a rice that needs {} days instead of a {}-day one, which would have been home before the flood had a chance to ruin it.', num(longDays), num(shortDays))}</p>
        <p>${T('Go back to December. Choose the seed and the date, read the NASA data, and see if the rice is home before the water comes.')}</p>
        <p class="note">${T('Hashem and Nodi are composite characters based on real haor farmers\' experiences. The rain is real NASA data.')}</p>
        <div class="panel-foot"><button class="btn" id="pBack">${T('Back')}</button><button class="btn primary" id="pGo">${T('Back to December')}</button></div>
      </section>`);
    h.querySelector('#pGo').onclick = onContinue;
    h.querySelector('#pBack').onclick = onBack;
    if (season) typeLines(h.querySelector('#satText'), satelliteLines(season, cfg), { onLine });
  };
  current = { name: 'prologue', render };
  render();
}

// ===================================================================== plan the season

export function planScreen({ initial, onStart, onBack }) {
  const sel = { scenario: initial?.scenario ?? 'haor-2017', mode: initial?.mode ?? MODE_SCOUT, variety: initial?.variety ?? 'short', dateIdx: initial?.dateIdx ?? 1 };
  const cfgOf = () => (CATALOG.find((c) => c.id === sel.scenario)?.hazard === 'drought' ? DROUGHT : FLOOD);
  const render = () => {
    const cfg = cfgOf(), dry = isDrought(cfg), scout = sel.mode === MODE_SCOUT;
    const ripe = (idx, v = sel.variety) => addDays(cfg.transplantOptions[idx][0], cfg.varieties[v].fieldDays);
    const v = cfg.varieties[scout ? sel.variety : 'long'];
    const plant = scout ? cfg.transplantOptions[sel.dateIdx][0] : cfg.defaultTransplant;
    const ripeDate = addDays(plant, v.fieldDays);
    const plan = scout
      ? f(dry ? '{}, transplanted {}, ripe about {}. Irrigate when the scout warns: the soil must stay wet while the rice flowers.'
        : '{}, transplanted {}, ripe about {}. You can harvest from 80% maturity, so a warning can still save part of the crop.', t(v.label), date(plant), date(ripeDate))
      : t(dry ? 'Standard rice transplanted on the usual date, no satellite warning, no irrigation. You watch the real weather decide.'
        : 'Long-duration rice transplanted on the usual date, no satellite warning, no actions. You watch the real rain decide.');
    const why = t(dry
      ? 'In the Barind Tract the monsoon can fail in the weeks when rice flowers. A tolerant variety copes with dry soil but yields less; the NASA soil and rain data tell you when to irrigate.'
      : 'In flood-prone haors the rain that arrives in spring comes from the hills upstream. Rice that ripens earlier can be harvested before the water arrives; rice that ripens later can only be saved by watching the data.');
    const h = mount('plan', `
      <section class="card panel" style="--w:1000px" role="dialog" aria-label="Plan the Season">
        <h2>${T('Plan the Season')}</h2>
        <div class="label section-label">${T('Scenario')}</div>
        <div class="choices cols-3">${CATALOG.map((c) => `<button class="choice ${c.locked ? 'locked' : ''} ${sel.scenario === c.id ? 'on' : ''}" data-scn="${c.id}" ${c.locked ? 'disabled' : ''}><b>${T(c.name)}</b><span>${T(c.tag)}</span></button>`).join('')}</div>
        <div class="plan-grid">
          <div>
            <div class="label section-label">${T('How you play')}</div>
            <div class="choices cols-2">
              <button class="choice ${scout ? 'on' : ''}" data-mode="${MODE_SCOUT}"><b>${T('Scout mode')}</b><span>${T('You farm with the satellite scout')}</span></button>
              <button class="choice ${!scout ? 'on' : ''}" data-mode="${MODE_RAHIM}"><b>${T("Rahim's way")}</b><span>${T('Watch a farmer with no warning')}</span></button>
            </div>
            <div class="label section-label">${T('Rice variety')}</div>
            <div class="choices cols-2">${['short', 'long'].map((k) => {
              const vv = cfg.varieties[k];
              return `<button class="choice variety ${scout && sel.variety === k ? 'on' : ''}" data-var="${k}" ${scout ? '' : 'disabled'}>
                <b>${T(vv.label)}</b><span>${T(vv.note)}</span>
                <span class="label" style="margin-top:12px">${T('Days in the field')}</span><span class="days">${num(vv.fieldDays)}</span>
                ${dry ? '' : `<span class="seedline">${f('{} days from seed, seedbed included', num(vv.fieldDays + BRAND.seedbedDays))}</span>`}
                <span class="label">${T('Yield potential')}</span><div class="yield-bar"><i style="width:${vv.potential * 100}%"></i></div>
                <span>${f('{}% of full yield', Math.round(vv.potential * 100))}</span></button>`;
            }).join('')}</div>
          </div>
          <div>
            <div class="label section-label">${T('Transplanting date')}</div>
            <div class="choices">${cfg.transplantOptions.map((o, i) => `<button class="choice ${scout && sel.dateIdx === i ? 'on' : ''}" data-date="${i}" ${scout ? '' : 'disabled'}><b>${T(o[1])}</b><span>${f('ripe about {}', date(ripe(i)))}</span></button>`).join('')}</div>
          </div>
        </div>
        <div class="advice">
          <b>${T(scout ? 'Your plan' : 'Rahim farms the way his father did')}</b>
          <p>${esc(plan)}</p>
          <div class="why">${T('Why it matters')}</div><p>${esc(why)}</p>
        </div>
        <div class="panel-foot"><button class="btn" id="plBack">${T('Back')}</button><button class="btn primary big" id="plStart">${T('Start Season')}</button></div>
      </section>`);
    h.querySelectorAll('[data-scn]').forEach((b) => b.onclick = () => { sel.scenario = b.dataset.scn; sel.dateIdx = 1; render(); });
    h.querySelectorAll('[data-mode]').forEach((b) => b.onclick = () => { sel.mode = b.dataset.mode; render(); });
    h.querySelectorAll('[data-var]').forEach((b) => b.onclick = () => { sel.variety = b.dataset.var; render(); });
    h.querySelectorAll('[data-date]').forEach((b) => b.onclick = () => { sel.dateIdx = Number(b.dataset.date); render(); });
    h.querySelector('#plBack').onclick = onBack;
    h.querySelector('#plStart').onclick = () => {
      const c = cfgOf(), sc = sel.mode === MODE_SCOUT;
      onStart({ ...sel, plantDate: sc ? c.transplantOptions[sel.dateIdx][0] : c.defaultTransplant, variety: sc ? sel.variety : 'long' });
    };
  };
  current = { name: 'plan', render };
  render();
}

// ===================================================================== dashboard

export function dashboardScreen({ session, onClose }) {
  const render = () => {
    const s = session.season, st = session.state, cfg = session.cfg, dry = isDrought(cfg), n = s.days.length;
    const col = (fn) => s.days.map(fn);
    const dates = col((d) => date(d.date));
    const hist = (key) => s.days.map((_, i) => (i < st.startIndex && key === 'level' ? 0 : st.history[Math.max(0, Math.min(st.history.length - 1, i - st.startIndex))][key]));
    const base = (c) => Object.assign(c, { days: n, visibleUntil: st.i, cursorDay: st.i, dates, firstDay: st.startIndex });
    const charts = [];
    if (dry) {
      charts.push(base(new Chart('Rain at the farm (mm/day)')).areaOf('Rain', col((d) => d.rainFarm), COLORS.rain, 'mm'));
      charts.push(base(new Chart('Scout index: rain of the last 7 days (mm)')).line('7-day rain', s.days.map((_, i) => recentRain(s.days, i, 7)), COLORS.text, 'mm').hline(cfg.dryWatchMm, COLORS.warn, 'WATCH').hline(cfg.dryWarnMm, COLORS.bad, 'WARNING'));
      const moist = base(new Chart("Soil wetness vs the crop's stress line"));
      moist.decimals = 2; moist.yMax = 1;
      charts.push(moist.areaOf('Soil + irrigation', hist('level'), COLORS.water).stepped('Stress line', hist('bund'), COLORS.soil));
    } else {
      charts.push(base(new Chart('Rain: hills upstream vs farm (mm/day)')).areaOf('Upstream', col((d) => d.rainUp), COLORS.rain, 'mm').line('Farm', col((d) => d.rainFarm), COLORS.good, 'mm'));
      charts.push(base(new Chart('Scout index: 3-day upstream rain (mm)')).line('3-day sum', s.days.map((_, i) => threeDayUpstream(s.days, i)), COLORS.text, 'mm').hline(cfg.watchMm, COLORS.warn, 'WATCH').hline(cfg.warningMm, COLORS.bad, 'WARNING'));
      const water = base(new Chart('Floodwater vs your embankment (m)'));
      water.decimals = 2;
      charts.push(water.areaOf('Water', hist('level'), COLORS.water, 'm').stepped('Embankment', hist('bund'), COLORS.soil, 'm'));
    }
    const temp = base(new Chart('Maximum temperature (C)')).line('Tmax', col((d) => d.tmax), COLORS.temp, 'C');
    if (dry) temp.hline(cfg.heatC, COLORS.bad, 'HEAT');
    charts.push(temp);
    const soil = base(new Chart('Root-zone soil wetness (0-1)'));
    soil.decimals = 2; soil.yMax = 1;
    charts.push(soil.areaOf('Soil wetness', col((d) => d.soil), COLORS.soil));
    const mat = base(new Chart('Rice maturity (%), harvest from 80%'));
    mat.yMax = 100;
    charts.push(mat.line(cfg.varieties.long.label, col((d) => maturityOn(d.date, 'long', st.transplant, cfg) * 100), session.variety === 'long' ? COLORS.crop : '#8a7a4a', '%')
      .line(cfg.varieties.short.label, col((d) => maturityOn(d.date, 'short', st.transplant, cfg) * 100), session.variety === 'short' ? COLORS.good : '#4a7a5a', '%')
      .hline(80, COLORS.text, '80%'));

    const prov = provenance(s);
    const h = mount('dashboard', `
      <section class="card panel dashboard" role="dialog" aria-label="NASA Satellite Scout">
        <div class="dash-head">
          <div><h2>${T('NASA Satellite Scout')}</h2>
          <p class="sub">${esc(date(st.date))}   |   ${f('day {} of {}', st.i + 1, n)}   |   ${T(prov === 'sample' ? 'SAMPLE DATA' : 'NASA POWER')}   |   ${T('hover a chart for values')}</p></div>
          <button class="btn" id="dClose">${T('Close')}</button>
        </div>
        <div class="dash-grid">${charts.map(() => '<div></div>').join('')}</div>
      </section>`);
    const cells = h.querySelectorAll('.dash-grid > div');
    charts.forEach((c, i) => c.mount(cells[i]));
    requestAnimationFrame(() => charts.forEach((c) => c.draw()));
    h.querySelector('#dClose').onclick = onClose;
  };
  current = { name: 'dashboard', render };
  render();
}

// ===================================================================== debrief

export function debriefScreen({ session, other, onAgain, onPlan, onReport, onLook, onTitle }) {
  const noWarning = planningWindow(session.season, session.cfg, 'rahim');
  const withScout = planningWindow(session.season, session.cfg, 'scout');
  const render = () => {
    const st = session.state, cfg = session.cfg, dry = isDrought(cfg), s = session.season, n = s.days.length;
    const rahim = session.mode === MODE_RAHIM;
    const sum = summarize(st);
    const best = (plans) => plans.reduce((a, b) => (b.yieldPct > (a?.yieldPct ?? -1) ? b : a), null);
    const bestNo = best(noWarning), bestScout = best(withScout);
    const planLabel = (p) => `${t(cfg.varieties[p.variety].label)}, ${t(p.label)}`;
    const insights = [];
    if (sum.leadDays != null) insights.push(f(dry ? 'NASA rain and soil data gave a warning {} day(s) before the crop felt the dry soil.' : 'NASA rain data in the hills gave a warning {} day(s) before the water reached the embankment.', sum.leadDays));
    else if (sum.floodDate) insights.push(t(dry ? 'The dry spell hit without a scout warning ahead of it.' : 'The flood arrived without a scout warning ahead of it.'));
    else insights.push(t(dry ? 'The crop never suffered from dry soil on this plan.' : 'No flood reached the embankment on this plan.'));
    if (bestNo) insights.push(f('With no warning at all, the best plan keeps {}%: {}.', Math.round(bestNo.yieldPct * 100), planLabel(bestNo)));
    if (bestScout) insights.push(f('Acting on the scout, the best plan keeps {}%: {}.', Math.round(bestScout.yieldPct * 100), planLabel(bestScout)));
    if (dry && bestNo && bestScout && bestNo.variety === 'short' && bestScout.variety === 'long') insights.push(t('Without a warning the drought-tolerant variety is the safer choice; with the scout and well-timed irrigation the standard variety wins.'));
    else if (!dry && bestNo && bestNo.variety === 'short' && bestNo.transplant !== cfg.transplantOptions.at(-1)[0]) insights.push(t('Ripening earlier let the rice come in before the spring water: timing is itself an adaptation.'));
    else if (bestNo && bestNo.yieldPct < 0.5) insights.push(t('Without a warning, every plan here lost most of the crop: early warning, not the calendar, is what protects the harvest.'));

    const result = (label, y, color) => `<div class="result"><div class="who">${T(label)}</div><div class="pct" style="color:${color}">${f('{}%', Math.round(y * 100))}</div><div class="small faint">${T('of the harvest saved')}</div><div class="bar"><i style="width:${Math.min(1, y) * 100}%;background:${color}"></i></div></div>`;
    const grid = (title, plans) => {
      const cols = cfg.transplantOptions;
      const rows = ['short', 'long'].map((v) => `<tr><td class="name">${T(v === 'short' ? (dry ? 'Tolerant rice' : 'Short rice') : (dry ? 'Standard rice' : 'Long rice'))}</td>${cols.map(([d]) => {
        const p = plans.find((x) => x.variety === v && x.transplant === d);
        if (!p) return '<td class="cell none"></td>';
        const mine = p.variety === session.variety && p.transplant === st.transplant;
        return `<td class="cell ${mine ? 'mine' : ''}" style="background:${withAlpha(lerpColor('#b5453a', '#4fbf7e', Math.min(1, p.yieldPct)), 0.8)}">${f('{}%', Math.round(p.yieldPct * 100))}</td>`;
      }).join('')}</tr>`).join('');
      return `<b style="font-size:13px">${T(title)}</b><table class="plan-table"><tr><th></th>${cols.map((o) => `<th>${T(o[1].replace(/ \(.*/, ''))}</th>`).join('')}</tr>${rows}</table>`;
    };
    const events = st.events.filter((e) => e.type !== 'watch').slice(-7);
    const evColor = { warning: COLORS.bad, loss: COLORS.bad, flood: COLORS.water, stress: COLORS.temp, harvest: COLORS.good };
    const beat = st.yieldPct > other.yieldPct;
    const saved = !rahim && (beat || st.yieldPct > 0.5);
    const closing = rahim
      ? `<div class="mono">${T('NO ONE IS LISTENING.')}</div><div class="bn" lang="bn">${NODI_BN[1]}</div><div class="en quote">“${esc(NODI_EN[1])}”</div>`
      : saved
        ? `<div class="mono">${T('SOMEONE IS LISTENING.')}</div><div class="bn" lang="bn">${CLOSE_BN}</div><div class="en quote">“${esc(CLOSE_EN)}”</div><div class="now">${T('This time, someone was listening.')}</div>`
        : `<div class="mono">${T('DECEMBER AGAIN.')}</div><div class="en">${T('The water still came first. Go back to December: a rice that ripens sooner, then act on the warning.')}</div>`;
    const p = s.points;
    const source = f('Data: NASA POWER daily (PRECTOTCORR, T2M_MAX, GWETROOT) · farm {} N {} E{} · {} to {}{}',
      p.farm.lat.toFixed(2), p.farm.lon.toFixed(2), dry ? '' : f(' · upstream {} N {} E', p.upstream.lat.toFixed(2), p.upstream.lon.toFixed(2)),
      date(s.days[0].date), date(s.days.at(-1).date), s.sample ? ` · ${t('SAMPLE DATA')}` : '');

    const h = mount('debrief', `
      <section class="card panel debrief" role="dialog" aria-label="Season debrief">
        <div class="debrief-head">
          <div><h1>${T(st.yieldPct > 0 ? 'The rice is home' : dry ? 'The drought took it all' : 'The water took it all')}</h1>
          <p class="sub">${T(dry ? 'Same field, same real weather. The difference is whether the warning reached the farmer, and how the season was planned.' : 'Same field, same real rain. The difference is whether the warning reached the farmer, and how the season was planned.')}</p><p class="source">${esc(source)}</p></div>
          <div class="closing">${closing}</div>
        </div>
        <div class="debrief-grid">
          <div>
            ${result(rahim ? "Rahim's way (no scout)" : 'You, with the scout', st.yieldPct, COLORS.crop)}
            ${result(rahim ? 'With the scout' : "Rahim's way (no scout)", other.yieldPct, '#8ab4f0')}
            <div class="label section-label">${T('What the data showed')}</div>
            <ul class="insights">${insights.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>
          </div>
          <div>
            <div id="dbChart" style="height:250px"></div>
            <div class="label section-label">${T('Timeline')}</div>
            <ul class="timeline-list">${events.map((e) => `<li><i style="background:${evColor[e.type] || COLORS.text}"></i><time>${esc(date(e.date))}</time><span>${T(e.text)}</span></li>`).join('')}</ul>
          </div>
          <div>
            <div class="label" style="margin-bottom:8px">${T('Every plan, same real weather')}</div>
            ${grid('No warning, no action', noWarning)}
            <div style="height:12px"></div>
            ${grid('Acting on the scout', withScout)}
            <div class="small faint">${T('White outline = your plan')}</div>
          </div>
        </div>
        <div class="panel-foot">
          <button class="btn primary" id="eAgain">${T('Play again')}</button>
          <button class="btn" id="ePlan">${T('Back to December')}</button>
          <button class="btn" id="eReport">${T('Save report')}</button>
          <button class="btn" id="eLook">${T('Look around')}</button>
          <button class="btn" id="eTitle">${T('Title screen')}</button>
        </div>
      </section>`);
    const chart = new Chart(dry ? "Soil wetness vs the crop's stress line" : 'Floodwater vs your embankment (m)');
    const dates = s.days.map((d) => date(d.date));
    const level = s.days.map((_, i) => (i < st.startIndex ? 0 : st.history[Math.min(st.history.length - 1, i - st.startIndex)].level));
    const bund = s.days.map((_, i) => st.history[Math.max(0, Math.min(st.history.length - 1, i - st.startIndex))].bund);
    Object.assign(chart, { days: n, visibleUntil: st.i, cursorDay: st.i, dates, decimals: 2, firstDay: st.startIndex });
    if (dry) { chart.yMax = 1; chart.areaOf('Soil + irrigation', level, COLORS.water).stepped('Stress line', bund, COLORS.soil); }
    else chart.areaOf('Water', level, COLORS.water, 'm').stepped('Embankment', bund, COLORS.soil, 'm');
    for (const e of st.events) {
      const c = { warning: COLORS.bad, flood: COLORS.water, stress: COLORS.temp, harvest: COLORS.good, loss: '#cccccc' }[e.type];
      if (c) chart.vline(e.i, c, e.type);
    }
    chart.mount(h.querySelector('#dbChart'));
    requestAnimationFrame(() => chart.draw());
    h.querySelector('#eAgain').onclick = onAgain;
    h.querySelector('#ePlan').onclick = onPlan;
    h.querySelector('#eReport').onclick = () => onReport(noWarning, withScout);
    h.querySelector('#eLook').onclick = onLook;
    h.querySelector('#eTitle').onclick = onTitle;
  };
  current = { name: 'debrief', render };
  render();
}

// ===================================================================== data & model

export function aboutScreen({ seasons, onClose, onRefetch, onUseBundled, fetching }) {
  const render = () => {
    const f2 = (v) => Number(v).toFixed(2);
    const blocks = [];
    for (const s of seasons()) {
      if (!s) continue;
      const c = s.hazard === 'drought' ? DROUGHT : FLOOD, dry = isDrought(c);
      const where = t(dry ? 'Barind Tract' : 'Sunamganj haor');
      const farm = f('farm {} N {} E', f2(s.points.farm.lat), f2(s.points.farm.lon));
      const pts = dry ? farm : f('farm {} N {} E, upstream {} N {} E', f2(s.points.farm.lat), f2(s.points.farm.lon), f2(s.points.upstream.lat), f2(s.points.upstream.lon));
      let data = f('NASA POWER Daily API (community AG): precipitation (PRECTOTCORR), maximum temperature (T2M_MAX) and root-zone soil wetness (GWETROOT), {} to {}. Points: {} ({}).',
        s.days[0].date, s.days.at(-1).date, pts, t(dry ? 'the Barind Tract in north-western Bangladesh' : 'Sunamganj haor and the Meghalaya hills above it'));
      const prov = provenance(s);
      data += '\n' + (prov === 'sample' ? t('THIS COPY IS SAMPLE DATA, NOT REAL. Press the button below to load the real season from NASA POWER in this browser.')
        : prov === 'live' ? f('Fetched live from NASA POWER in this browser on {}.', date(s.fetched.slice(0, 10))) : t('Real NASA POWER data committed to the repository.'));
      blocks.push([f('Where the data comes from: {}', where), data, s.id, prov]);
      const sv = c.varieties.short, lv = c.varieties.long;
      const model = dry
        ? [f('The soil is a simple store: NASA\'s root-zone wetness plus any irrigation you add, which fades over a few days. The rice is stressed whenever that wetness falls below its stress line ({} for standard, {} for drought-tolerant).', f2(lv.stressSoil), f2(sv.stressSoil)),
          f('Stress piles up on dry days, fastest during flowering and grain filling, and more when it is hotter than {} C. The crop fails when stress reaches 100%; otherwise the harvest is reduced by the stress it has collected.', c.heatC),
          f('The satellite scout warns from the rain of the last 7 days and today\'s soil wetness: Watch under {} mm of rain, Drought Warning under {} mm.', c.dryWatchMm, c.dryWarnMm),
          f("Irrigating costs Tk {} and one load from the village tank ({} loads at most; rain refills it). Rice matures linearly after transplanting ({} days drought-tolerant, {} standard). You may harvest from {}% maturity. Yield = maturity x the variety's potential ({}% tolerant, {}% standard) x what the stress leaves; game parameters, not agronomic advice.",
            c.irrigationCost, c.tankMax, sv.fieldDays, lv.fieldDays, c.minHarvestMaturity * 100, Math.round(sv.potential * 100), Math.round(lv.potential * 100))]
        : [f('Water level is a simple bucket, in metres above the field: rain in the hills upstream (arriving {} days later) and rain on the farm fill it, drainage empties it.', c.lagDays),
          f('A flash flood starts when the water rises above the embankment ({} m, +{} m per raise, up to {} raises). The crop dies after {} days under water.', c.bundStart, c.bundRaise, c.maxBundRaises, c.daysUnderwaterToKill),
          f('The satellite scout warns from the 3-day total of upstream rain: Watch at {} mm, Flood Warning at {} mm.', c.watchMm, c.warningMm),
          f("Rice matures linearly after transplanting ({} days for short-duration, {} for long-duration). You may harvest from {}% maturity. Yield = maturity x the variety's potential ({}% for short, {}% for long; game parameters, not agronomic advice).",
            sv.fieldDays, lv.fieldDays, c.minHarvestMaturity * 100, Math.round(sv.potential * 100), Math.round(lv.potential * 100))];
      blocks.push([f('How the model works: {}', where), model.join('\n')]);
    }
    blocks.push([t('What it leaves out, on purpose'), t('One bucket for the whole field, or one soil layer: no river routing, terrain, embankment failure, groundwater, canals or pumps. Rain from a single point stands in for the whole catchment or district. Crop growth is linear: no fertiliser, pests or disease.\nIt is a learning game calibrated to one real event per scenario, not a forecast.')]);
    blocks.push([t('How to play'), t('Drag to look around, scroll or pinch to zoom. Space starts and pauses time; + and - change the speed. 1: raise the embankment, or irrigate in the drought scenario. 2: harvest (from 80% maturity). 3 or M: NASA satellite view. 4 or E: scout dashboard. Click Rahim to hear what he thinks. Esc opens the menu.')]);
    blocks.push([t('Rahim'), t("Rahim is a composite character based on real haor farmers' experiences. The seasons are real; his story stands in for many.")]);
    blocks.push([t('Team Anthropocene'), t('Alif, Safwat, Yasin, Zawad, Yaminur and Marwa. NASA Space Apps Challenge 2026, Chattogram, Bangladesh. Challenge: Field Shift. Data: NASA POWER. Every model, texture and sound in this game is generated by code.')]);

    const h = mount('about', `
      <section class="card panel about" role="dialog" aria-label="Data and model">
        <h2>${T('Data and model')}</h2>
        ${blocks.map(([head, body, id, prov]) => `<h3>${esc(head)}</h3><p>${esc(body)}</p>${id ? `<div class="data-row">
          <span class="data-badge ${fetching(id) ? 'fetching' : prov}">${T(fetching(id) ? 'Fetching NASA POWER data…' : prov === 'sample' ? 'SAMPLE DATA' : prov === 'live' ? 'NASA POWER (live)' : 'NASA POWER')}</span>
          ${prov !== 'nasa' ? `<button class="btn" data-refetch="${id}">${T('Fetch from NASA POWER now')}</button>` : ''}
          ${prov === 'live' ? `<button class="btn" data-bundled="${id}">${T('Use the bundled file')}</button>` : ''}</div>` : ''}`).join('')}
        <div class="panel-foot"><span></span><button class="btn primary" id="aClose">${T('Done')}</button></div>
      </section>`);
    h.querySelector('#aClose').onclick = onClose;
    h.querySelectorAll('[data-refetch]').forEach((b) => b.onclick = () => onRefetch(b.dataset.refetch));
    h.querySelectorAll('[data-bundled]').forEach((b) => b.onclick = () => onUseBundled(b.dataset.bundled));
  };
  current = { name: 'about', render };
  render();
}

// ===================================================================== advancements

export function advancementsScreen({ onClose }) {
  const render = () => {
    const h = mount('advancements', `
      <section class="card panel" style="--w:640px" role="dialog" aria-label="Advancements">
        <h2>${T('Advancements')}</h2>
        <p class="sub">${f('{} of {} earned', ADVANCEMENTS.filter((a) => has(a.id)).length, ADVANCEMENTS.length)}</p>
        <div class="adv-list">${ADVANCEMENTS.map((a) => `<div class="adv ${has(a.id) ? 'got' : ''}"><div class="medal">${icon('wheat')}</div><div><b>${T(a.title)}</b><span>${T(a.description)}</span></div></div>`).join('')}</div>
        <div class="panel-foot"><span></span><button class="btn primary" id="advClose">${T('Done')}</button></div>
      </section>`);
    h.querySelector('#advClose').onclick = onClose;
  };
  current = { name: 'advancements', render };
  render();
}

// ===================================================================== pause menu

export function menuScreen({ onResume, onRestart, onPlan, onAbout, onTitle }) {
  const render = () => {
    const h = mount('menu', `
      <section class="card panel menu-panel" role="dialog" aria-label="Menu">
        <h2>${T('Game Paused')}</h2>
        <button class="btn primary" id="mResume">${T('Resume')}</button>
        <button class="btn" id="mRestart">${T('Restart this season')}</button>
        <button class="btn" id="mPlan">${T('Plan another season')}</button>
        <button class="btn" id="mAbout">${T('Data & Model')}</button>
        <div style="display:flex;justify-content:center;margin:4px 0">${langSwitch()}</div>
        <button class="btn" id="mTitle">${T('Title screen')}</button>
      </section>`);
    wireLang(h);
    h.querySelector('#mResume').onclick = onResume;
    h.querySelector('#mRestart').onclick = onRestart;
    h.querySelector('#mPlan').onclick = onPlan;
    h.querySelector('#mAbout').onclick = onAbout;
    h.querySelector('#mTitle').onclick = onTitle;
  };
  current = { name: 'menu', render };
  render();
}

function lerpColor(a, b, k) {
  const pa = parseInt(a.slice(1), 16), pb = parseInt(b.slice(1), 16);
  const ch = (s) => Math.round(((pa >> s) & 255) + (((pb >> s) & 255) - ((pa >> s) & 255)) * k);
  return `#${((ch(16) << 16) | (ch(8) << 8) | ch(0)).toString(16).padStart(6, '0')}`;
}
