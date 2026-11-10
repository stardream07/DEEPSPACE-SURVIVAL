# ORPHEUS — Deep-Space Survival Simulator

A playable Java/JavaFX survival game built from scratch because no existing Java project was supplied. Its visual direction follows the supplied `WhatsApp Image 2026-10-09 at 23.56.47.jpeg`: dark cockpit framing, muted indigo space, small instrument lights, and crisp, deliberately authored pixels.

![Ship view](artifacts/ready.png)

## Run

Requires Java 21 or newer with the compiler module, and a desktop graphical session. The local Linux x86-64 JavaFX dependencies have already been downloaded; no network connection is needed to play on this machine.

```bash
cd /home/cloud/deep-space-survival
./run.sh
```

Enter a crew name, then press **Enter** or click **Begin Watch**. The ship starts damaged. Salvage a crate in Cargo, restore the oxygen scrubber and hull seal, then return to Flight Deck to jump.

For another supported Linux/macOS platform, run `./setup.sh` to fetch the matching native JavaFX modules first. With a full JDK and Maven installed, including on Windows, the alternative is:

```bash
mvn javafx:run
```

Dependencies and the Maven launch configuration follow the [official OpenJFX setup](https://openjfx.io/openjfx-docs/#maven). The project pins JavaFX 21.0.5. The source is compiled to Java 21 bytecode; this workspace was verified using OpenJDK 25.

## Controls

| Input | Action |
|---|---|
| WASD / arrow keys | Move through connected rooms |
| E | Salvage the nearest crate, repair a system, open a circuit, or jump from Flight Deck |
| Q | Consume an oxygen canister for up to +25% oxygen |
| F | Consume coolant to lower reactor temperature by up to 25°C |
| Tab | Toggle the BFS route guide to a damaged system |
| P / Escape | Pause / resume; Escape closes a circuit first |
| H / ? button | Open the crew manual; reading pauses the ship |
| Click a circuit tile | Rotate its wire connections clockwise |
| Enter in a circuit | Energize once source and sink connect |

Oxygen and coolant can also be used by clicking their cargo slots. The pause screen has a restart button. A new watch can be started after success or failure.

Repairs take four seconds; movement cancels them without spending materials. Circuit repairs consume materials only after you connect the source to the sink and press Energize. Nearby terminals show their actual material costs in the interaction strip. Crates may be collected once per stage and restock after a sector jump.

## Mission scoring

Earn 100 points per salvaged crate, 250 for the first completed repair of each subsystem per sector, 1,000 per completed sector, and 2,000 more for returning to Earth. The results panel shows the breakdown.

Open **Leaderboard** from crew entry or mission results. The top ten wins and losses are saved locally in data/leaderboard.properties. Ties favor shorter watches, then earlier completion. Restarting before the watch finishes discards that run.

## Three mission stages

| Stage | Sector | Main work |
|---|---|---|
| 01 | The Long Silence | Salvage resources, restore life support and the bulkhead seal |
| 02 | Debris Crossing | Repair the coolant pump and rewire the power bus while crossing moving debris |
| 03 | Homeward Bound | Control a reactor fire, seal impact damage, and rewire the Earth navigation vector |

The ship stays spatially consistent across stages. The planet, debris, objectives, damage, hazards, resources, and return conditions change. Periodic emergencies become more frequent in later stages. To jump, restore the required systems and reach Flight Deck with safe oxygen, hull, temperature, and power. The final jump returns the ship to Earth.

## Emergency effects

| Emergency | Immediate and continuing consequences | Resolution |
|---|---|---|
| Oxygen leak | Immediate oxygen loss, damaged scrubber, continuous oxygen drain | Repair the oxygen scrubber; canisters provide temporary relief |
| Reactor overheating | Temperature spike, failed cooling, continued heating; extreme heat damages hull and crew in Reactor | Repair the coolant pump; coolant buys time |
| Hull breach | Immediate hull damage, continuing hull and oxygen loss | Repair the bulkhead seal |
| Power surge | Power loss and a damaged bus; low power dims rooms, slows movement, and worsens oxygen loss | Solve and energize the power circuit |
| Reactor fire | Increased temperature, continued heating, and damage to nearby crew | Repair the coolant pump and clear its fire |

Zero oxygen, zero hull, fatal crew injury, or a 120°C reactor ends the mission. Emergencies are physical simulation events with rendered consequences, log entries, and warning lights. The ship clock continues while circuits are open. All clocks and hazards stop when paused.

## Architecture

`Simulation` owns the world and runs on a single scheduled worker. Commands and snapshots are synchronized so JavaFX never mutates world data while a hazard ticks. The renderer receives immutable snapshots; it uses the same topology as movement and pathfinding.

- `Subsystem`: abstract system strategy and factory, with distinct effects and repair costs.
- `ShipMap`: connected ship rooms, collision boundaries, and BFS routes.
- `CircuitBoard`: rotated reciprocal wire connections, BFS power traversal, and DFS topology inspection. Every generated board has a valid path.
- `GameRenderer`: one 640×400 logical canvas, nearest-neighbor scaling, consistent panels and live telemetry.
- `ShipArt`, `PixelFont`, `Palette`: reusable original sprites, a bitmap alphabet, and a small shared color set. No external art, decorative image generation, gradients, or bloom.
- `GameApp`: keyboard/mouse input, focus handling, lifecycle, and JavaFX rendering. Losing window focus pauses the simulation.

All rooms are navigable from the outset. Equipment graphics are interaction markers rather than additional collision obstacles. This first version has one reusable ship layout, three mission stages, and no save/resume system.

## Verification

```bash
./build.sh --test
./run.sh --smoke-test
```

The headless test suite covers mission scoring, including an entire three-stage journey to Earth using public movement, salvage, repair, circuit, and navigation commands. It also covers collision boundaries, pause behavior, interruption/material transactions, all five emergency effects, restart, puzzle solvability across 80 seeds, BFS/DFS agreement, and the real scheduled clock.

The graphical smoke test checks keyboard movement, start/pause/resume, manual behavior, mouse buttons, circuit tile rotation, and successful energizing of the actual power subsystem. It writes screenshots to `artifacts/` and exits. It requires a graphical session.

![Live emergency view](artifacts/emergencies.png)
![Connected circuit](artifacts/circuit-connected.png)
