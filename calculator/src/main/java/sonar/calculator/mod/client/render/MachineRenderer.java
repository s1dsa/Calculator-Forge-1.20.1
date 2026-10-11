package sonar.calculator.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import sonar.calculator.mod.client.model.*;
import sonar.calculator.mod.machine.MachineBlock;
import sonar.calculator.mod.machine.MachineBlockEntity;
import org.joml.Quaternionf;

/** Individual legacy geometry and animation paths. All state is read from server-synchronized data. */
public final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private final ModelPart chamber = ModelChamber.createLayer().bakeRoot();
    private final ModelPart processing = ModelProcessing.createLayer().bakeRoot();
    private final ModelPart split = ModelSplit.createLayer().bakeRoot();
    private final ModelPart mast = ModelConductorMast.createLayer().bakeRoot();
    private final ModelPart transmitter = ModelTransmitter.createLayer().bakeRoot();
    private final ModelPart scarecrow = ModelScarecrow.createLayer().bakeRoot();
    private final ModelPart crank = ModelCrankHandle.createLayer().bakeRoot();
    private final ModelPart docking = ModelDockingStation.createLayer().bakeRoot();
    private final ModelPart arm = ModelFabricationArm.createLayer().bakeRoot();
    private final ModelPart weatherBase = ModelWeatherStationBase.createLayer().bakeRoot();
    private final ModelPart weatherDish = ModelWeatherStationDish.createLayer().bakeRoot();
    private final ModelPart chest = LegacyVanillaModels.chest().bakeRoot();
    private final ModelPart sign = LegacyVanillaModels.sign().bakeRoot();
    private final Font font;

    // #3: the advanced power cube shows a per-face lightning symbol. The baked model only carries the
    // yellow INPUT face, so these overlays paint the OUTPUT (white) and DISABLED (plain) faces from the
    // server-synchronized side state.
    private static final ResourceLocation CUBE_FRONT_SLOT2 = tex("advancedpowercube_front_slot2");
    private static final ResourceLocation CUBE_SIDE_SLOT2 = tex("advancedpowercube_side_slot2");
    private static final ResourceLocation CUBE_DISABLED = tex("machine_side");
    private static final float[][] FACE_UV = {{0F,1F},{1F,1F},{1F,0F},{0F,0F}};
    /** Offset of the front face plane behind the model's 1px frame band (upstream {@code SonarMachine}). */
    private static final float FRAME_INSET = 1F / 16F;
    private static final float OVERLAY_EPS = 0.002F;

    public MachineRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }
    private static ResourceLocation tex(String name) { return new ResourceLocation("calculator", "textures/blocks/" + name + ".png"); }
    private static VertexConsumer vertices(MultiBufferSource buffers, String name) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("calculator", "textures/model/" + name + ".png")));
    }
    private static int packedLight(MachineBlockEntity block, int sampled) {
        if (block.getLevel() == null) return sampled;
        // BER geometry keeps the block's real sky/block light; no fullbright override.
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(block.getLevel(), block.getBlockPos());
    }
    private static float angle(MachineBlockEntity block) {
        Direction direction = block.getBlockState().getValue(MachineBlock.FACING);
        return switch (direction) { case NORTH -> 180F; case WEST -> 90F; case EAST -> 270F; default -> 0F; };
    }
    private static void begin(PoseStack poses, float angle) {
        poses.translate(0.5, 1.5, 0.5);
        // Exact half turn keeps the normal matrix orthogonal; legacy GL axis magnitude was irrelevant.
        poses.mulPose(Axis.XP.rotationDegrees(180));
        poses.mulPose(Axis.YP.rotationDegrees(angle));
    }
    private static void item(MachineBlockEntity block, ItemStack stack, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (!stack.isEmpty()) Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, poses, buffers, block.getLevel(), 0);
    }
    private static boolean flat(ItemStack stack, MachineBlockEntity block) {
        return !Minecraft.getInstance().getItemRenderer().getModel(stack, block.getLevel(), null, 0).isGui3d();
    }
    private static ItemStack stack(MachineBlockEntity block, int slot) {
        var inv = block.getInventory();
        return slot < inv.getSlots() ? inv.getStackInSlot(slot) : ItemStack.EMPTY;
    }
    @Override public void render(MachineBlockEntity block, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        String id = block.id();
        light = packedLight(block, light);
        poses.pushPose();
        switch (id) {
            case "conductormast", "transmitter", "scarecrow", "dockingstation" -> {
                begin(poses, angle(block));
                switch (id) {
                    case "conductormast" -> ModelConductorMast.render(mast, poses, vertices(buffers,"conductormask"), light, overlay);
                    case "transmitter" -> ModelTransmitter.render(transmitter, poses, vertices(buffers,"transmitter"), light, overlay);
                    case "scarecrow" -> ModelScarecrow.render(scarecrow, poses, vertices(buffers,"scarecrow"), light, overlay);
                    default -> {
                        ModelDockingStation.render(docking, poses, vertices(buffers,"dockingstation"), light, overlay);
                        ItemStack stack = stack(block, 6);
                        poses.scale(.8F,.8F,.8F);
                        if (flat(stack, block)) {
                            poses.mulPose(Axis.XP.rotationDegrees(135)); poses.translate(0,-.86,-.58); poses.scale(1.75F,1.75F,1.75F);
                        } else {
                            poses.mulPose(Axis.XP.rotationDegrees(180)); poses.mulPose(Axis.YP.rotationDegrees(180));
                            poses.mulPose(Axis.XP.rotationDegrees(45)); poses.translate(0,-.8,.5);
                        }
                        item(block,stack,poses,buffers,light,overlay);
                    }
                }
            }
            case "extractionchamber", "precisionchamber", "reassemblychamber", "restorationchamber", "processingchamber" -> {
                begin(poses, angle(block));
                var vertex = vertices(buffers, id.equals("precisionchamber") || id.equals("processingchamber") ? "machine_frame" : "machine_frame_black");
                ModelChamber.render(chamber, poses, vertex, light, overlay);
                poses.pushPose();
                float tick = block.getRenderPosition();
                if (id.equals("extractionchamber") || id.equals("precisionchamber")) {
                    poses.translate(0,tick / 4.5F,0); ModelSplit.renderSplitter(split,poses,vertex,light,overlay);
                    poses.translate(0,-tick / 4.5F,0); ModelSplit.render(split,poses,vertex,light,overlay);
                } else {
                    poses.translate(0,0,-tick / 2F); ModelProcessing.render(processing,poses,vertex,light,overlay);
                }
                poses.popPose();
                ItemStack target = stack(block, block.getProgress() == 0 && !stack(block,2).isEmpty() ? 2 : 0);
                if (flat(target,block)) { poses.mulPose(Axis.XP.rotationDegrees(90)); poses.translate(0,0,-1.17); poses.scale(.5F,.5F,.5F); }
                else { poses.mulPose(Axis.XP.rotationDegrees(180)); poses.mulPose(Axis.YP.rotationDegrees(180)); poses.scale(.6F,.6F,.6F); poses.translate(0,-2.05,0); }
                item(block,target,poses,buffers,light,overlay);
            }
            case "crankhandle" -> {
                poses.translate(.5,1.5,.5); poses.mulPose(Axis.ZP.rotationDegrees(180));
                poses.mulPose(Axis.YP.rotationDegrees(90 + block.getStateValue("angle") * 10 + angle(block)));
                ModelCrankHandle.render(crank,poses,vertices(buffers,"crank"),light,overlay);
            }
            case "fabricationchamber" -> {
                poses.translate(.5,1.5,.5); poses.mulPose(Axis.ZP.rotationDegrees(180));
                Direction facing=block.getBlockState().getValue(MachineBlock.FACING);
                poses.mulPose(Axis.YP.rotationDegrees(switch(facing) { case NORTH -> 0; case SOUTH -> 180; case WEST -> 270; default -> 90; }));
                float move=block.getStateValue("currentMoveTime");
                arm.getChild("Arm3").xRot=arm.getChild("Arm4").xRot=move * .5F / 100;
                poses.pushPose(); poses.scale(.5F,.5F,.5F); poses.translate(.5,.375,.75);
                poses.mulPose(Axis.YP.rotationDegrees(move / 2)); ModelFabricationArm.renderTile(arm,poses,vertices(buffers,"fabrication_arm_techne"),light,overlay);
                poses.mulPose(Axis.YP.rotationDegrees(-move / 2)); poses.translate(-1,0,0);
                poses.mulPose(Axis.YP.rotationDegrees(360-move / 2)); ModelFabricationArm.renderTile(arm,poses,vertices(buffers,"fabrication_arm_techne"),light,overlay);
                poses.popPose();
            }
            case "weatherstation" -> {
                begin(poses,0);
                float rotation=block.getStateValue("angle");
                if (rotation==1000) rotation=angle(block);
                poses.pushPose(); poses.mulPose(Axis.YP.rotationDegrees(-90));
                ModelWeatherStationBase.renderBase(weatherBase,poses,vertices(buffers,"weatherstation_base"),light,overlay); poses.popPose();
                poses.mulPose(Axis.YP.rotationDegrees(rotation));
                poses.pushPose(); poses.mulPose(Axis.YP.rotationDegrees(-90));
                ModelWeatherStationBase.render(weatherBase,poses,vertices(buffers,"weatherstation_base"),light,overlay); poses.popPose();
                poses.translate(0,-.9,-.84); poses.mulPose(Axis.XP.rotationDegrees(45));
                ModelWeatherStationDish.render(weatherDish,poses,vertices(buffers,"weatherstation_dish"),light,overlay);
            }
            case "reinforcedchest" -> {
                poses.translate(0,1,1); poses.scale(1,-1,-1); poses.translate(.5,.5,.5);
                poses.mulPose(Axis.YP.rotationDegrees(angle(block))); poses.translate(-.5,-.5,-.5);
                float lid=block.getLidAngle(partialTick);
                lid=1-(1-lid)*(1-lid)*(1-lid);
                chest.getAllParts().forEach(ModelPart::resetPose);
                chest.getChild("lid").xRot=chest.getChild("lock").xRot=-lid*(float)Math.PI/2;
                chest.render(poses,vertices(buffers,"reinforced_chest"),light,overlay);
            }
            case "calculatorscreenblock" -> {
                poses.translate(.5,.5,.5); poses.mulPose(Axis.YP.rotationDegrees(-angle(block))); poses.translate(0,-.3125,-.4375);
                poses.pushPose(); poses.scale(2F/3,-2F/3,-2F/3); sign.render(poses,vertices(buffers,"calculatorscreen"),light,overlay); poses.popPose();
                poses.translate(0,1F/3,.07F*2/3); poses.scale(1F/90,-1F/90,1F/90);
                text("C: " + block.getStateValue("latestEnergy"),-8,poses,buffers,light);
                text("M: " + block.getStateValue("latestMax"),4,poses,buffers,light);
            }
            case "analysingchamber" -> {
                ItemStack stack=stack(block,0);
                poses.translate(.5,.526,.5); poses.mulPose(Axis.YP.rotationDegrees(-angle(block)));
                if (flat(stack,block)) poses.mulPose(Axis.XP.rotationDegrees(90));
                poses.scale(.5F,.5F,.5F); item(block,stack,poses,buffers,light,overlay);
            }
            case "advancedpowercube" -> {
                // #3: paint OUTPUT (white) and DISABLED (plain) faces over the baked INPUT face.
                Direction front = block.getBlockState().getValue(MachineBlock.FACING);
                for (Direction side : Direction.values()) {
                    int mode = block.energySideMode(side);
                    if (mode == 0) continue; // INPUT already comes from the baked model (yellow)
                    boolean isFront = side == front;
                    ResourceLocation texture = mode == 1
                            ? (isFront ? CUBE_FRONT_SLOT2 : CUBE_SIDE_SLOT2) : CUBE_DISABLED;
                    face(buffers, poses, side, isFront, texture, light, overlay);
                }
            }
            default -> { /* Other machines use their own converted baked JSON geometry. */ }
        }
        poses.popPose();
    }
    /**
     * Paints one full-block quad onto a single face. Corners are emitted bottom-left, bottom-right,
     * top-right, top-left so the symbol stays upright; {@code front} selects the plane just in front
     * of the legacy 1px frame band of the {@code calculator:block/sonarmachine} parent model. The
     * offset is always toward the outside of the block (the non-front faces use +/-1 + eps for the
     * same reason), so the overlay is never hidden behind the model face it has to cover (#3).
     */
    private static void face(MultiBufferSource buffers, PoseStack poses, Direction side, boolean front, ResourceLocation texture, int light, int overlay) {
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float coord = front ? (positive ? 1F - FRAME_INSET + OVERLAY_EPS : FRAME_INSET - OVERLAY_EPS)
                            : (positive ? 1F + OVERLAY_EPS : -OVERLAY_EPS);
        float[][] corners = switch (side) {
            case NORTH -> new float[][]{{1,0,coord},{0,0,coord},{0,1,coord},{1,1,coord}};
            case SOUTH -> new float[][]{{0,0,coord},{1,0,coord},{1,1,coord},{0,1,coord}};
            case WEST  -> new float[][]{{coord,0,0},{coord,0,1},{coord,1,1},{coord,1,0}};
            case EAST  -> new float[][]{{coord,0,1},{coord,0,0},{coord,1,0},{coord,1,1}};
            case UP    -> new float[][]{{0,coord,1},{1,coord,1},{1,coord,0},{0,coord,0}};
            case DOWN  -> new float[][]{{0,coord,0},{1,coord,0},{1,coord,1},{0,coord,1}};
        };
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poses.last();
        org.joml.Matrix4f matrix = pose.pose();
        for (int i = 0; i < 4; i++) {
            consumer.vertex(matrix, corners[i][0], corners[i][1], corners[i][2])
                    .color(1F, 1F, 1F, 1F)
                    .uv(FACE_UV[i][0], FACE_UV[i][1])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(pose.normal(), side.getStepX(), side.getStepY(), side.getStepZ())
                    .endVertex();
        }
    }

    private void text(String value,int y,PoseStack poses,MultiBufferSource buffers,int light) {        font.drawInBatch(value,-font.width(value)/2F,y,0,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
    }
    @Override public boolean shouldRenderOffScreen(MachineBlockEntity block) {
        return switch(block.id()) { case "conductormast","weatherstation","transmitter","scarecrow" -> true; default -> false; };
    }
}
