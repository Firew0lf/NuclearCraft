package nc.multiblock.heatExchanger.tile;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import nc.ModCheck;
import nc.config.NCConfig;
import nc.multiblock.cuboidal.CuboidalPartPositionType;
import nc.multiblock.heatExchanger.HeatExchanger;
import nc.multiblock.heatExchanger.HeatExchangerTubeSetting;
import nc.multiblock.heatExchanger.HeatExchangerTubeType;
import nc.recipe.AbstractRecipeHandler;
import nc.recipe.IngredientSorption;
import nc.recipe.NCRecipes;
import nc.recipe.ProcessorRecipe;
import nc.recipe.ProcessorRecipeHandler;
import nc.recipe.ingredient.IFluidIngredient;
import nc.tile.fluid.ITileFluid;
import nc.tile.internal.fluid.FluidConnection;
import nc.tile.internal.fluid.FluidTileWrapper;
import nc.tile.internal.fluid.GasTileWrapper;
import nc.tile.internal.fluid.Tank;
import nc.tile.internal.fluid.TankSorption;
import nc.tile.passive.ITilePassive;
import nc.tile.processor.IFluidProcessor;
import nc.util.GasHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class TileHeatExchangerTube extends TileHeatExchangerPartBase implements IFluidProcessor, ITileFluid {
	
	private final @Nonnull List<Tank> tanks = Lists.newArrayList(new Tank(32000, NCRecipes.heat_exchanger_valid_fluids.get(0)), new Tank(64000, new ArrayList<String>()));
	
	private @Nonnull FluidConnection[] fluidConnections = ITileFluid.fluidConnectionAll(Lists.newArrayList(TankSorption.NON, TankSorption.NON));
	
	private @Nonnull FluidTileWrapper[] fluidSides;
	
	private @Nonnull GasTileWrapper gasWrapper;
	
	private @Nonnull HeatExchangerTubeSetting[] tubeSettings = new HeatExchangerTubeSetting[] {HeatExchangerTubeSetting.DISABLED, HeatExchangerTubeSetting.DISABLED, HeatExchangerTubeSetting.DISABLED, HeatExchangerTubeSetting.DISABLED, HeatExchangerTubeSetting.DISABLED, HeatExchangerTubeSetting.DISABLED};
	
	public final int fluidInputSize = 1, fluidOutputSize = 1;
	
	public final int defaultProcessTime = 16000;
	public double baseProcessTime = defaultProcessTime;
	
	private int fluidToHold;
	
	public double time;
	public boolean isProcessing, canProcessInputs;
	public double speedMultiplier = 0;
	
	private int inputTemperature = 0, outputTemperature = 0;
	private EnumFacing flowDir = null;
	
	public final NCRecipes.Type recipeType = NCRecipes.Type.HEAT_EXCHANGER;
	protected ProcessorRecipe recipe, cachedRecipe;
	
	private final double conductivity;
	
	protected int tubeCount;
	
	public static class Copper extends TileHeatExchangerTube {
		
		public Copper() {
			super(HeatExchangerTubeType.COPPER);
		}
	}
	
	public static class HardCarbon extends TileHeatExchangerTube {
		
		public HardCarbon() {
			super(HeatExchangerTubeType.HARD_CARBON);
		}
	}
	
	public static class Thermoconducting extends TileHeatExchangerTube {
		
		public Thermoconducting() {
			super(HeatExchangerTubeType.THERMOCONDUCTING);
		}
	}
	
	private TileHeatExchangerTube(HeatExchangerTubeType tubeType) {
		super(CuboidalPartPositionType.INTERIOR);
		fluidSides = ITileFluid.getDefaultFluidSides(this);
		gasWrapper = new GasTileWrapper(this);
		
		this.conductivity = tubeType.getConductivity();
	}
	
	@Override
	public void onMachineAssembled(HeatExchanger controller) {
		doStandardNullControllerResponse(controller);
		super.onMachineAssembled(controller);
		if (getWorld().isRemote) return;
	}
	
	@Override
	public void onMachineBroken() {
		super.onMachineBroken();
		if (getWorld().isRemote) return;
		//getWorld().setBlockState(getPos(), getWorld().getBlockState(getPos()), 2);
	}
	
	public int checkPosition() {
		if (!isMultiblockAssembled() || !canProcessInputs) {
			speedMultiplier = 0;
			return 0;
		}
		
		int adjCount = 0;
		double speedCount = 0;
		
		for (EnumFacing dir : EnumFacing.VALUES) {
			double mult = getTubeSpeedMultiplier(dir);
			speedCount += mult;
			if (mult > 0) adjCount++;
		}
		
		speedMultiplier = speedCount;
		return adjCount;
	}
	
	private double getTubeSpeedMultiplier(EnumFacing dir) {
		TileEntity tile = world.getTileEntity(pos.offset(dir));
		if (!(tile instanceof TileHeatExchangerTube)) return 0;
		TileHeatExchangerTube tube = (TileHeatExchangerTube) tile;
		
		if (!tube.canProcessInputs || (requiresContraflow(tube) && !isContraflow(tube))) return 0;
		
		if (!canConnectFluid(dir) || !tube.canConnectFluid(dir.getOpposite())) {
			return conductivityMult()*getRawTubeSpeedMultiplier(tube);
		}
		return 0;
	}
	
	private double getRawTubeSpeedMultiplier(TileHeatExchangerTube tube) {
		return isHeating() != tube.isHeating() ? tube.getAbsRecipeTempDiff() : (isHeating() ? -getAbsInputTempDiff(tube) : 0);
	}
	
	private boolean isContraflow(TileHeatExchangerTube tube) {
		if (flowDir == null || tube.flowDir == null) return !(flowDir == null ^ tube.flowDir == null);
		return flowDir.getIndex() != tube.flowDir.getIndex();
	}
	
	private boolean requiresContraflow(TileHeatExchangerTube tube) {
		return inputTemperature > tube.inputTemperature ^ outputTemperature > tube.outputTemperature;
	}
	
	// Ticking
	
	@Override
	public void onAdded() {
		super.onAdded();
		if (!world.isRemote) {
			refreshRecipe();
			refreshActivity();
			isProcessing = isProcessing();
		}
	}
	
	@Override
	public void update() {
		super.update();
		updateTube();
	}
	
	public void updateTube() {
		if (!world.isRemote) {
			setIsHeatExchangerOn();
			boolean wasProcessing = isProcessing;
			isProcessing = isProcessing();
			boolean shouldUpdate = false;
			tickTube();
			if (isProcessing) process();
			if (wasProcessing != isProcessing) {
				shouldUpdate = true;
			}
			if (tubeCount == 0) {
				pushFluid();
				refreshRecipe();
				refreshActivity();
			}
			if (shouldUpdate) markDirty();
		}
	}
	
	public void tickTube() {
		tubeCount++; tubeCount %= NCConfig.machine_update_rate / 4;
	}
	
	@Override
	public void refreshRecipe() {
		if (recipe == null || !recipe.matchingInputs(new ArrayList<ItemStack>(), getFluidInputs())) {
			/** Temporary caching while looking for recipe map solution */
			if (cachedRecipe != null && cachedRecipe.matchingInputs(new ArrayList<ItemStack>(), getFluidInputs())) {
				recipe = cachedRecipe;
			}
			else {
				recipe = getRecipeHandler().getRecipeFromInputs(new ArrayList<ItemStack>(), getFluidInputs());
			}
			if (recipe != null) {
				cachedRecipe = recipe;
			}
		}
	}
	
	@Override
	public void refreshActivity() {
		canProcessInputs = canProcessInputs(false);
	}
	
	@Override
	public void refreshActivityOnProduction() {
		canProcessInputs = canProcessInputs(true);
	}
	
	// Processor Stats
	
	public double getSpeedMultiplier() {
		return speedMultiplier;
	}
	
	private int getAbsRecipeTempDiff() {
		return Math.abs(inputTemperature - outputTemperature);
	}
	
	private int getAbsInputTempDiff(TileHeatExchangerTube tube) {
		return Math.abs(inputTemperature - tube.inputTemperature);
	}
	
	private double conductivityMult() {
		return isHeating() ? conductivity : 1D/conductivity;
	}
	
	private boolean isHeating() {
		return inputTemperature < outputTemperature;
	}
	
	public boolean setRecipeStats() {
		if (recipe == null) {
			baseProcessTime = defaultProcessTime;
			inputTemperature = 0;
			outputTemperature = 0;
			return false;
		}
		baseProcessTime = recipe.getHeatExchangerProcessTime(defaultProcessTime);
		fluidToHold = getFluidToHold();
		inputTemperature = recipe.getHeatExchangerInputTemperature();
		outputTemperature = recipe.getHeatExchangerOutputTemperature();
		return true;
	}
	
	private int getFluidToHold() {
		return Math.min(8000, getFluidIngredients().get(0).getMaxStackSize()*NCConfig.machine_update_rate / 4);
	}
	
	// Processing
	
	public boolean isProcessing() {
		return readyToProcess() && isHeatExchangerOn;
	}
	
	public boolean readyToProcess() {
		return canProcessInputs && isMultiblockAssembled();
	}
	
	public boolean canProcessInputs(boolean justProduced) {
		if (!setRecipeStats()) return false;
		else if (!justProduced && time >= baseProcessTime) return true;
		return canProduceProducts();
	}
	
	public boolean canProduceProducts() {
		for (int j = 0; j < fluidOutputSize; j++) {
			IFluidIngredient fluidProduct = getFluidProducts().get(j);
			if (fluidProduct.getMaxStackSize() <= 0) continue;
			if (fluidProduct.getStack() == null) return false;
			else if (!tanks.get(j + fluidInputSize).isEmpty()) {
				if (!tanks.get(j + fluidInputSize).getFluid().isFluidEqual(fluidProduct.getStack())) {
					return false;
				} else if (tanks.get(j + fluidInputSize).getFluidAmount() + fluidProduct.getMaxStackSize() > tanks.get(j + fluidInputSize).getCapacity()) {
					return false;
				}
			}
		}
		return true;
	}
	
	public void process() {
		time = Math.max(0, time + getSpeedMultiplier());
		if (time >= baseProcessTime) finishProcess();
	}
	
	public void finishProcess() {
		double oldProcessTime = baseProcessTime;
		produceProducts();
		refreshRecipe();
		if (!setRecipeStats()) time = 0;
		else time = MathHelper.clamp(time - oldProcessTime, 0D, baseProcessTime);
		refreshActivityOnProduction();
		if (!canProcessInputs) time = 0;
	}
	
	public void produceProducts() {
		if (recipe == null) return;
		List<Integer> fluidInputOrder = getFluidInputOrder();
		if (fluidInputOrder == AbstractRecipeHandler.INVALID) return;
		
		for (int i = 0; i < fluidInputSize; i++) {
			int fluidIngredientStackSize = getFluidIngredients().get(fluidInputOrder.get(i)).getMaxStackSize();
			if (fluidIngredientStackSize > 0) tanks.get(i).changeFluidAmount(-fluidIngredientStackSize);
			if (tanks.get(i).getFluidAmount() <= 0) tanks.get(i).setFluidStored(null);
		}
		for (int j = 0; j < fluidOutputSize; j++) {
			IFluidIngredient fluidProduct = getFluidProducts().get(j);
			if (fluidProduct.getMaxStackSize() <= 0) continue;
			if (tanks.get(j + fluidInputSize).isEmpty()) {
				tanks.get(j + fluidInputSize).setFluidStored(fluidProduct.getNextStack());
			} else if (tanks.get(j + fluidInputSize).getFluid().isFluidEqual(fluidProduct.getStack())) {
				tanks.get(j + fluidInputSize).changeFluidAmount(fluidProduct.getNextStackSize());
			}
		}
	}
	
	// IProcessor
	
	@Override
	public ProcessorRecipeHandler getRecipeHandler() {
		return recipeType.getRecipeHandler();
	}
	
	@Override
	public ProcessorRecipe getRecipe() {
		return recipe;
	}
	
	@Override
	public List<Tank> getFluidInputs() {
		return tanks.subList(0, fluidInputSize);
	}
	
	@Override
	public List<IFluidIngredient> getFluidIngredients() {
		return recipe.fluidIngredients();
	}
	
	@Override
	public List<IFluidIngredient> getFluidProducts() {
		return recipe.fluidProducts();
	}
	
	@Override
	public List<Integer> getFluidInputOrder() {
		List<Integer> fluidInputOrder = new ArrayList<Integer>();
		List<IFluidIngredient> fluidIngredients = recipe.fluidIngredients();
		for (int i = 0; i < fluidInputSize; i++) {
			int position = -1;
			for (int j = 0; j < fluidIngredients.size(); j++) {
				if (fluidIngredients.get(j).matches(getFluidInputs().get(i), IngredientSorption.INPUT)) {
					position = j;
					break;
				}
			}
			if (position == -1) return AbstractRecipeHandler.INVALID;
			fluidInputOrder.add(position);
		}
		return fluidInputOrder;
	}
	
	// Fluids
	
	@Override
	@Nonnull
	public List<Tank> getTanks() {
		return tanks;
	}

	@Override
	@Nonnull
	public FluidConnection[] getFluidConnections() {
		return fluidConnections;
	}
	
	@Override
	public void setFluidConnections(@Nonnull FluidConnection[] connections) {
		fluidConnections = connections;
	}

	@Override
	@Nonnull
	public FluidTileWrapper[] getFluidSides() {
		return fluidSides;
	}
	
	@Override
	public @Nonnull GasTileWrapper getGasWrapper() {
		return gasWrapper;
	}
	
	public @Nonnull HeatExchangerTubeSetting[] getTubeSettings() {
		return tubeSettings;
	}
	
	public void setTubeSettings(@Nonnull HeatExchangerTubeSetting[] settings) {
		tubeSettings = settings;
	}
	
	public HeatExchangerTubeSetting getTubeSetting(@Nonnull EnumFacing side) {
		return tubeSettings[side.getIndex()];
	}
	
	public void setTubeSetting(@Nonnull EnumFacing side, @Nonnull HeatExchangerTubeSetting setting) {
		tubeSettings[side.getIndex()] = setting;
	}
	
	public void toggleTubeSetting(@Nonnull EnumFacing side) {
		setTubeSetting(side, getTubeSetting(side).next());
		switch (getTubeSetting(side)) {
		case DISABLED:
			setTankSorption(side, 0, TankSorption.NON);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		case DEFAULT:
			setTankSorption(side, 0, TankSorption.IN);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		case PRODUCT_OUT:
			setTankSorption(side, 0, TankSorption.NON);
			setTankSorption(side, 1, TankSorption.OUT);
			break;
		case INPUT_SPREAD:
			setTankSorption(side, 0, TankSorption.OUT);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		default:
			setTankSorption(side, 0, TankSorption.NON);
			setTankSorption(side, 1, TankSorption.NON);
			break;
		}
		updateFlowDir();
		markAndRefresh();
	}
	
	public void updateFlowDir() {
		for (EnumFacing side : EnumFacing.VALUES) {
			HeatExchangerTubeSetting thisSetting = getTubeSetting(side);
			if (thisSetting == HeatExchangerTubeSetting.DISABLED) continue;
			
			TileEntity tile = getTileWorld().getTileEntity(getTilePos().offset(side));
			
			if (tile instanceof TileHeatExchangerVent) {
				if (thisSetting == HeatExchangerTubeSetting.DEFAULT) {
					flowDir = side.getOpposite();
					return;
				}
				else if (thisSetting == HeatExchangerTubeSetting.PRODUCT_OUT) {
					flowDir = side;
					return;
				}
			}
			else if (tile instanceof TileHeatExchangerTube) {
				TileHeatExchangerTube tube = (TileHeatExchangerTube)tile;
				HeatExchangerTubeSetting tubeSetting = tube.getTubeSetting(side.getOpposite());
				
				if ((thisSetting == HeatExchangerTubeSetting.INPUT_SPREAD && tubeSetting == HeatExchangerTubeSetting.DEFAULT) || (thisSetting == HeatExchangerTubeSetting.PRODUCT_OUT && (tubeSetting == HeatExchangerTubeSetting.DEFAULT || tubeSetting == HeatExchangerTubeSetting.INPUT_SPREAD))) {
					flowDir = side;
					return;
				}
			}
		}
		
		flowDir = null;
	}
	
	@Override
	public void pushFluidToSide(@Nonnull EnumFacing side) {
		HeatExchangerTubeSetting thisSetting = getTubeSetting(side);
		if (thisSetting == HeatExchangerTubeSetting.DISABLED) return;
		
		TileEntity tile = getTileWorld().getTileEntity(getTilePos().offset(side));
		
		if (tile instanceof TileHeatExchangerTube) {
			TileHeatExchangerTube tube = (TileHeatExchangerTube)tile;
			HeatExchangerTubeSetting tubeSetting = tube.getTubeSetting(side.getOpposite());
			
			if (thisSetting == HeatExchangerTubeSetting.INPUT_SPREAD) {
				if (tubeSetting == HeatExchangerTubeSetting.DEFAULT) {
					pushInputFluid(tube);
					pushProduct(tube);
				} else if (tubeSetting == HeatExchangerTubeSetting.PRODUCT_OUT) {
					pushInputFluid(tube);
				}
			} else if (thisSetting == HeatExchangerTubeSetting.PRODUCT_OUT && (tubeSetting == HeatExchangerTubeSetting.DEFAULT || tubeSetting == HeatExchangerTubeSetting.INPUT_SPREAD)) {
				pushProduct(tube);
			}
		}
		
		else if (thisSetting == HeatExchangerTubeSetting.PRODUCT_OUT) {
			if (tile instanceof ITilePassive) if (!((ITilePassive) tile).canPushFluidsTo()) return;
			IFluidHandler adjStorage = tile == null ? null : tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
			
			if (adjStorage == null) return;
			
			for (int i = 0; i < getTanks().size(); i++) {
				if (getTanks().get(i).getFluid() == null || !getTankSorption(side, i).canDrain()) continue;
				
				getTanks().get(i).drainInternal(adjStorage.fill(getTanks().get(i).drainInternal(getTanks().get(i).getCapacity(), false), true), true);
			}
		}
	}
	
	public void pushInputFluid(TileHeatExchangerTube other) {
		if (getTanks().get(0).getFluidAmount() > other.getTanks().get(0).getFluidAmount()) {
			getTanks().get(0).drainInternal(other.getTanks().get(0).fillInternal(getTanks().get(0).drainInternal(getTanks().get(0).getFluidAmount() - fluidToHold, false), true), true);
		}
	}
	
	public void pushProduct(TileHeatExchangerTube other) {
		getTanks().get(1).drainInternal(other.getTanks().get(1).fillInternal(getTanks().get(1).drainInternal(getTanks().get(1).getCapacity(), false), true), true);
	}

	@Override
	public boolean getInputTanksSeparated() {
		return false;
	}

	@Override
	public void setInputTanksSeparated(boolean separated) {}

	@Override
	public boolean getVoidUnusableFluidInput(int tankNumber) {
		return false;
	}

	@Override
	public void setVoidUnusableFluidInput(int tankNumber, boolean voidUnusableFluidInput) {}

	@Override
	public boolean getVoidExcessFluidOutput(int tankNumber) {
		return false;
	}

	@Override
	public void setVoidExcessFluidOutput(int tankNumber, boolean voidExcessFluidOutput) {}
	
	@Override
	public boolean hasConfigurableFluidConnections() {
		return true;
	}
	
	// NBT
	
	public NBTTagCompound writeTubeSettings(NBTTagCompound nbt) {
		NBTTagCompound settingsTag = new NBTTagCompound();
		for (EnumFacing side : EnumFacing.VALUES) {
			settingsTag.setInteger("setting" + side.getIndex(), getTubeSetting(side).ordinal());
		}
		nbt.setTag("tubeSettings", settingsTag);
		return nbt;
	}
	
	public void readTubeSettings(NBTTagCompound nbt) {
		if (nbt.hasKey("fluidConnections0")) {
			for (EnumFacing side : EnumFacing.VALUES) {
				TankSorption sorption = TankSorption.values()[nbt.getInteger("fluidConnections" + side.getIndex())];
				switch (sorption) {
				case NON:
					setTankSorption(side, 0, TankSorption.NON);
					setTankSorption(side, 1, TankSorption.NON);
					setTubeSetting(side, HeatExchangerTubeSetting.DISABLED);
					break;
				case BOTH:
					setTankSorption(side, 0, TankSorption.IN);
					setTankSorption(side, 1, TankSorption.NON);
					setTubeSetting(side, HeatExchangerTubeSetting.DEFAULT);
					break;
				case IN:
					setTankSorption(side, 0, TankSorption.NON);
					setTankSorption(side, 1, TankSorption.OUT);
					setTubeSetting(side, HeatExchangerTubeSetting.PRODUCT_OUT);
					break;
				case OUT:
					setTankSorption(side, 0, TankSorption.OUT);
					setTankSorption(side, 1, TankSorption.NON);
					setTubeSetting(side, HeatExchangerTubeSetting.INPUT_SPREAD);
					break;
				default:
					setTankSorption(side, 0, TankSorption.NON);
					setTankSorption(side, 1, TankSorption.NON);
					setTubeSetting(side, HeatExchangerTubeSetting.DISABLED);
					break;
				}
			}
		}
		else {
			NBTTagCompound settingsTag = nbt.getCompoundTag("tubeSettings");
			for (EnumFacing side : EnumFacing.VALUES) {
				setTubeSetting(side, HeatExchangerTubeSetting.values()[settingsTag.getInteger("setting" + side.getIndex())]);
			}
		}
	}
	
	@Override
	public NBTTagCompound writeAll(NBTTagCompound nbt) {
		super.writeAll(nbt);
		writeTanks(nbt);
		writeFluidConnections(nbt);
		writeTubeSettings(nbt);
		
		nbt.setDouble("baseProcessTime", baseProcessTime);
		
		nbt.setDouble("time", time);
		nbt.setBoolean("isProcessing", isProcessing);
		nbt.setBoolean("canProcessInputs", canProcessInputs);
		nbt.setDouble("speedMultiplier", speedMultiplier);
		
		nbt.setInteger("fluidToHold", fluidToHold);
		
		nbt.setInteger("inputTemperature", inputTemperature);
		nbt.setInteger("outputTemperature", outputTemperature);
		
		nbt.setInteger("flowDir", flowDir == null ? -1 : flowDir.getIndex());
		return nbt;
	}
	
	@Override
	public void readAll(NBTTagCompound nbt) {
		super.readAll(nbt);
		readTanks(nbt);
		readFluidConnections(nbt);
		readTubeSettings(nbt);
		
		baseProcessTime = nbt.getDouble("baseProcessTime");
		
		time = nbt.getDouble("time");
		isProcessing = nbt.getBoolean("isProcessing");
		canProcessInputs = nbt.getBoolean("canProcessInputs");
		speedMultiplier = nbt.getDouble("speedMultiplier");
		
		fluidToHold = nbt.getInteger("fluidToHold");
		
		inputTemperature = nbt.getInteger("inputTemperature");
		outputTemperature = nbt.getInteger("outputTemperature");
		
		flowDir = nbt.getInteger("flowDir") == -1 ? null : EnumFacing.VALUES[nbt.getInteger("flowDir")];
	}
	
	// Capability
	
	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
		if (!getTanks().isEmpty() && hasFluidSideCapability(side)) {
			side = nonNullSide(side);
			if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return getFluidSide(side) != null;
			if (ModCheck.mekanismLoaded()) if (GasHelper.isGasCapability(capability)) return getGasWrapper() != null;
		}
		return super.hasCapability(capability, side);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
		if (!getTanks().isEmpty() && hasFluidSideCapability(side)) {
			side = nonNullSide(side);
			if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return (T) getFluidSide(side);
			if (ModCheck.mekanismLoaded()) if (GasHelper.isGasCapability(capability)) return (T) getGasWrapper();
		}
		return super.getCapability(capability, side);
	}
}
