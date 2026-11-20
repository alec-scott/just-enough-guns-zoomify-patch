# HPJ-11 close-in weapon system brief

Status: **refit pass 1 being built, 2026-11-17** ([refit.py](refit.py), [rig.json](rig.json)). From
[the fleet plan](../FLEET_PLAN.md), section 5; its decisions (2026-11-15) apply: automatic fire on by default, with a
server cap per chunk.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the Mk42 and BL-132 (crewed guns that also fire at aircraft), the Mi-28 (its rotary gun's spool-up)
- **Source:** Superb Warfare's `hpj_11` (shipped unchanged since the port; now refit), used with the SBW team's
  permission.

## Purpose

- **What the player uses it for:** protect a base from the air. From day 80 the enemy brings AH-6s and Mi-28s, and the
  Mi-28 fires missiles; nothing on the ground could answer them.
- **Success:** place it at a base, load it with 30 mm, switch it to automatic, and watch it shoot down an enemy AH-6
  and the missiles fired at the base, while ignoring friendly aircraft and players.

## Real-world reference

- **Class:** H/PJ-11 (Type 1130), an eleven-barrel 30 mm Gatling close-in weapon system with search and tracking
  radars; about 10,000 rounds a minute in reality, elevation −25° to +85°.
- **Refit:** SBW's mount is 3.6 blocks wide and 3.7 high with its radars: **scale 1.0**.
- **Moving parts:** `turret`, `barrel`, `barrel_cluster` (spins with the gun), `search_radar` (turns all the time while
  manned or on automatic), `tracking_radar` (follows what it tracks).

## Controls

| Seat | Key | Action |
| --- | --- | --- |
| Operator (0, inside) | Mouse | lay the gun (the mount slews at 9° and 6° a tick) |
| | Left button | fire (the barrels spool up first) |
| | N | manual or automatic |
| | R | (fed from the hold: no magazine to change) |

## Behaviour

- **The gun** (`vehicle_hpj11_ciws`): belt-fed from the hold (30 mm, the BMP-2's ammunition), a round a tick once the
  barrels have spun up (1,200 a minute, SBW's rate; 1.5 s to spool, as the Mi-28's), 30 blocks a tick on the long-range
  path, armour piercing 7.5 (through aircraft and light vehicles, not a tank's front). The firing loop is SBW's
  HPJ-11 recording.
- **Proximity fuze:** a round bursts beside a threat that passes within 1.6 blocks of its path: a missile is destroyed
  in flight (its warhead goes off there), an aircraft or phantom takes the round's hit.
- **Automatic** (N; works crewed or not): it looks for threats within 256 blocks in sight, in order: missiles the enemy
  fired, enemy aircraft, phantoms. It leads the target at the round's speed, lays the mount on it, spins up and fires
  when within 2.5°. It never lays the gun below 5° above the horizon, so it can't rake its own base.
- **What is a threat** (`AirDefenceTargets`): a missile or rocket the enemy launched (decided at launch, since the
  launcher may be gone by then), an aircraft the raids brought or a monster flies, a phantom. Never a player, anything
  a player fired, a vehicle with a player aboard, or a tame or passive creature.
- **Server limits** (`jeg-server.toml`): `autoDefenceEnabled` (on), `autoDefencePerChunk` (2: the rest wait for a crew).

## Vehicle data

- One seat, the operator, hidden inside, the camera above the mount behind the gun.
- Hold: 27 slots. Energy 500,000; health 300.
- Hitbox: the base, the mount, each radar (turning with the mount), the gun outside the mount (elevating with it).
- Assembly: now enabled, about the BMP-2's cost (42 iron, 20 copper, 18 redstone).

## Pack interplay

| Mod | How it cooperates |
| --- | --- |
| Carry On | `jeg:hpj11` on the entity blacklist |
| Claim mods | it only fires upward at air threats, never at blocks or players |

## Sound

Real recordings (`prepare_audio.py`, played by `CiwsAudio`): a Phalanx burst up close and its ring-out, a C-RAM from
afar and its echo, an M134's drive spooling up and down; the radar's servo while it sweeps. **To be checked by ear.**
