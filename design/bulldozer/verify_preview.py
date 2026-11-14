"""Validate the offline preview's linkage transforms and export review images and the selection icon.

Requires Playwright's Python package and an installed Chromium browser (Edge by default).
"""
import base64
import json
import os
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def main():
    rig = json.loads((HERE / "rig.json").read_text(encoding="utf-8"))
    edge = os.environ.get("BULLDOZER_BROWSER", "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe")
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=edge, headless=True, args=["--mute-audio"])
        page = browser.new_page(viewport={"width": 1500, "height": 940}, device_scale_factor=1)
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto((HERE / "preview.html").as_uri())
        page.wait_for_function("window.bulldozerPreview && window.bulldozerPreview.ready")
        mirror = page.evaluate("""() => {
            const r=MODEL.rig,m=window.bulldozerPreview.transforms.mirror,c=r.mirror_center;
            const center=point(m,c),n=unit(sub(point(m,[c[0],c[1],c[2]+1]),center));
            const eye=unit(sub(r.driver_eye,center)),rear=unit(sub(r.mirror_rear_target,center));
            const reflected=n.map((v,i)=>2*dot(eye,n)*v-eye[i]);
            function intersectsPost(cube,from) {
                const delta=sub(r.driver_eye,from);let enter=0,leave=1;
                for(let k=0;k<3;k++) {
                    if(Math.abs(delta[k])<1e-8) { if(from[k]<cube.origin[k]||from[k]>cube.origin[k]+cube.size[k])return false; }
                    else {
                        const a=(cube.origin[k]-from[k])/delta[k],b=(cube.origin[k]+cube.size[k]-from[k])/delta[k];
                        enter=Math.max(enter,Math.min(a,b));leave=Math.min(leave,Math.max(a,b));
                    }
                }
                return enter<=leave;
            }
            const posts=MODEL.bones.flatMap(b=>b.cubes).filter(c=>c._name.includes('ROPS post'));
            const glass=MODEL.bones.find(b=>b.name==='mirror').cubes.filter(c=>c._material==='mirror');
            const corners=glass.flatMap(c=>[0,1,2,3].map(i=>point(m,[c.origin[0]+(i&1)*c.size[0],c.origin[1]+((i>>1)&1)*c.size[1],c.origin[2]+c.size[2]])));
            return {eyeFacing:dot(eye,n),rearAlignment:dot(reflected,rear),blocked:corners.some(p=>posts.some(c=>intersectsPost(c,p)))};
        }""")
        assert mirror["eyeFacing"] > .8 and mirror["rearAlignment"] > .99999 and not mirror["blocked"], mirror
        for angle in (rig["lift_min_degrees"], 0, 12, rig["lift_max_degrees"]):
            result = page.evaluate("""angle => {
                const p=window.bulldozerPreview, r=MODEL.rig;
                p.setLift(angle);
                const m=p.transforms;
                const ends=[];
                for (const side of ['left','right']) {
                    const s=side==='left'?1:-1;
                    const lift=cylinderPose(0)[1], tilt=tiltPose(0)[1];
                    const a=point(m['lift_rod_'+side],[s*r.lift_base[0],r.lift_base[1]+lift,r.lift_base[2]]);
                    const b=point(m.blade_tilt,[s*r.lift_end[0],r.lift_end[1],r.lift_end[2]]);
                    const c=point(m['tilt_rod_'+side],[s*r.tilt_base[0],r.tilt_base[1]+tilt,r.tilt_base[2]]);
                    const d=point(m.blade_tilt,[s*r.tilt_end[0],r.tilt_end[1],r.tilt_end[2]]);
                    ends.push(Math.hypot(...sub(a,b)), Math.hypot(...sub(c,d)));
                }
                const lo=point(m.blade_tilt,[0,2,-43]), hi=point(m.blade_tilt,[0,18,-43]);
                return {ends, bladeLean: Math.hypot(hi[0]-lo[0],hi[2]-lo[2])};
            }""", angle)
            assert max(result["ends"]) < 1e-6, (angle, result)
            assert result["bladeLean"] < 1e-6, (angle, result)
        icon = page.evaluate("window.renderVehicleIcon()")
        icon_path = ROOT / "src/main/resources/assets/jeg/textures/vehicle_icon/bulldozer_icon.png"
        icon_path.write_bytes(base64.b64decode(icon.split(",", 1)[1]))
        with Image.open(icon_path) as exported_icon:
            assert exported_icon.size == (256, 64) and exported_icon.mode == "RGBA"
        page.evaluate("window.bulldozerPreview.setLift(0)")
        page.screenshot(path=str(HERE / "preview.png"))
        page.click("#raise")
        page.screenshot(path=str(HERE / "blade-raised.png"))
        page.click('[data-view="side"]')
        page.screenshot(path=str(HERE / "side-profile.png"))
        page.click('[data-view="front"]')
        page.screenshot(path=str(HERE / "front-view.png"))
        page.click('[data-view="rear"]')
        page.screenshot(path=str(HERE / "rear-view.png"))
        page.select_option("#motion", "pivot")
        page.wait_for_timeout(250)
        assert page.evaluate("phaseLeft > 0 && phaseRight < 0")
        assert not errors, errors
        browser.close()
    project = json.loads((HERE / "bulldozer.bbmodel").read_text(encoding="utf-8"))
    identities = {i["uuid"] for i in project["groups"] + project["elements"]}
    used = []

    def walk(items):
        for item in items:
            if isinstance(item, str):
                used.append(item)
            else:
                used.append(item["uuid"])
                walk(item["children"])
    walk(project["outliner"])
    assert len(used) == len(set(used)) and set(used) == identities
    print("PASS: mirror faces the seat, rearward reflection angle and clear sightline; preview linkage, track motion, icon and Blockbench hierarchy.")


if __name__ == "__main__":
    main()
