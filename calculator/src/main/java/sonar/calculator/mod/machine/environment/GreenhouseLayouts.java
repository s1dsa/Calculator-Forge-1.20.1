package sonar.calculator.mod.machine.environment;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Original ordered construction layouts, retaining roof overlaps and legacy stair metadata. */
final class GreenhouseLayouts {
    enum Kind { LOG, GLASS, PLANKS, STAIRS }
    record Place(Kind type, BlockPos pos, int meta) {}
    private static Place place(Kind kind, int x, int y, int z, int meta) { return new Place(kind, new BlockPos(x,y,z), meta); }
    static int stairMeta(Direction facing,boolean left,boolean top) {
        int right=switch(facing) { case SOUTH -> 1; case WEST -> 3; case EAST -> 2; default -> 0; };
        return (left ? right ^ 1 : right)+(top ? 4 : 0);
    }
static java.util.ArrayList<Place> basic(BlockPos pos, Direction forward, Direction facing) {
        java.util.ArrayList<Place> blocks = new java.util.ArrayList<>();

		int hX = forward.getClockWise().getStepX();
		int hZ = forward.getClockWise().getStepZ();

		int hoX = forward.getClockWise().getOpposite().getStepX();
		int hoZ = forward.getClockWise().getOpposite().getStepZ();

		int fX = forward.getStepX();
		int fZ = forward.getStepZ();

		// end
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		for (int i = 1; i <= 2; i++) {
			blocks.add(place(Kind.LOG, x, y + i, z, -1));
		}

		for (int i = 0; i <= 2; i++) {
            blocks.add(place(Kind.LOG, x + hX * 2, y + i, z + hZ * 2, -1));
            blocks.add(place(Kind.LOG, x + hoX * 2, y + i, z + hoZ * 2, -1));
		}
		for (int i = 0; i <= 2; i++) {
            blocks.add(place(Kind.GLASS, x + hX, y + i, z + hZ, -1));
            blocks.add(place(Kind.GLASS, x + hoX, y + i, z + hoZ, -1));
		}

		// front
        x = pos.getX() + forward.getStepX() * 4;
		y = pos.getY();
        z = pos.getZ() + forward.getStepZ() * 4;
		for (int i = 0; i <= 2; i++) {
            blocks.add(place(Kind.LOG, x + hX * 2, y + i, z + hZ * 2, -1));
            blocks.add(place(Kind.LOG, x + hoX * 2, y + i, z + hoZ * 2, -1));
		}
		for (int i = 0; i <= 2; i++) {
            blocks.add(place(Kind.PLANKS, x + hX, y + i, z + hZ, -1));
            blocks.add(place(Kind.PLANKS, x + hoX, y + i, z + hoZ, -1));
			if (i == 2)
				blocks.add(place(Kind.PLANKS, x, y + i, z, -1));
		}

		x = pos.getX();
		y = pos.getY();
		z = pos.getZ();
		// sides
		for (int i = 1; i <= 3; i++) {
			if (i != 2) {
                blocks.add(place(Kind.PLANKS, x + hX * 2 + fX * i, y - 1, z + hZ * 2 + fZ * i, -1));
                blocks.add(place(Kind.PLANKS, x + hoX * 2 + fX * i, y - 1, z + hoZ * 2 + fZ * i, -1));
				for (int s = 0; s <= 1; s++) {

                    blocks.add(place(Kind.GLASS, x + hX * 2 + fX * i, y + s, z + hZ * 2 + fZ * i, -1));
                    blocks.add(place(Kind.GLASS, x + hoX * 2 + fX * i, y + s, z + hoZ * 2 + fZ * i, -1));
				}
			}
            blocks.add(place(Kind.PLANKS, x + hX * 2 + fX * i, y + 2, z + hZ * 2 + fZ * i, -1));
            blocks.add(place(Kind.PLANKS, x + hoX * 2 + fX * i, y + 2, z + hoZ * 2 + fZ * i, -1));
		}

		for (int Y = 0; Y <= 1; Y++) {
            blocks.add(place(Kind.LOG, x + hX * 2 + fX * 2, y + Y, z + hZ * 2 + fZ * 2, -1));
            blocks.add(place(Kind.LOG, x + hoX * 2 + fX * 2, y + Y, z + hoZ * 2 + fZ * 2, -1));
		}

		// roof
		for (int i = -1; i <= 1; i++) {
            blocks.add(place(Kind.PLANKS, x + hX * i, y + 3, z + hZ * i, -1));
            blocks.add(place(Kind.PLANKS, x + hX * i + fX * 4, y + 3, z + hZ * i + fZ * 4, -1));
		}
		for (int i = -1; i <= 5; i++) {
			for (int s = 2; s <= 4; s++) {
                blocks.add(place(Kind.STAIRS, x + hX * (5 - s) + fX * i, y + s, z + hZ * (5 - s) + fZ * i, GreenhouseLayouts.stairMeta(facing, false, false)));
                blocks.add(place(Kind.STAIRS, x + hoX * (5 - s) + fX * i, y + s, z + hoZ * (5 - s) + fZ * i, GreenhouseLayouts.stairMeta(facing, true, false)));
                blocks.add(place(Kind.PLANKS, x + fX * i, y + 4, z + fZ * i, -1));
			}
		}

		// underroof
		for (int i = -1; i <= 5; i++) {
			if (i != -1 && i != 0 && i != 4 && i != 5) {
                blocks.add(place(Kind.STAIRS, x + hX + fX * i, y + 3, z + hZ + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));
                blocks.add(place(Kind.STAIRS, x + hoX + fX * i, y + 3, z + hoZ + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
			} else {
				if (i != 0 && i != 4) {
                    blocks.add(place(Kind.STAIRS, x + hX + fX * i, y + 3, z + hZ + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));
                    blocks.add(place(Kind.STAIRS, x + hoX + fX * i, y + 3, z + hoZ + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
                    blocks.add(place(Kind.STAIRS, x + hX * 2 + fX * i, y + 2, z + hZ * 2 + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));
                    blocks.add(place(Kind.STAIRS, x + hoX * 2 + fX * i, y + 2, z + hoZ * 2 + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
				}
			}
		}
		return blocks;
}
static java.util.ArrayList<Place> advanced(BlockPos pos, Direction forward, Direction facing) {
        java.util.ArrayList<Place> blocks = new java.util.ArrayList<>();

		int hX = forward.getClockWise().getStepX();
		int hZ = forward.getClockWise().getStepZ();

		int hoX = forward.getClockWise().getOpposite().getStepX();
		int hoZ = forward.getClockWise().getOpposite().getStepZ();

		int fX = forward.getStepX();
		int fZ = forward.getStepZ();

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		//end
		for (int i = 1; i <= 6; i++) {
			blocks.add(place(Kind.LOG, x, y + i, z, -1));
		}

		for (int i = 0; i <= 3; i++) {
            blocks.add(place(Kind.LOG, x + hX * 4, y + i, z + hZ * 4, -1));
            blocks.add(place(Kind.LOG, x + hoX * 4, y + i, z + hoZ * 4, -1));
		}

		for (int i = 1; i <= 3; i++) {
            blocks.add(place(Kind.PLANKS, x + hX * i, y - 1, z + hZ * i, -1));
            blocks.add(place(Kind.PLANKS, x + hoX * i, y - 1, z + hoZ * i, -1));
		}

		for (int j = 1; j <= 3; j++) {
			if (j != 3) {
				for (int i = 0; i <= 5; i++) {
                    blocks.add(place(Kind.GLASS, x + hX * j, y + i, z + hZ * j, -1));
                    blocks.add(place(Kind.GLASS, x + hoX * j, y + i, z + hoZ * j, -1));
				}
			} else {
				for (int i = 0; i <= 4; i++) {
                    blocks.add(place(Kind.GLASS, x + hX * j, y + i, z + hZ * j, -1));
                    blocks.add(place(Kind.GLASS, x + hoX * j, y + i, z + hoZ * j, -1));
				}
			}
		}

        x = pos.getX() + forward.getStepX() * 8;
		y = pos.getY();
        z = pos.getZ() + forward.getStepZ() * 8;
		for (int i = 0; i <= 3; i++) {
            blocks.add(place(Kind.LOG, x + hX * 4, y + i, z + hZ * 4, -1));
            blocks.add(place(Kind.LOG, x + hoX * 4, y + i, z + hoZ * 4, -1));
		}

		for (int i = 0; i <= 5; i++) {
			if (i <= 4) {
                blocks.add(place(Kind.GLASS, x + hX * 3, y + i, z + hZ * 3, -1));
                blocks.add(place(Kind.GLASS, x + hoX * 3, y + i, z + hoZ * 3, -1));
			}
            blocks.add(place(Kind.GLASS, x + hX * 2, y + i, z + hZ * 2, -1));
            blocks.add(place(Kind.GLASS, x + hoX * 2, y + i, z + hoZ * 2, -1));
		}

		for (int i = 0; i <= 6; i++) {
			if (i > 2) {
                blocks.add(place(Kind.GLASS, x + hX, y + i, z + hZ, -1));
                blocks.add(place(Kind.GLASS, x + hoX, y + i, z + hoZ, -1));
				blocks.add(place(Kind.GLASS, x, y + i, z, -1));
			}
			if (i <= 2) {
                blocks.add(place(Kind.PLANKS, x + hX, y + i, z + hZ, -1));
                blocks.add(place(Kind.PLANKS, x + hoX, y + i, z + hoZ, -1));
			}
			if (i == 2) {
				blocks.add(place(Kind.PLANKS, x, y + i, z, -1));
			}
		}

		for (int i = 2; i <= 3; i++) {
            blocks.add(place(Kind.PLANKS, x + hX * i, y - 1, z + hZ * i, -1));
            blocks.add(place(Kind.PLANKS, x + hoX * i, y - 1, z + hoZ * i, -1));
		}

		x = pos.getX();
		y = pos.getY();
		z = pos.getZ();
		//sides
		for (int i = 1; i <= 7; i++) {
			if (i != 4) {
				for (int s = 0; s <= 2; s++) {
					if (s == 0) {
                        blocks.add(place(Kind.PLANKS, x + hX * 4 + fX * i, y + s, z + hZ * 4 + fZ * i, -1));
                        blocks.add(place(Kind.PLANKS, x + hoX * 4 + fX * i, y + s, z + hoZ * 4 + fZ * i, -1));
					} else {
                        blocks.add(place(Kind.GLASS, x + hX * 4 + fX * i, y + s, z + hZ * 4 + fZ * i, -1));
                        blocks.add(place(Kind.GLASS, x + hoX * 4 + fX * i, y + s, z + hoZ * 4 + fZ * i, -1));
					}
				}
			}
		}

		for (int Y = 0; Y <= 3; Y++) {
            blocks.add(place(Kind.LOG, x + hX * 4 + fX * 4, y + Y, z + hZ * 4 + fZ * 4, -1));
            blocks.add(place(Kind.LOG, x + hoX * 4 + fX * 4, y + Y, z + hoZ * 4 + fZ * 4, -1));
		}

		//roof		
        blocks.add(place(Kind.PLANKS, x + hX, y + 6, z + hZ, -1));
        blocks.add(place(Kind.PLANKS, x + hoX, y + 6, z + hoZ, -1));

		for (int i = -1; i <= 9; i++) {
			for (int s = 3; s <= 7; s++) {
                blocks.add(place(Kind.STAIRS, x + hX * (8 - s) + fX * i, y + s, z + hZ * (8 - s) + fZ * i, GreenhouseLayouts.stairMeta(facing, false, false)));
                blocks.add(place(Kind.STAIRS, x + hoX * (8 - s) + fX * i, y + s, z + hoZ * (8 - s) + fZ * i, GreenhouseLayouts.stairMeta(facing, true, false)));
                blocks.add(place(Kind.PLANKS, x + fX * i, y + 7, z + fZ * i, -1));
			}
		}

		//under-roof
		for (int i = -1; i <= 9; i++) {
			if (i != 0) {
				if (i != 4 && i != 8) {
                    blocks.add(place(Kind.STAIRS, x + hX * 4 + fX * i, y + 3, z + hZ * 4 + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));
                    blocks.add(place(Kind.STAIRS, x + hoX * 4 + fX * i, y + 3, z + hoZ * 4 + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
				}
				if (i != 8) {
                    blocks.add(place(Kind.STAIRS, x + hX * 3 + fX * i, y + 4, z + hZ * 3 + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));
                    blocks.add(place(Kind.STAIRS, x + hX * 2 + fX * i, y + 5, z + hZ * 2 + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));
                    blocks.add(place(Kind.STAIRS, x + hX + fX * i, y + 6, z + hZ + fZ * i, GreenhouseLayouts.stairMeta(facing, true, true)));

                    blocks.add(place(Kind.STAIRS, x + hoX * 3 + fX * i, y + 4, z + hoZ * 3 + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
                    blocks.add(place(Kind.STAIRS, x + hoX * 2 + fX * i, y + 5, z + hoZ * 2 + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
                    blocks.add(place(Kind.STAIRS, x + hoX + fX * i, y + 6, z + hoZ + fZ * i, GreenhouseLayouts.stairMeta(facing, false, true)));
				}
			}
		}
		return blocks;
}

}
