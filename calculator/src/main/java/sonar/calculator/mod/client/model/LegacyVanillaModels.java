package sonar.calculator.mod.client.model;

import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;

/** Legacy vanilla ModelChest/ModelSign geometry, retaining the original 64x64/64x32 UV layouts. */
public final class LegacyVanillaModels {
    private LegacyVanillaModels() {}
    public static LayerDefinition chest() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(0, -5, -14, 14, 5, 14), PartPose.offset(1, 7, 15));
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 19).addBox(0, 0, 0, 14, 10, 14), PartPose.offset(1, 6, 1));
        root.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -2, -15, 2, 4, 1), PartPose.offset(8, 7, 15));
        return LayerDefinition.create(mesh, 64, 64);
    }
    public static LayerDefinition sign() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("board", CubeListBuilder.create().texOffs(0, 0).addBox(-12, -14, -1, 24, 12, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
}
