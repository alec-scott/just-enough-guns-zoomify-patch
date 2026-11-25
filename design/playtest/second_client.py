"""Start a second pack client beside a running one, for two-player play-tests over mcpfabric.

    python -I design/playtest/second_client.py <name> <lan port> [--no-shaders]

Start the first client as usual (./gradlew runPackClient -PwithPack -PwithMcp ... --args="--username Pilot"), join
its world and open it to LAN (`/publish true creative <lan port>` in its chat). This then starts a copy of the same
JVM command (read from the running process) in build/run/packClient2 with its own name, its own mcpfabric bridge on
port 25600 (no discovery file, so ~/.mcpfabric/bridge.json stays the first client's), and the LAN world in its server
list as "Host", so `session.join {"worldId": "mp:0"}` joins it. Drive it with MCP_RUN=build/run/packClient2 mcp.py.

It uses the first client's classes: no Gradle build may run while both are up (it rewrites build/classes under them).
Kill it as the first: the java.exe whose command line holds packClient2.
"""
import json
import pathlib
import shutil
import struct
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
FIRST = ROOT / "build" / "run" / "packClient"
SECOND = ROOT / "build" / "run" / "packClient2"


def nbt_string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def servers_dat(name, address):
    """An uncompressed servers.dat holding one server (what JoinMultiplayerScreen reads)."""
    entry = b"\x08" + nbt_string("ip") + nbt_string(address) + b"\x08" + nbt_string("name") + nbt_string(name) + b"\x00"
    servers = b"\x09" + nbt_string("servers") + b"\x0a" + struct.pack(">i", 1) + entry
    return b"\x0a" + nbt_string("") + servers + b"\x00"


def first_command():
    script = ("Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match "
              "'KnotClient' -and $_.CommandLine -notmatch 'packClient2' } | Select-Object -First 1 -ExpandProperty CommandLine")
    out = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True).stdout.strip()
    if not out:
        sys.exit("No running pack client found")
    return out


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    name, port = sys.argv[1], int(sys.argv[2])
    shaders = "--no-shaders" not in sys.argv
    SECOND.mkdir(parents=True, exist_ok=True)
    for folder in ("config", "shaderpacks", "resourcepacks", "defaultconfigs"):
        if (FIRST / folder).exists():
            shutil.copytree(FIRST / folder, SECOND / folder, dirs_exist_ok=True)
    shutil.copy2(FIRST / "options.txt", SECOND / "options.txt")
    config = json.loads((SECOND / "config" / "mcpfabric.config.json").read_text())
    config.update(port=25600, writeDiscoveryFile=False, enableUiControl=True)
    (SECOND / "config" / "mcpfabric.config.json").write_text(json.dumps(config, indent=2))
    iris = SECOND / "config" / "iris.properties"
    if iris.exists():
        lines = [line for line in iris.read_text().splitlines() if not line.startswith("enableShaders=")]
        iris.write_text("\n".join(lines + ["enableShaders=" + ("true" if shaders else "false")]) + "\n")
    (SECOND / "servers.dat").write_bytes(servers_dat("Host", "127.0.0.1:%d" % port))
    # The argfile can be rewritten by a later Gradle run: keep a copy for this client.
    command = first_command()
    argfile = command.split("@", 1)[1].split(" ", 1)[0]
    kept = SECOND / "argfile.txt"
    shutil.copy2(argfile, kept)
    command = command.replace("@" + argfile, "@" + str(kept))
    if "--username" in command:
        command = command[:command.index("--username")].rstrip()
    command += " --username " + name
    log = open(SECOND / "launch.log", "w")
    process = subprocess.Popen(command, cwd=SECOND, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0))
    print("started", name, "pid", process.pid, "in", SECOND)


if __name__ == "__main__":
    main()
