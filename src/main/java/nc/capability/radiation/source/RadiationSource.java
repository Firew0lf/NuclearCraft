package nc.capability.radiation.source;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public class RadiationSource implements IRadiationSource {
	
	private double radiationLevel = 0D, radiationBuffer = 0D;
	private boolean scrubbing = false;
	
	public RadiationSource(double startRadiation) {
		this.radiationLevel = startRadiation;
	}
	
	@Override
	public NBTTagCompound writeNBT(IRadiationSource instance, EnumFacing side, NBTTagCompound nbt) {
		nbt.setDouble("radiationLevel", getRadiationLevel());
		nbt.setDouble("radiationBuffer", getRadiationBuffer());
		return nbt;
	}
	
	@Override
	public void readNBT(IRadiationSource instance, EnumFacing side, NBTTagCompound nbt) {
		setRadiationLevel(nbt.getDouble("radiationLevel"));
		setRadiationBuffer(nbt.getDouble("radiationBuffer"));
	}
	
	@Override
	public double getRadiationLevel() {
		return radiationLevel;
	}
	
	@Override
	public void setRadiationLevel(double newRads) {
		radiationLevel = Math.max(newRads, 0D);
	}
	
	@Override
	public double getRadiationBuffer() {
		return radiationBuffer;
	}
	
	@Override
	public void setRadiationBuffer(double newBuffer) {
		radiationBuffer = newBuffer;
	}
	
	@Override
	public boolean getScrubbing() {
		return scrubbing;
	}
	
	@Override
	public void setScrubbing(boolean scrubbing) {
		this.scrubbing = scrubbing;
	}
}
