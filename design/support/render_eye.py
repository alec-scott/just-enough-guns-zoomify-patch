"""Perspective review render of the FSV-6 from a point in the model (e.g. a player's eye in the deployed room).

python design/support/render_eye.py <progress> <x> <y> <z> <yaw_deg> <pitch_deg> <out.png>
Model pixels; yaw 0 looks forward (-Z), positive yaw turns towards +X (the left side); positive pitch looks down.
"""
import math
import sys

from PIL import Image, ImageDraw

import generate as g


def main():
    progress, x, y, z, yaw, pitch = map(float, sys.argv[1:7])
    out = sys.argv[7]
    g.create_model()
    g.separate_finishes()
    state = g.pose(progress)
    w, h, fov = 854, 480, math.radians(70)
    focal = (h / 2) / math.tan(fov / 2)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        dx, dy, dz = p[0] - x, p[1] - y, p[2] - z
        # Camera looks along -Z when yaw is 0; turn by yaw about Y, then tilt by pitch about X.
        rx = dx * cy - dz * sy
        rz = dx * sy + dz * cy
        fy = dy * cp - rz * sp
        fz = dy * sp + rz * cp
        return rx, fy, -fz  # depth: distance in front of the camera

    faces = []
    for _, c, pts in g.posed_cubes(state):
        rgba = g.MATERIALS[c["_material"]]
        for idx, shade in (((0, 1, 3, 2), .78), ((4, 6, 7, 5), .78), ((0, 4, 5, 1), .62), ((2, 3, 7, 6), 1.0),
                           ((0, 2, 6, 4), .9), ((1, 5, 7, 3), .9)):
            cam = [view(pts[i]) for i in idx]
            if any(p[2] < 1 for p in cam):
                continue
            poly = [(w / 2 - p[0] / p[2] * focal, h / 2 - p[1] / p[2] * focal) for p in cam]
            depth = sum(p[2] for p in cam) / 4
            faces.append((depth, poly, tuple(int(v * shade) for v in rgba[:3]) + (255,)))
    faces.sort(key=lambda f: -f[0])
    image = Image.new("RGBA", (w, h), (120, 160, 210, 255))
    draw = ImageDraw.Draw(image)
    for _, poly, colour in faces:
        draw.polygon(poly, fill=colour)
    image.save(out)


if __name__ == "__main__":
    main()
