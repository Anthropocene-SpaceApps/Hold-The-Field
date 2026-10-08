// Loads a precomputed season file from /data. The game never calls an API live.
export async function loadSeason(name = 'haor-2017') {
  const res = await fetch(`data/${name}.json`);
  if (!res.ok) throw new Error(`Could not load data/${name}.json (${res.status})`);
  const data = await res.json();
  if (!Array.isArray(data.days) || data.days.length === 0) throw new Error('Season file has no days');
  for (const k of ['date', 'rainUp', 'rainFarm', 'tmax', 'soil']) {
    if (!(k in data.days[0])) throw new Error(`Season file is missing "${k}"`);
  }
  return data;
}
