package slimeknights.tconstruct.tables.tileentity.table.tinkerstation;

import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.util.NonNullList;
import net.minecraft.world.GameRules;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.hooks.BasicEventHooks;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.SoundUtils;
import slimeknights.tconstruct.common.Sounds;
import slimeknights.tconstruct.library.network.TinkerNetwork;
import slimeknights.tconstruct.library.recipe.RecipeTypes;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.shared.inventory.ConfigurableInvWrapperCapability;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.inventory.table.tinkerstation.TinkerStationContainer;
import slimeknights.tconstruct.tables.network.UpdateCraftingRecipePacket;
import slimeknights.tconstruct.tables.network.UpdateTinkerStationRecipePacket;
import slimeknights.tconstruct.tables.tileentity.crafting.CraftingInventoryWrapper;
import slimeknights.tconstruct.tables.tileentity.crafting.LazyResultInventory;
import slimeknights.tconstruct.tables.tileentity.table.RetexturedTableTileEntity;

import javax.annotation.Nullable;
import java.util.Collections;

public class TinkerStationTileEntity extends RetexturedTableTileEntity implements LazyResultInventory.ILazyCrafter {

  public static final int TINKER_SLOT = 5;
  public static final int OUTPUT_SLOT = 0;

  /** Last crafted crafting recipe */
  @Nullable
  private ITinkerStationRecipe lastRecipe;
  /** Result inventory, lazy loads results */
  @Getter
  private final LazyResultInventory craftingResult;
  /** Crafting inventory for the recipe calls */
  private final TinkerStationInventoryWrapper inventoryWrapper;

  public TinkerStationTileEntity() {
    super(TinkerTables.tinkerStationTile.get(), "gui.tconstruct.tinker_station", 6);
    this.itemHandler = new ConfigurableInvWrapperCapability(this, false, false);
    this.itemHandlerCap = LazyOptional.of(() -> this.itemHandler);
    this.inventoryWrapper = new TinkerStationInventoryWrapper(this);
    this.craftingResult = new LazyResultInventory(this);
  }

  @Nullable
  @Override
  public Container createMenu(int menuId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
    return new TinkerStationContainer(menuId, playerInventory, this);
  }

  /* Crafting */

  @Override
  public ItemStack calcResult() {
    if (this.world == null) {
      return ItemStack.EMPTY;
    }

    // assume empty unless we learn otherwise
    ItemStack result = ItemStack.EMPTY;

    if (!this.world.isRemote && this.world.getServer() != null) {
      RecipeManager manager = this.world.getServer().getRecipeManager();

      // first, try the cached recipe
      ITinkerStationRecipe recipe = lastRecipe;
      // if it does not match, find a new recipe
      if (recipe == null || !recipe.matches(this.inventoryWrapper, this.world)) {
        recipe = manager.getRecipe(RecipeTypes.TINKER_STATION, this.inventoryWrapper, this.world).orElse(null);
      }

      // if we have a recipe, fetch its result
      if (recipe != null) {
        result = recipe.getCraftingResult(this.inventoryWrapper);
        // sync if the recipe is different
        if (recipe != this.lastRecipe) {
          this.lastRecipe = recipe;
          this.syncToRelevantPlayers();
        }
      }
    }
    else if (this.lastRecipe != null && this.lastRecipe.matches(this.inventoryWrapper, world)) {
      result = this.lastRecipe.getCraftingResult(this.inventoryWrapper);
    }

    return result;
  }

  @Override
  public ItemStack onCraft(PlayerEntity player, ItemStack result, int amount) {
    if (this.world == null || amount == 0 || this.lastRecipe == null || !this.lastRecipe.matches(this.inventoryWrapper, world)) {
      return ItemStack.EMPTY;
    }

    // check if the player has access to the result
    if (player instanceof ServerPlayerEntity) {
      if (this.lastRecipe != null) {
        // if the player cannot craft this, block crafting
        if (!this.lastRecipe.isDynamic() && world.getGameRules().getBoolean(GameRules.DO_LIMITED_CRAFTING) && !((ServerPlayerEntity) player).getRecipeBook().isUnlocked(this.lastRecipe)) {
          return ItemStack.EMPTY;
        }
        // unlock the recipe if it was not unlocked
        if (this.lastRecipe != null && !this.lastRecipe.isDynamic()) {
          player.unlockRecipes(Collections.singleton(this.lastRecipe));
        }
      }

      // fire crafting events
      result.onCrafting(this.world, player, amount);
      BasicEventHooks.firePlayerCraftingEvent(player, result, this.inventoryWrapper);
    }

    this.playCraftSound(player);

    // update all slots in the inventory
    // remove remaining items
    NonNullList<ItemStack> remaining = this.lastRecipe.getRemainingItems(this.inventoryWrapper);

    for (int i = 0; i < remaining.size(); ++i) {
      ItemStack original = getStackInSlot(i);
      ItemStack newStack = remaining.get(i);

      // if the slot contains a stack, decrease by 1
      if (!original.isEmpty()) {
        original.shrink(1);
      }

      // if we have a new item, try merging it in
      if (!newStack.isEmpty()) {
        // if empty, set directly
        if (original.isEmpty()) {
          this.setInventorySlotContents(i, newStack);
        }
        else if (ItemStack.areItemsEqual(original, newStack) && ItemStack.areItemStackTagsEqual(original, newStack)) {
          // if matching, merge
          newStack.grow(original.getCount());
          this.setInventorySlotContents(i, newStack);
        }
        else {
          // otherwise, drop the item as the player
          if (!player.inventory.addItemStackToInventory(newStack)) {
            player.dropItem(newStack, false);
          }
        }
      }
    }

    return result;
  }

  @Override
  public void setInventorySlotContents(int slot, ItemStack itemstack) {
    super.setInventorySlotContents(slot, itemstack);
    // clear the crafting result when the matrix changes so we recalculate the result
    this.craftingResult.clear();
    this.inventoryWrapper.clearInputs();
  }

  /* Syncing */

  /**
   * Sends a packet to all players with this container open
   */
  private void syncToRelevantPlayers() {
    if (this.world == null || this.world.isRemote) {
      return;
    }

    this.world.getPlayers().stream()
      // sync if they are viewing this tile
      .filter(player -> {
        if (player.openContainer instanceof TinkerStationContainer) {
          return ((TinkerStationContainer) player.openContainer).getTile() == this;
        }
        return false;
      })
      // send packets
      .forEach(this::syncRecipe);
  }

  /**
   * Sends the current recipe to the given player
   * @param player  Player to send an update to
   */
  public void syncRecipe(PlayerEntity player) {
    // must have a last recipe and a server world
    if (this.lastRecipe != null && this.world != null && !this.world.isRemote && player instanceof ServerPlayerEntity) {
      TinkerNetwork.getInstance().sendTo(new UpdateTinkerStationRecipePacket(this.pos, this.lastRecipe), (ServerPlayerEntity) player);
    }
  }

  /**
   * Updates the recipe from the server
   * @param recipe  New recipe
   */
  public void updateRecipe(ITinkerStationRecipe recipe) {
    this.lastRecipe = recipe;
    this.craftingResult.clear();
  }

  protected void playCraftSound(PlayerEntity player) {
    SoundUtils.playSoundForAll(player, Sounds.SAW.getSound(), 0.8f, 0.8f + 0.4f * TConstruct.random.nextFloat());
  }
}