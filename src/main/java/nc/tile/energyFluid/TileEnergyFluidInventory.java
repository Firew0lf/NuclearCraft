package nc.tile.energyFluid;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import nc.Global;
import nc.tile.internal.energy.EnergyConnection;
import nc.tile.internal.fluid.FluidConnection;
import nc.tile.inventory.ITileInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public abstract class TileEnergyFluidInventory extends TileEnergyFluid implements IInventory, ITileInventory {
	
	public @Nonnull String inventoryName;
	public @Nonnull NonNullList<ItemStack> inventoryStacks;
	
	public TileEnergyFluidInventory(String name, int size, int capacity, @Nonnull EnergyConnection[] energyConnections, int fluidCapacity, List<String> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		this(name, size, capacity, capacity, energyConnections, fluidCapacity, fluidCapacity, allowedFluids, fluidConnections);
	}
	
	public TileEnergyFluidInventory(String name, int size, int capacity, @Nonnull EnergyConnection[] energyConnections, @Nonnull List<Integer> fluidCapacity, List<List<String>> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		this(name, size, capacity, capacity, energyConnections, fluidCapacity, fluidCapacity, allowedFluids, fluidConnections);
	}
	
	public TileEnergyFluidInventory(String name, int size, int capacity, @Nonnull EnergyConnection[] energyConnections, int fluidCapacity, int maxFluidTransfer, List<String> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		this(name, size, capacity, capacity, energyConnections, fluidCapacity, maxFluidTransfer, allowedFluids, fluidConnections);
	}
	
	public TileEnergyFluidInventory(String name, int size, int capacity, @Nonnull EnergyConnection[] energyConnections, @Nonnull List<Integer> fluidCapacity, @Nonnull List<Integer> maxFluidTransfer, List<List<String>> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		this(name, size, capacity, capacity, energyConnections, fluidCapacity, maxFluidTransfer, allowedFluids, fluidConnections);
	}
	
	public TileEnergyFluidInventory(String name, int size, int capacity, int maxTransfer, @Nonnull EnergyConnection[] energyConnections, int fluidCapacity, List<String> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		this(name, size, capacity, maxTransfer, energyConnections, fluidCapacity, fluidCapacity, allowedFluids, fluidConnections);
	}
	
	public TileEnergyFluidInventory(String name, int size, int capacity, int maxTransfer, @Nonnull EnergyConnection[] energyConnections, @Nonnull List<Integer> fluidCapacity, List<List<String>> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		this(name, size, capacity, maxTransfer, energyConnections, fluidCapacity, fluidCapacity, allowedFluids, fluidConnections);
	}
	
	public TileEnergyFluidInventory(String name, int size, int capacity, int maxTransfer, @Nonnull EnergyConnection[] energyConnections, int fluidCapacity, int maxFluidTransfer, List<String> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		super(capacity, maxTransfer, energyConnections, fluidCapacity, maxFluidTransfer, allowedFluids, fluidConnections);
		inventoryName = Global.MOD_ID + ".container." + name;
		inventoryStacks = NonNullList.<ItemStack>withSize(size, ItemStack.EMPTY);
	}
	
	public TileEnergyFluidInventory(@Nonnull String name, int size, int capacity, int maxTransfer, EnergyConnection[] energyConnections, @Nonnull List<Integer> fluidCapacity, @Nonnull List<Integer> maxFluidTransfer, List<List<String>> allowedFluids, @Nonnull FluidConnection[] fluidConnections) {
		super(capacity, maxTransfer, energyConnections, fluidCapacity, maxFluidTransfer, allowedFluids, fluidConnections);
		inventoryName = Global.MOD_ID + ".container." + name;
		inventoryStacks = NonNullList.<ItemStack>withSize(size, ItemStack.EMPTY);
	}
	
	// Inventory Name
	
	@Override
	public String getName() {
		return inventoryName;
	}
	
	@Override
	public boolean hasCustomName() {
		return false;
	}
	
	// Inventory
	
	@Override
	public int getSizeInventory() {
		return inventoryStacks.size();
	}
	
	@Override
	public boolean isEmpty() {
		for (ItemStack itemstack : inventoryStacks) {
			if (!itemstack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inventoryStacks.get(slot);
	}

	@Override
	public ItemStack decrStackSize(int index, int count) {
		return ItemStackHelper.getAndSplit(inventoryStacks, index, count);
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		return ItemStackHelper.getAndRemove(inventoryStacks, index);
	}

	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		ItemStack itemstack = inventoryStacks.get(index);
		boolean flag = !stack.isEmpty() && stack.isItemEqual(itemstack) && nc.util.ItemStackHelper.areItemStackTagsEqual(stack, itemstack);
		inventoryStacks.set(index, stack);

		if (stack.getCount() > getInventoryStackLimit()) {
			stack.setCount(getInventoryStackLimit());
		}

		if (index == 0 && !flag) {
			markDirty();
		}
	}
		
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return true;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}
		
	@Override
	public void clear() {
		inventoryStacks.clear();
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return world.getTileEntity(pos) != this ? false : player.getDistanceSq((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory(EntityPlayer player) {}

	@Override
	public void closeInventory(EntityPlayer player) {}
	
	@Override
	public NonNullList<ItemStack> getInventoryStacks() {
		return inventoryStacks;
	}
	
	// NBT
	
	@Override
	public NBTTagCompound writeAll(NBTTagCompound nbt) {
		super.writeAll(nbt);
		ItemStackHelper.saveAllItems(nbt, inventoryStacks);
		return nbt;
	}
		
	@Override
	public void readAll(NBTTagCompound nbt) {
		super.readAll(nbt);
		inventoryStacks = NonNullList.<ItemStack>withSize(getSizeInventory(), ItemStack.EMPTY);
		ItemStackHelper.loadAllItems(nbt, inventoryStacks);
	}
	
	// Inventory Fields
	
	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {}
		
	@Override
	public int getFieldCount() {
		return 0;
	}
	
	// Capability
	
	IItemHandler handler = new InvWrapper(this);
	
	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
		return super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) handler;
		return super.getCapability(capability, facing);
	}
}
