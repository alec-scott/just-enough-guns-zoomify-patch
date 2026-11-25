"""Call the mcpfabric bridge of a running pack client (./gradlew runPackClient -PwithPack -PwithMcp ...).

python -I design/playtest/mcp.py <method> '<json params>' [screenshot_out.png]

Examples: session.join '{"worldId":"sp:SupportTest"}', screen.get '{}', chat.send '{"message":"/gamemode creative"}',
command.run '{"command":"time set midnight"}', vision.screenshot '{}' shot.png. The token is read from
~/.mcpfabric/bridge.json. See docs/vehicle-development-guide.md section 9.

A second client (multiplayer play-tests, design/playtest/second_client.py) has its own bridge: set MCP_RUN to its run
directory (MCP_RUN=build/run/packClient2) and the port and token are read from its config/mcpfabric.config.json.
"""
import base64
import json
import os
import pathlib
import sys
import urllib.request

run = os.environ.get("MCP_RUN")
if run:
    cfg = json.loads((pathlib.Path(run) / "config" / "mcpfabric.config.json").read_text())
else:
    cfg = json.loads((pathlib.Path.home() / ".mcpfabric" / "bridge.json").read_text())
token = cfg.get("token") or cfg.get("authToken") or cfg.get("bearer")
port = cfg.get("port", 25599)
method = sys.argv[1]
params = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
req = urllib.request.Request(f"http://127.0.0.1:{port}/rpc", data=json.dumps({"method": method, "params": params}).encode(),
                             headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
with urllib.request.urlopen(req, timeout=120) as r:
    out = json.loads(r.read())
if len(sys.argv) > 3:
    res = out.get("result", out)
    data = None
    if isinstance(res, dict):
        for k in ("image", "png", "data", "base64"):
            if isinstance(res.get(k), str):
                data = res[k]
                break
    if data:
        if "," in data[:40]:
            data = data.split(",", 1)[1]
        pathlib.Path(sys.argv[3]).write_bytes(base64.b64decode(data))
        print("saved", sys.argv[3])
    else:
        print(json.dumps(out)[:600])
else:
    print(json.dumps(out)[:1500])
