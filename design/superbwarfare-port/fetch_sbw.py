"""Download Superb Warfare source assets for a refit, from one pinned commit.

python -I design/superbwarfare-port/fetch_sbw.py <out_dir> <sbw_id> [<sbw_id> ...] [--pin 2026-11] [--extra <asset path> ...]

Fetches, for each id, whatever exists of its geometry, LODs, animations, textures (the `_e` emissive layer too), the
vehicle icon and the SBW vehicle data. --extra adds paths under assets/superbwarfare/ (sounds, item icons).

Two pins:
- the default, the Fabric port 0.8.8 (2026-06-29), which every vehicle ported up to the YX-100 came from;
- `--pin 2026-11`, the Fabric port's `1.21-Fabric` branch on 2026-11-25, for the candidates that arrived upstream
  after that (AC-130H, M1A2, T-90A, ZTZ-99A, Bradley, LAV-25, LAV-AD, Ju-87, the Sodayo pickups, Kirov, KV-16). Its
  layout moved: models/bedrock/vehicle, animations/bedrock/vehicle, textures/bedrock/vehicle, sbw/vehicles under
  assets. Files land in <out_dir> at the old layout's names (geo/, animations/, textures/entity/, sbw/vehicles/), so
  refit scripts read either pin the same way.

The assets are the SBW team's (used with their permission, see PLAN.md) and are not committed: refit scripts read
them from <out_dir>.
"""
import pathlib
import sys
import urllib.error
import urllib.request

PINS = {
    # CrawKatt/SuperbWarfareReFabricated 0.8.8-1.21-Fabric, 2026-06-29
    "0.8.8": "952d73c249f6e1cf1c58fa7d07eec48db45a030f",
    # CrawKatt/SuperbWarfareReFabricated 1.21-Fabric, 2026-11-25
    "2026-11": "c5189d1c09f66d9036303510bd224d8c7bd4f183",
}


def candidates(sbw_id, pin):
    """(source path under src/main/resources/, local path under <out_dir>)."""
    a = "assets/superbwarfare/"
    if pin == "0.8.8":
        yield a + f"geo/{sbw_id}.geo.json", f"geo/{sbw_id}.geo.json"
        for level in (1, 2, 3):
            yield a + f"geo/vehicle_lod/{sbw_id}_lod{level}.geo.json", f"geo/vehicle_lod/{sbw_id}_lod{level}.geo.json"
        yield a + f"animations/{sbw_id}.animation.json", f"animations/{sbw_id}.animation.json"
        yield a + f"textures/entity/{sbw_id}.png", f"textures/entity/{sbw_id}.png"
        yield a + f"textures/vehicle_icon/{sbw_id}_icon.png", f"textures/vehicle_icon/{sbw_id}_icon.png"
        yield f"data/superbwarfare/sbw/vehicles/{sbw_id}.json", f"sbw/vehicles/{sbw_id}.json"
        return
    yield a + f"models/bedrock/vehicle/{sbw_id}.geo.json", f"geo/{sbw_id}.geo.json"
    for level in (1, 2, 3):
        yield (a + f"models/bedrock/vehicle/lod/{sbw_id}_lod{level}.geo.json",
               f"geo/vehicle_lod/{sbw_id}_lod{level}.geo.json")
    yield a + f"animations/bedrock/vehicle/{sbw_id}.animation.json", f"animations/{sbw_id}.animation.json"
    yield a + f"textures/bedrock/vehicle/{sbw_id}.png", f"textures/entity/{sbw_id}.png"
    yield a + f"textures/bedrock/vehicle/{sbw_id}_e.png", f"textures/entity/{sbw_id}_e.png"
    yield a + f"textures/vehicle_icon/{sbw_id}_icon.png", f"textures/vehicle_icon/{sbw_id}_icon.png"
    yield a + f"sbw/vehicles/{sbw_id}.json", f"sbw/vehicles/{sbw_id}.json"


def main():
    out = pathlib.Path(sys.argv[1])
    args = sys.argv[2:]
    extra = []
    if "--extra" in args:
        at = args.index("--extra")
        args, extra = args[:at], args[at + 1:]
    pin = "0.8.8"
    if "--pin" in args:
        at = args.index("--pin")
        pin = args[at + 1]
        args = args[:at] + args[at + 2:]
    base = f"https://raw.githubusercontent.com/CrawKatt/SuperbWarfareReFabricated/{PINS[pin]}/src/main/resources/"
    jobs = [(sbw_id, list(candidates(sbw_id, pin))) for sbw_id in args]
    jobs.append(("extra", [("assets/superbwarfare/" + path, path) for path in extra]))
    for sbw_id, paths in jobs:
        for rel, local in paths:
            target = out / local
            try:
                with urllib.request.urlopen(base + rel, timeout=30) as response:
                    data = response.read()
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    continue
                raise
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            print(f"{sbw_id}: {target.relative_to(out)} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
