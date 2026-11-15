"""Download Superb Warfare source assets for a refit, from one pinned commit.

python -I design/superbwarfare-port/fetch_sbw.py <out_dir> <sbw_id> [<sbw_id> ...]

Fetches, for each id, whatever exists of: geo/<id>.geo.json, geo/vehicle_lod/<id>_lod{1,2,3}.geo.json,
animations/<id>.animation.json, textures/entity/<id>.png, textures/vehicle_icon/<id>_icon.png and
sbw/vehicles/<id>.json (the SBW vehicle data). The assets are the SBW team's (used with their permission, see
PLAN.md) and are not committed: refit scripts read them from <out_dir>.
"""
import pathlib
import sys
import urllib.error
import urllib.request

COMMIT = "952d73c249f6e1cf1c58fa7d07eec48db45a030f"  # CrawKatt/SuperbWarfareReFabricated 0.8.8-1.21-Fabric, 2026-06-29
BASE = f"https://raw.githubusercontent.com/CrawKatt/SuperbWarfareReFabricated/{COMMIT}/src/main/resources/"


def candidates(sbw_id):
    a = "assets/superbwarfare/"
    yield a + f"geo/{sbw_id}.geo.json"
    for level in (1, 2, 3):
        yield a + f"geo/vehicle_lod/{sbw_id}_lod{level}.geo.json"
    yield a + f"animations/{sbw_id}.animation.json"
    yield a + f"textures/entity/{sbw_id}.png"
    yield a + f"textures/vehicle_icon/{sbw_id}_icon.png"
    yield f"data/superbwarfare/sbw/vehicles/{sbw_id}.json"


def main():
    out = pathlib.Path(sys.argv[1])
    for sbw_id in sys.argv[2:]:
        for rel in candidates(sbw_id):
            target = out / rel.split("superbwarfare/", 1)[1]
            try:
                with urllib.request.urlopen(BASE + rel, timeout=30) as response:
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
