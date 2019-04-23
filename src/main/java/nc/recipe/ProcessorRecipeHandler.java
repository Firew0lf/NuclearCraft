package nc.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import nc.Global;
import nc.ModCheck;
import nc.recipe.ingredient.IFluidIngredient;
import nc.recipe.ingredient.IItemIngredient;
import nc.util.NCUtil;
import nc.util.OreDictHelper;
import nc.util.RecipeHelper;
import net.minecraft.item.ItemStack;

public abstract class ProcessorRecipeHandler extends AbstractRecipeHandler<ProcessorRecipe> {
	
	public int itemInputSize, fluidInputSize, itemOutputSize, fluidOutputSize;
	public final boolean shapeless;
	private final String recipeName;
	
	public ProcessorRecipeHandler(String recipeName, int itemInputSize, int fluidInputSize, int itemOutputSize, int fluidOutputSize) {
		this(recipeName, itemInputSize, fluidInputSize, itemOutputSize, fluidOutputSize, true);
	}
	
	public ProcessorRecipeHandler(String recipeName, int itemInputSize, int fluidInputSize, int itemOutputSize, int fluidOutputSize, boolean shapeless) {
		this.itemInputSize = itemInputSize;
		this.fluidInputSize = fluidInputSize;
		this.itemOutputSize = itemOutputSize;
		this.fluidOutputSize = fluidOutputSize;
		this.shapeless = shapeless;
		this.recipeName = recipeName;
		addRecipes();
	}
	
	@Override
	public void addRecipe(Object... objects) {
		List itemInputs = new ArrayList(), fluidInputs = new ArrayList(), itemOutputs = new ArrayList(), fluidOutputs = new ArrayList(), extras = new ArrayList();
		for (int i = 0; i < objects.length; i++) {
			Object object = objects[i];
			if (i < itemInputSize) {
				itemInputs.add(object);
			} else if (i < itemInputSize + fluidInputSize) {
				fluidInputs.add(object);
			} else if (i < itemInputSize + fluidInputSize + itemOutputSize) {
				itemOutputs.add(object);
			} else if (i < itemInputSize + fluidInputSize + itemOutputSize + fluidOutputSize) {
				fluidOutputs.add(object);
			} else {
				extras.add(object);
			}
		}
		
		ProcessorRecipe recipe = buildRecipe(itemInputs, fluidInputs, itemOutputs, fluidOutputs, extras, shapeless);
		addRecipe(recipe);
		

		if (!ModCheck.gregtechLoaded() || recipe == null) return;
		if (recipe.extras == null || recipe.extras.size() != 2) return;
		
		SimpleRecipeBuilder builder = null;
		
		
		switch (this.recipeName) {
		case "alloy_furnace":
			builder = RecipeMaps.ALLOY_SMELTER_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "isotope_separator":
			builder = RecipeMaps.THERMAL_CENTRIFUGE_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "fuel_reprocessor":
			builder = RecipeMaps.CENTRIFUGE_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "supercooler":
			builder = RecipeMaps.VACUUM_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "electrolyser":
			builder = RecipeMaps.CENTRIFUGE_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "infuser":
			builder = RecipeMaps.FLUID_CANNER_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "melter":
			builder = RecipeMaps.FLUID_EXTRACTION_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "pressurizer":
			builder = RecipeMaps.COMPRESSOR_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "chemical_reactor":
			builder = RecipeMaps.CHEMICAL_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "salt_mixer":
			builder = RecipeMaps.MIXER_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "dissolver":
			builder = RecipeMaps.CHEMICAL_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "extractor":
			builder = RecipeMaps.FLUID_EXTRACTION_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "centrifuge":
			builder = RecipeMaps.CENTRIFUGE_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "rock_crusher":
			builder = RecipeMaps.MACERATOR_RECIPES.recipeBuilder().EUt((int)(30*(double)recipe.extras.get(0))).duration((int)(20*(double)recipe.extras.get(1)));
			break;
		case "ingot_former":
			break; // TODO
		}
		
		if (builder == null) return;
		NCUtil.getLogger().info("Adding GT recipe: " + this.recipeName + " " + recipe.itemIngredients.toString() + "=> " + recipe.itemProducts);
		
		List<SimpleRecipeBuilder> builders = new ArrayList<SimpleRecipeBuilder>(); // Holds all the recipe variants
		builders.add(builder);
		
		if (recipe.itemIngredients.size() > 0) {
			for (IItemIngredient input : recipe.itemIngredients) {
				List<ItemStack> inputStackList = input.getInputStackList();
				String inputOreDictName = OreDictHelper.getOreNameFromStacks(input.getInputStackList()); // search a common oreDict name
				if (inputOreDictName != null && !inputOreDictName.equals("Unknown")) { // got one !
					for (SimpleRecipeBuilder builderVariant : builders) {
						builderVariant.input(inputOreDictName, input.getStack().getCount());
					}
				} else { // didn't find one, let's search one for every ingredient.
					List<String> ingredientOreList = new ArrayList<String>(); // hold the different oreDict names
					List<SimpleRecipeBuilder> newBuilders = new ArrayList<SimpleRecipeBuilder>();
					for (ItemStack inputVariant : inputStackList) {
						List<String> variantOreList = OreDictHelper.getOreNames(inputVariant);
						
						if (variantOreList.size() > 0) { // This variant has oredict
							if (ingredientOreList.containsAll(variantOreList)) continue;
							ingredientOreList.addAll(variantOreList);
							
							for (SimpleRecipeBuilder recipeBuilder : builders) {
								newBuilders.add(recipeBuilder.copy().input(variantOreList.get(0), inputVariant.getCount()));
							}
						} else {
							for (SimpleRecipeBuilder recipeBuilder : builders) {
								newBuilders.add(recipeBuilder.copy().inputs(inputVariant));
							}
						}
					}
					
					builders = newBuilders;
				}
			}
		}
		
		if (recipe.fluidIngredients.size() > 0) {
			for (IFluidIngredient input : recipe.fluidIngredients) {
				if (input.getInputStackList().size() < 1) continue;
				for (SimpleRecipeBuilder builderVariant : builders) {
					builderVariant.fluidInputs(input.getInputStackList().get(0));
				}
			}
		}
		
		if (recipe.itemProducts.size() > 0) {
			for (IItemIngredient output : recipe.itemProducts) {
				List<ItemStack> outputStackList = output.getOutputStackList();
				if (outputStackList.size() < 1) continue;
				for (SimpleRecipeBuilder builderVariant : builders) {
					builderVariant = builderVariant.outputs(outputStackList.get(0));
				}
			}
		}
		
		if (recipe.fluidProducts.size() > 0) {
			for (IFluidIngredient output : recipe.fluidProducts) {
				if (output.getOutputStackList().size() < 1) continue;
				for (SimpleRecipeBuilder builderVariant : builders) {
					builderVariant.fluidOutputs(output.getOutputStackList().get(0));
				}
			}
		}
		
		for (SimpleRecipeBuilder builderVariant : builders) {
			builderVariant.buildAndRegister();
		}
	}
	
