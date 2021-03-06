package nc.integration.jei;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import nc.Global;
import nc.recipe.ingredient.ChanceFluidIngredient;
import nc.recipe.ingredient.ChanceItemIngredient;
import nc.util.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

public abstract class JEICategoryAbstract<WRAPPER extends JEIProcessorRecipeWrapper> extends JEICategory<WRAPPER> {
	
	private final IDrawable background;
	protected String recipeTitle;
	protected final int backPosX, backPosY;
	
	public JEICategoryAbstract(IGuiHelper guiHelper, IJEIHandler handler, String title, int backX, int backY, int backWidth, int backHeight) {
		this(guiHelper, handler, title, "", backX, backY, backWidth, backHeight);
	}
	
	public JEICategoryAbstract(IGuiHelper guiHelper, IJEIHandler handler, String title, String guiExtra, int backX, int backY, int backWidth, int backHeight) {
		super(handler);
		ResourceLocation location = new ResourceLocation(Global.MOD_ID + ":textures/gui/container/" + handler.getTextureName() + guiExtra + ".png");
		background = guiHelper.createDrawable(location, backX, backY, backWidth, backHeight);
		recipeTitle = Lang.localise("tile." + Global.MOD_ID + "." + title + ".name");
		backPosX = backX + 1;
		backPosY = backY + 1;
	}
	
	@Override
	public void drawExtras(Minecraft minecraft) {
		
	}
	
	@Override
	public IDrawable getBackground() {
		return background;
	}
	
	@Override
	public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
		recipeLayout.getItemStacks().addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
			int outputIndex = slotIndex - recipeWrapper.recipeHandler.itemInputSize;
			if (outputIndex >= 0 && outputIndex <= recipeWrapper.recipeHandler.itemOutputSize && recipeWrapper.recipe.itemProducts().get(outputIndex) instanceof ChanceItemIngredient) {
				ChanceItemIngredient chanceIngredient = (ChanceItemIngredient)recipeWrapper.recipe.itemProducts().get(outputIndex);
				tooltip.add(TextFormatting.WHITE + Lang.localise("jei.nuclearcraft.chance_output", chanceIngredient.minStackSize, chanceIngredient.getMaxStackSize(), chanceIngredient.meanStackSize));
			}
		});
		
		recipeLayout.getFluidStacks().addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
			int outputIndex = slotIndex - recipeWrapper.recipeHandler.fluidInputSize;
			if (outputIndex >= 0 && outputIndex <= recipeWrapper.recipeHandler.fluidOutputSize && recipeWrapper.recipe.fluidProducts().get(outputIndex) instanceof ChanceFluidIngredient) {
				ChanceFluidIngredient chanceIngredient = (ChanceFluidIngredient)recipeWrapper.recipe.fluidProducts().get(outputIndex);
				tooltip.add(TextFormatting.WHITE + Lang.localise("jei.nuclearcraft.chance_output", chanceIngredient.minStackSize, chanceIngredient.getMaxStackSize(), chanceIngredient.meanStackSize));
			}
		});
	}
	
	@Override
	public String getTitle() {
		return recipeTitle;
	}
}
