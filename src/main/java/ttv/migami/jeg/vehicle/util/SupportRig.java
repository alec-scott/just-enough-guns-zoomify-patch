package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Geometry of the FSV-6 field support vehicle shared by the server (deploy checks, the walk-in shell) and the
 * renderer. Source units are model pixels (16 per block); the vehicle faces -Z and +X is the driver's left.
 * Mirrors {@code design/support/rig.json}; regenerate with {@code python design/support/generate.py}.
 */
public final class SupportRig {
    public static final double FLOOR_Y = 16, CEILING_Y = 48, ROOF_TOP = 50;
    /** Deployed interior: x -32..32 (four columns), z -24..56 (five rows), one block per cell. */
    public static final double INTERIOR_X = 32, INTERIOR_FRONT = -24, INTERIOR_REAR = 56, CELL = 16;
    public static final int COLUMNS = 4, ROWS = 5;
    public static final double SLIDE_TRAVEL = 16;
    /** Rolling radius (rig.json wheel_radius): 7.6 since the tyres sit in cut wheel arches under the floor plate. */
    public static final double WHEEL_RADIUS = 7.6, WHEEL_X = 17;
    /** Front, middle and rear axle positions along the model's Z. */
    public static final double[] AXLE_Z = {-44, 20, 38};
    public static final Vec3 DOOR_HINGE = new Vec3(0, 16, 60);
    public static final double DOOR_OPEN_DEGREES = 119.461;
    /** Jack positions (x, z) in model pixels: front left, front right, rear left, rear right. */
    public static final double[][] JACKS = {{17, -25.5}, {-17, -25.5}, {17, 58}, {-17, 58}};
    public static final double JACK_FOOT_Y = 3;
    /** How far below its stowed foot a jack can reach for the ground, in blocks (including the body's lift). */
    public static final double JACK_REACH_BLOCKS = 2.5;
    public static final double MAST_STAGE = 12, BEACON_RAISE = 8;
    public static final double AWNING_LEG = 44;
    public static final Vec3 SEAT_DRIVER = new Vec3(10, 16, -46);
    public static final Vec3 EXHAUST_TIP = new Vec3(-18, 54, -29);
    public static final Vec3 GENERATOR_EXHAUST = new Vec3(-21, 8, -6);
    public static final Vec3 MAST_FLOODLIGHTS = new Vec3(14, 103.9, -20);

    /** Deploy stages as fractions of the whole sequence: jacks, slide-outs, doors and awning, mast raise, mast and beacon. */
    public static final float JACKS_END = .19F, SLIDES_END = .56F, DOORS_END = .75F, MAST_RAISE_END = .86F;
    /** Deploying takes eight seconds. */
    public static final int DEPLOY_TICKS = 160;
    /** At or past this progress the slide-outs are out and the deployed hitboxes apply. */
    public static final float SLIDES_OUT = SLIDES_END;

    private SupportRig() {}