	public void addRecipe(List itemInputList, List fluidInputList, List itemOutputList, List fluidOutputList, List extrasList, boolean shapeless) {
		addRecipe(buildRecipe(itemInputList, fluidInputList, itemOutputList, fluidOutputList, extrasList, shapeless));
	}
	
	@Nullable
	public ProcessorRecipe buildRecipe(List itemInputs, List fluidInputs, List itemOutputs, List fluidOutputs, List extras, boolean shapeless) {
		List<IItemIngredient> itemIngredients = new ArrayList<IItemIngredient>(), itemProducts = new ArrayList<IItemIngredient>();
		List<IFluidIngredient> fluidIngredients = new ArrayList<IFluidIngredient>(), fluidProducts = new ArrayList<IFluidIngredient>();
		for (Object obj : itemInputs) {
			if (obj != null && isValidItemInputType(obj)) {
				IItemIngredient input = buildItemIngredient(obj);
				if (input == null) return null;
				itemIngredients.add(input);
			} else return null;
		}
		for (Object obj : fluidInputs) {
			if (obj != null && isValidFluidInputType(obj)) {
				IFluidIngredient input = buildFluidIngredient(obj);
				if (input == null) return null;
				fluidIngredients.add(input);
			} else return null;
		}
		for (Object obj : itemOutputs) {
			if (obj != null && isValidItemOutputType(obj)) {
				IItemIngredient output = buildItemIngredient(obj);
				if (output == null) return null;
				itemProducts.add(output);
			} else return null;
		}
		for (Object obj : fluidOutputs) {
			if (obj != null && isValidFluidOutputType(obj)) {
				IFluidIngredient output = buildFluidIngredient(obj);
				if (output == null) return null;
				fluidProducts.add(output);
			} else return null;
		}
		if (!isValidRecipe(itemIngredients, fluidIngredients, itemProducts, fluidProducts)) {
			NCUtil.getLogger().info(getRecipeName() + " - a recipe was removed: " + RecipeHelper.getRecipeString(itemIngredients, fluidIngredients, itemProducts, fluidProducts));
		}
		return new ProcessorRecipe(itemIngredients, fluidIngredients, itemProducts, fluidProducts, extras, shapeless);
	}
	
	public boolean isValidRecipe(List<IItemIngredient> itemIngredients, List<IFluidIngredient> fluidIngredients, List<IItemIngredient> itemProducts, List<IFluidIngredient> fluidProducts) {
		return itemIngredients.size() == itemInputSize && fluidIngredients.size() == fluidInputSize && itemProducts.size() == itemOutputSize && fluidProducts.size() == fluidOutputSize;
	}
	
	@Override
	public String getRecipeName() {
		return Global.MOD_ID + "_" + recipeName;
	}
}
