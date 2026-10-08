# Hold the Field *(working title)*

**A strategy game where the climate raids your farm and NASA satellite data is your scout.**

NASA Space Apps Challenge 2026 · Chattogram, Bangladesh
**Challenge:** [Field Shift: Adapting Farms with NASA Data](https://www.spaceappschallenge.org/2026/challenges/)
**Live demo:** _add Vercel link_ · **Video:** _add YouTube link_ · **Team page:** _add NASA team link_

---

## The idea

Rahim farms rice in the Sunamganj haor. His father taught him the sky always warns before it takes. In 2017, a flash flood took his harvest days before it was ready, with no warning he could see.

NASA satellites *did* see the rain building in the hills upstream. In this game you replay that real season, day by day, on real NASA data. The **Satellite Scout** shows what NASA measured, and you decide: raise the bund, plant a faster variety, or harvest early. Then compare your result with *Rahim's way*: same field, same rain, no warning.

How the game answers the challenge, what players learn and what is still missing: [`docs/GAME_DESIGN.md`](docs/GAME_DESIGN.md).

## Who it helps

- **Students** in farming regions, learning how climate is changing their land
- **Farming families**, seeing why short-duration varieties and early warnings matter
- **Agricultural extension officers**, who need to *show*, not just tell

NASA POWER is global, so the same engine works for any farm on Earth by changing a latitude and longitude.

## NASA data used

| Dataset | Parameters | Used for |
|---|---|---|
| NASA POWER Daily API (community AG) | `PRECTOTCORR` precipitation, `T2M_MAX` max temperature, `GWETROOT` root-zone soil wetness | Daily weather that drives water level, Scout warnings and readings |

Points: farm 25.07°N 91.40°E (Sunamganj haor); upstream 25.27°N 91.73°E (Cherrapunji/Sohra, Meghalaya).
How the data becomes gameplay, and every simplification: [`docs/DATA.md`](docs/DATA.md).

## Run it

**The game (Java, realistic 3D, with launcher, menus and settings)** lives in [`game/`](game/README.md):

```bash
cd game && mvn package && java -jar target/hold-the-field.jar
```

**The web prototype** (the original browser version, kept as a no-install demo) is the rest of this repo. No install needed:

```bash
python3 -m http.server 5173     # then open http://localhost:5173
node --test                     # run the simulation tests
python3 data-pipeline/fetch_power.py --region haor --start 20161201 --end 20170430 --out data/haor-2017.json
node scripts/calibrate.mjs data/haor-2017.json --target YYYY-MM-DD   # fit the water model to the documented flood date
```

## Team Anthropocene

| Name | Role |
|---|---|
| Alif | Mission Lead: direction, repo, data pipeline, deployment |
| Safwat | Systems Engineer: architecture, game loop, CI, performance |
| Yasin | Systems Designer: game mechanics, renderer, interactions |
| Zawad | Creative Director: visual identity, art, UI |
| Yaminur | Science & Research Lead: climate and crop facts, sources |
| Marwa | Narrative Lead: story, script, presentation |

## Use of AI

See [`docs/AI_USAGE.md`](docs/AI_USAGE.md). Every AI tool we used is listed there with its purpose.

## License

MIT. Everything built during Space Apps stays open source.