    /** Smoothstep progress of one stage, 0..1, from the whole sequence's progress. */
    public static float stage(float progress, float start, float end) {
        float t = Mth.clamp((progress - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static float jacks(float p) { return stage(p, 0, JACKS_END); }
    public static float slides(float p) { return stage(p, JACKS_END, SLIDES_END); }
    public static float doors(float p) { return stage(p, SLIDES_END, DOORS_END); }
    public static float mastRaise(float p) { return stage(p, DOORS_END, MAST_RAISE_END); }
    public static float mast(float p) { return stage(p, MAST_RAISE_END, 1); }

    public static Vec3 forward(float yaw) {
        return Vec3.directionFromRotation(0, yaw);
    }

    /** A model point, in source pixels, to world space. */
    public static Vec3 world(VehicleEntity vehicle, double x, double y, double z) {
        return world(vehicle.position(), vehicle.getYRot(), x, y, z);
    }

    public static Vec3 world(Vec3 origin, float yaw, double x, double y, double z) {
        Vec3 f = forward(yaw);
        return origin.add(f.z * x / 16 - f.x * z / 16, y / 16, -f.x * x / 16 - f.z * z / 16);
    }

    /** The centre of interior cell (column, row) at a level above the floor (0, 1; -1 is the floor itself), model px. */
    public static Vec3 cellCentre(int column, int row, int level) {
        return new Vec3(-INTERIOR_X + (column + .5) * CELL, FLOOR_Y + (level + .5) * CELL, INTERIOR_FRONT + (row + .5) * CELL);
    }

    /** What occupies each cell of the deployed room. */
    public enum Cell {
        /** The van floor (and the space under it): a full block so nothing walks under the body. */
        FLOOR,
        /** Open room: no collision, but nothing can be placed in it; lit. */
        ROOM,
        /** A module bay: solid, and using it opens the fitted module. */
        BAY,
        /** A bunk: a bed is placed here when one is fitted, open room otherwise. */
        BUNK,
        /** A side, front or rear wall: thin collision on the room side. */
        WALL,
        /** The ceiling: thin collision at the bottom of the cell. */
        ROOF,
        /** The ramp's upper step (top at 3/4 of the cell) and lower step (top at 1/4). */
        STEP_HIGH, STEP_LOW
    }

    /** Bays: columns 0 and 3, rows 2..4. Bunks: columns 0 and 3, rows 0..1 (head at row 0). */
    public static boolean isBay(int column, int row) {
        return (column == 0 || column == 3) && row >= 2;
    }

    public static boolean isBunk(int column, int row) {
        return (column == 0 || column == 3) && row <= 1;
    }

    /** Index of the bay at (column, row): 0..2 on the left (column 3, rows 2..4), 3..5 on the right. */
    public static int bayIndex(int column, int row) {
        return (column == 3 ? 0 : 3) + row - 2;
    }

    /** One cell of the shell, in the deployed vehicle's local grid; {@code level} -1 is the floor. */
    public record ShellCell(int column, int row, int level, Cell kind, int facing) {
        /** Centre of the cell in model pixels. */
        public Vec3 centre() {
            return new Vec3(-INTERIOR_X + (this.column + .5) * CELL, FLOOR_Y + (this.level + .5) * CELL,
                    INTERIOR_FRONT + (this.row + .5) * CELL);
        }
    }

    /** Wall facings, the side of the cell the wall is on, in model terms. */
    public static final int FACE_LEFT = 0, FACE_RIGHT = 1, FACE_FRONT = 2, FACE_REAR = 3;

    /** Every cell of the deployed shell. */
    public static java.util.List<ShellCell> shell() {
        java.util.List<ShellCell> cells = new java.util.ArrayList<>();
        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row < ROWS; row++) {
                cells.add(new ShellCell(column, row, -1, Cell.FLOOR, 0));
                for (int level = 0; level <= 1; level++) {
                    Cell kind = Cell.ROOM;
                    if (level == 0 && isBay(column, row)) kind = Cell.BAY;
                    else if (level == 0 && isBunk(column, row)) kind = Cell.BUNK;
                    cells.add(new ShellCell(column, row, level, kind, 0));
                }
                cells.add(new ShellCell(column, row, 2, Cell.ROOF, 0));
            }
        }
        for (int level = 0; level <= 1; level++) {
            for (int row = 0; row < ROWS; row++) {
                // The side walls are just outside columns 0 and 3: a thin skin on the room side of those cells.
                cells.add(new ShellCell(COLUMNS, row, level, Cell.WALL, FACE_LEFT));
                cells.add(new ShellCell(-1, row, level, Cell.WALL, FACE_RIGHT));
            }
            for (int column = 0; column < COLUMNS; column++) {
                cells.add(new ShellCell(column, -1, level, Cell.WALL, FACE_FRONT));
            }
            // The rear of the slide-outs and the door posts; the middle two columns are the doorway.
            cells.add(new ShellCell(0, ROWS, level, Cell.WALL, FACE_REAR));
            cells.add(new ShellCell(3, ROWS, level, Cell.WALL, FACE_REAR));
        }
        for (int column = 1; column <= 2; column++) {
            cells.add(new ShellCell(column, ROWS, -1, Cell.STEP_HIGH, 0));
            cells.add(new ShellCell(column, ROWS + 1, -1, Cell.STEP_LOW, 0));
        }
        return cells;
    }

    /** The interior's front-right corner (model x -32, z -24): it lands on whole block coordinates when deployed. */
    public static final double CORNER_X = -INTERIOR_X, CORNER_Z = INTERIOR_FRONT;
}
