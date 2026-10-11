package sonar.calculator.mod.machine.environment;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import sonar.calculator.mod.CalculatorConfig;
import sonar.calculator.mod.item.misc.CalculatorSeed;
import sonar.calculator.mod.machine.MachineBlockEntity;
import sonar.core.api.blocks.IStableBlock;
import static sonar.calculator.mod.machine.environment.GreenhouseLayouts.*;

/** Server-side construction and planting transactions; no unloaded chunks are read or modified. */
public final class GreenhouseLogic {
    private GreenhouseLogic() {}
    public static int tier(MachineBlockEntity m) { return m.id().equals("basicgreenhouse") ? 1 : m.id().equals("advancedgreenhouse") ? 2 : 3; }
    private static Direction forward(MachineBlockEntity m) { return m.facing().getOpposite(); }
    private static int seedStart(MachineBlockEntity m) { return tier(m) == 1 ? 5 : tier(m) == 2 ? 8 : 1; }
    private static boolean seed(ItemStack s) { return !s.isEmpty() && s.getItem() instanceof IPlantable; }
    private static boolean tagged(BlockState s, String tag) { return s.is(TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", tag))); }
    private static boolean matches(Kind k, BlockState s) {
        return switch(k) {
            case LOG -> s.is(BlockTags.LOGS);
            case PLANKS -> s.is(BlockTags.PLANKS);
            case STAIRS -> s.getBlock() instanceof StairBlock;
            case GLASS -> tagged(s,"glass") || tagged(s,"glass_panes");
        };
    }
    private static int[] slots(MachineBlockEntity m, Kind k) {
        if (tier(m) == 1) return new int[]{switch(k) { case LOG -> 0; case STAIRS -> 1; case GLASS -> 2; case PLANKS -> 3; }};
        return switch(k) { case LOG -> new int[]{0}; case STAIRS -> new int[]{1,2,3}; case GLASS -> new int[]{4,5}; case PLANKS -> new int[]{6}; };
    }
    public static boolean canInsert(MachineBlockEntity m, int slot, ItemStack s) {
        if (slot >= seedStart(m)) return seed(s);
        if (tier(m) == 3 || !(s.getItem() instanceof BlockItem item)) return false;
        for (Kind k : Kind.values()) for (int i : slots(m,k)) if (slot == i) return matches(k,item.getBlock().defaultBlockState());
        return false;
    }
    private static List<Place> structure(MachineBlockEntity m) { return tier(m) == 1 ? basic(m.getBlockPos(),forward(m),m.facing()) : advanced(m.getBlockPos(),forward(m),m.facing()); }
    private static boolean replaceable(BlockState s) { return s.canBeReplaced() || s.isAir() || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE) || s.is(Blocks.NETHERRACK); }
    private static boolean fail(MachineBlockEntity m, BlockPos p, String reason) { m.state.putLong("failedPos",p.asLong()); m.state.putString("failedReason",reason); return false; }
    private static boolean loaded(MachineBlockEntity m, BlockPos p) { return m.getLevel().hasChunkAt(p) && !m.getLevel().isOutsideBuildHeight(p); }
    public static void requestBuild(MachineBlockEntity m) {
        if (tier(m) == 3 || m.state.getInt("houseState") == 1) return;
        int[] required = tier(m) == 1 ? new int[]{18,14,30,56} : new int[]{30,94,42,183};
        for (Place p : structure(m)) {
            if (!loaded(m,p.pos())) { fail(m,p.pos(),"unloaded"); return; }
            BlockState s=m.getLevel().getBlockState(p.pos());
            if (!matches(p.type(),s) && !replaceable(s)) { fail(m,p.pos(),"cannot_replace"); return; }
        }
        long energy=0;
        for (Kind k : Kind.values()) {
            int count=0; for (int slot : slots(m,k)) { ItemStack s=m.inventory.getStackInSlot(slot); if (s.getItem() instanceof BlockItem b && matches(k,b.getBlock().defaultBlockState())) count+=s.getCount(); }
            if (count < required[k.ordinal()]) { m.state.putString("failedReason","requires_"+k.name().toLowerCase()); return; }
            energy+=(long)required[k.ordinal()]*CalculatorConfig.BUILD_ENERGY_USAGE;
        }
        if (m.energy.getEnergyStored() < energy) { m.state.putString("failedReason","requires_energy"); return; }
        m.state.putInt("houseState",1); m.state.remove("failedReason"); m.setChanged();
    }
    public static void requestDemolish(MachineBlockEntity m) {
        // The original flawless structure is player-built and has no demolition button.
        if (tier(m) != 3) { m.state.putInt("houseState",3); m.setChanged(); }
    }
    private static boolean fixedCheck(MachineBlockEntity m) {
        for (Place p : structure(m)) if (!loaded(m,p.pos()) || !matches(p.type(),m.getLevel().getBlockState(p.pos()))) return fail(m,p.pos(),p.type().name());
        return true;
    }
    private static void build(MachineBlockEntity m) {
        for (Place p : structure(m)) {
            if (!loaded(m,p.pos())) { fail(m,p.pos(),"unloaded"); return; }
            BlockState existing=m.getLevel().getBlockState(p.pos()); if (matches(p.type(),existing)) continue;
            if (!replaceable(existing) || m.energy.getEnergyStored() < CalculatorConfig.BUILD_ENERGY_USAGE) { fail(m,p.pos(),"blocked_or_no_energy"); return; }
            for (int slot : slots(m,p.type())) {
                ItemStack s=m.inventory.getStackInSlot(slot);
                if (s.getItem() instanceof BlockItem b && matches(p.type(),b.getBlock().defaultBlockState())) {
                    BlockState placed=b.getBlock().defaultBlockState();
                    if (p.type()==Kind.STAIRS) { Direction d=switch(p.meta() & 3) { case 0 -> Direction.EAST; case 1 -> Direction.WEST; case 2 -> Direction.SOUTH; default -> Direction.NORTH; }; placed=placed.setValue(StairBlock.FACING,d).setValue(StairBlock.HALF,p.meta()>=4 ? Half.TOP : Half.BOTTOM); }
                    if (m.getLevel().setBlock(p.pos(),placed,Block.UPDATE_ALL)) { m.inventory.extractItem(slot,1,false); m.energy.consume(CalculatorConfig.BUILD_ENERGY_USAGE); }
                    return; // Exactly one missing structure block per tick, as in 1.12.2.
                }
            }
            m.state.putInt("houseState",0); return;
        }
        complete(m); farmland(m);
    }
    private static void demolish(MachineBlockEntity m) {
        for (Place p : structure(m)) {
            if (!loaded(m,p.pos())) return;
            BlockState s=m.getLevel().getBlockState(p.pos()); if (!matches(p.type(),s)) continue;
            List<ItemStack> drops=Block.getDrops(s,(ServerLevel)m.getLevel(),p.pos(),m.getLevel().getBlockEntity(p.pos()));
            if (drops.isEmpty() && p.type()==Kind.GLASS) drops=List.of(new ItemStack(s.getBlock()));
            if (m.getLevel().removeBlock(p.pos(),false)) for (ItemStack drop : drops) m.drop(ItemHandlerHelper.insertItemStacked(m.inventory,drop,false));
        }
        BlockPos centre=m.getBlockPos().relative(forward(m),4);
        for (int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) {
            BlockPos p=centre.offset(x,-1,z); if (!loaded(m,p)) return; BlockState s=m.getLevel().getBlockState(p);
            if(s.is(Blocks.DIRT)||s.is(Blocks.FARMLAND)||s.is(Blocks.WATER)||s.canBeReplaced()) m.getLevel().setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        }
        m.state.putInt("houseState",0); m.state.putBoolean("wasBuilt",false);
    }
    private static boolean stable(BlockState s) { return s.getBlock() instanceof IStableBlock || MachineBlockEntity.blockId(s).equals("flawlessgreenhouse") || MachineBlockEntity.blockId(s).equals("co2generator"); }
    private static BlockPos local(MachineBlockEntity m,int h,int y,int f) { return m.getBlockPos().relative(forward(m),f).relative(forward(m).getClockWise(),h).above(y); }
    private static boolean section(MachineBlockEntity m,int f,boolean end) {
        for(int h=0;h<=3;h++) {
            BlockPos roof=local(m,h,2,f); if(!loaded(m,roof)) return false;
            BlockState s=m.getLevel().getBlockState(roof);
            if(!s.is(Blocks.QUARTZ_SLAB) || s.getValue(BlockStateProperties.SLAB_TYPE)!=net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) return fail(m,roof,"quartz_slab");
            if(end) { BlockPos floor=local(m,h,-1,f); if(!loaded(m,floor)||!stable(m.getLevel().getBlockState(floor))) return fail(m,floor,"stable_stone"); }
        }
        for(int h : new int[]{0,3}) for(int y=0;y<=1;y++) {
            BlockPos p=local(m,h,y,f); if(!loaded(m,p)) return false;
            BlockState s=m.getLevel().getBlockState(p);
            if(end ? !stable(s) : !MachineBlockEntity.blockId(s).equals("flawlessglass")) return fail(m,p,end ? "stable_stone" : "flawless_glass");
        }
        return true;
    }
    private static boolean flawlessCheck(MachineBlockEntity m) {
        m.state.putInt("houseSize",0); if(!section(m,0,true)) return false;
        for(int i=1;i<=65;i++) {
            if(i==65 || !section(m,i,false)) {
                if(i==1 || !section(m,i,true)) return false;
                m.state.putInt("houseSize",i-1); return true;
            }
        }
        return false;
    }
    public static List<BlockPos> plantArea(MachineBlockEntity m) {
        List<BlockPos> area=new ArrayList<>(); int t=tier(m);
        if(t==3) { for(int f=1;f<=m.state.getInt("houseSize")+1;f++) for(int h=1;h<=2;h++) area.add(local(m,h,0,f)); }
        else { int r=t==1 ? 1 : 3; BlockPos c=m.getBlockPos().relative(forward(m),t==1 ? 2 : 4); for(int z=-r;z<=r;z++) for(int x=-r;x<=r;x++) area.add(c.offset(x,0,z)); }
        return area;
    }
    private static void farmland(MachineBlockEntity m) {
        List<BlockPos> ground=new ArrayList<>(plantArea(m));
        if(tier(m)==3) for(int f=1;f<=m.state.getInt("houseSize")+1;f++) { ground.add(local(m,0,0,f)); ground.add(local(m,3,0,f)); }
        BlockPos centre=m.getBlockPos().relative(forward(m),tier(m)==1 ? 2 : 4);
        for(BlockPos plant : ground) {
            BlockPos p=plant.below(); if(!loaded(m,p)||!loaded(m,plant)) continue;
            int lateral=(plant.getX()-m.getBlockPos().getX())*forward(m).getClockWise().getStepX()+(plant.getZ()-m.getBlockPos().getZ())*forward(m).getClockWise().getStepZ();
            boolean water=tier(m)==1 ? plant.equals(centre) : tier(m)==2 ? Math.abs(plant.getX()-centre.getX())==3 && Math.abs(plant.getZ()-centre.getZ())==3 : lateral==0 || lateral==3;
            int cost=water ? CalculatorConfig.WATER_GENERATION_USAGE : CalculatorConfig.FARMLAND_GENERATION_USAGE;
            BlockState old=m.getLevel().getBlockState(p);
            if((old.is(Blocks.DIRT)||old.is(Blocks.GRASS_BLOCK)) && m.energy.getEnergyStored()>=cost && m.getLevel().setBlock(p,(water ? Blocks.WATER : Blocks.FARMLAND).defaultBlockState(),Block.UPDATE_ALL)) m.energy.consume(cost);
            if(tier(m)!=3 && !water) { BlockState s=m.getLevel().getBlockState(plant); if(!s.isAir()&&replaceable(s)) m.getLevel().removeBlock(plant,false); }
        }
    }
    private static void complete(MachineBlockEntity m) {
        if(!m.state.getBoolean("wasBuilt")) { m.state.putInt("carbon",0); m.state.putBoolean("wasBuilt",true); }
        m.state.putInt("houseState",2); m.state.remove("failedReason");
    }
    public static int growInterval(int oxygen,int tier) {
        if(tier==3) return oxygen>=90000 ? 200 : oxygen>=50000 ? 100 : oxygen>=30000 ? 50 : oxygen>=10000 ? 25 : 15;
        // Both building tiers called getGrowTicks(oxygen, 1) upstream.
        return oxygen>=90000 ? 400 : oxygen>=50000 ? 300 : oxygen>=30000 ? 200 : oxygen>=10000 ? 150 : 80;
    }
    private static void importSeeds(MachineBlockEntity m) {
        IItemHandler chest=m.adjacent(m.facing()); if(chest==null) return; int budget=32;
        for(int i=0;i<chest.getSlots() && budget>0;i++) {
            ItemStack offered=chest.extractItem(i,budget,true); if(!seed(offered)) continue;
            int accepted=offered.getCount()-ItemHandlerHelper.insertItemStacked(m.inventory,offered,true).getCount();
            if(accepted>0) { ItemStack removed=chest.extractItem(i,accepted,false); ItemStack rest=ItemHandlerHelper.insertItemStacked(m.inventory,removed,false); if(!rest.isEmpty()) m.drop(ItemHandlerHelper.insertItemStacked(chest,rest,false)); budget-=removed.getCount(); }
        }
    }
    private static void gas(MachineBlockEntity m,List<BlockPos> area) {
        int plants=0,lanterns=0,t=tier(m);
        // Preserve Basic's original unshifted plant-count coordinates.
        List<BlockPos> count=t==1 ? new ArrayList<>() : area;
        if(t==1) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) count.add(m.getBlockPos().offset(x,0,z));
        for(BlockPos p:count) if(loaded(m,p)&&m.getLevel().getBlockState(p).getBlock() instanceof BonemealableBlock) plants++;
        if(t!=3) for(BlockPos p:area) for(int y=0;y<=(t==1 ? 3 : 5);y++) {
            if(!loaded(m,p.above(y))) continue; BlockState s=m.getLevel().getBlockState(p.above(y)); String id=MachineBlockEntity.blockId(s);
            if((id.equals("gaslanternon") || id.equals("gaslanternoff")) && s.hasProperty(sonar.calculator.mod.machine.MachineBlock.ACTIVE) && s.getValue(sonar.calculator.mod.machine.MachineBlock.ACTIVE)) lanterns++;
        }
        int add;
        if(t==3) { BlockPos p=local(m,3,0,0); int generator=0; if(loaded(m,p)&&m.getLevel().getBlockEntity(p) instanceof MachineBlockEntity g && g.id().equals("co2generator")) generator=g.state.getInt("gasAdd"); add=m.getLevel().isDay() ? -plants*8+generator*2 : plants*2+generator*2; }
        else { int effective=t==2 ? plants/5 : plants; add=m.getLevel().isDay() ? -effective*8+lanterns*50 : effective*2+lanterns*50; }
        m.state.putInt("plants",plants); m.state.putInt("lanterns",lanterns); m.state.putInt("carbon",Math.max(0,Math.min(100000,m.state.getInt("carbon")+add)));
    }
    private static void plant(MachineBlockEntity m,List<BlockPos> area) {
        for(BlockPos p:area) {
            if(m.energy.getEnergyStored()<CalculatorConfig.PLANTING_ENERGY_USAGE) return;
            if(!loaded(m,p)||!m.getLevel().getBlockState(p).isAir()) continue;
            // The original selects the first available seed, even if it cannot plant here.
            for(int slot=seedStart(m);slot<m.inventory.getSlots();slot++) {
                ItemStack s=m.inventory.getStackInSlot(slot); if(!seed(s)) continue;
                if(s.getItem() instanceof CalculatorSeed custom && !custom.canTierUse(tier(m))) break;
                IPlantable plant=(IPlantable)s.getItem(); BlockState soil=m.getLevel().getBlockState(p.below()); BlockState crop=plant.getPlant(m.getLevel(),p);
                if(soil.canSustainPlant(m.getLevel(),p.below(),Direction.UP,plant) && crop.canSurvive(m.getLevel(),p) && m.getLevel().setBlock(p,crop,Block.UPDATE_ALL)) { m.inventory.extractItem(slot,1,false); m.energy.consume(CalculatorConfig.PLANTING_ENERGY_USAGE); }
                break;
            }
        }
    }
    private static void grow(MachineBlockEntity m,List<BlockPos> area) {
        if (m.energy.getEnergyStored() <= CalculatorConfig.GROWTH_ENERGY_USAGE) return;
        int timer=m.state.getInt("growTicks");
        if(timer==0) m.state.putInt("growTick",growInterval(100000-m.state.getInt("carbon"),tier(m)));
        timer++; int interval=m.state.getInt("growTick");
        if(timer>=interval && m.energy.getEnergyStored()>=CalculatorConfig.GROWTH_ENERGY_USAGE) {
            timer=0; for(int i=0;i<(tier(m)==3 ? m.state.getInt("houseSize") : 1);i++) {
                if(area.isEmpty()||m.energy.getEnergyStored()<=CalculatorConfig.GROWTH_ENERGY_USAGE) break;
                BlockPos p=area.get(m.getLevel().random.nextInt(area.size())); if(!loaded(m,p)) continue; BlockState s=m.getLevel().getBlockState(p);
                if(s.getBlock() instanceof BonemealableBlock b && b.isValidBonemealTarget(m.getLevel(),p,s,false)) { b.performBonemeal((ServerLevel)m.getLevel(),m.getLevel().random,p,s); m.energy.consume(CalculatorConfig.GROWTH_ENERGY_USAGE); if(tier(m)==3) m.state.putInt("plantsGrown",m.state.getInt("plantsGrown")+1); }
            }
        }
        m.state.putInt("growTicks",timer);
    }
    private static void harvest(MachineBlockEntity m,List<BlockPos> area) {
        for(BlockPos p:area) {
            if(m.energy.getEnergyStored()<Math.max(CalculatorConfig.PLANTING_ENERGY_USAGE,CalculatorConfig.GROWTH_ENERGY_USAGE)) return;
            if(!loaded(m,p)) continue; BlockState s=m.getLevel().getBlockState(p);
            if(s.getBlock() instanceof BonemealableBlock b && !b.isValidBonemealTarget(m.getLevel(),p,s,false)) {
                ItemStack tool=new ItemStack(Items.DIAMOND_HOE); tool.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE,tier(m));
                List<ItemStack> drops=Block.getDrops(s,(ServerLevel)m.getLevel(),p,m.getLevel().getBlockEntity(p),null,tool);
                if(m.getLevel().removeBlock(p,false)) { for(ItemStack drop:drops) m.addHarvest(drop); m.energy.consume(CalculatorConfig.GROWTH_ENERGY_USAGE); }
            }
        }
    }
    public static void tick(MachineBlockEntity m) {
        int t=tier(m); if(t!=3 && m.getLevel().hasNeighborSignal(m.getBlockPos())) { m.setActive(false); return; }
        int mode=m.state.getInt("houseState");
        if(mode==1) { build(m); return; } if(mode==3) { demolish(m); return; }
        if(m.tickCount()%50==0) { if(t==3 ? flawlessCheck(m) : fixedCheck(m)) { complete(m); farmland(m); } else m.state.putInt("houseState",0); }
        if(m.state.getInt("houseState")!=2) { m.setActive(false); return; }
        List<BlockPos> area=plantArea(m);
        if(m.tickCount()%20==0) { importSeeds(m); gas(m,area); }
        boolean active=!m.state.getBoolean("paused"); m.setActive(active);
        if(active) { plant(m,area); grow(m,area); harvest(m,area); }
    }
}
