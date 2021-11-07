package slimeknights.tconstruct.tools.data.sprite;

import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.data.material.AbstractPartSpriteProvider;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;

/**
 * This class handles all tool part sprites generated by Tinkers' Construct. You can freely use this in your addon to generate TiC part textures for a new material
 * Do not use both this and {@link TinkerMaterialSpriteProvider} in a single generator for an addon, if you need to use both make two instances of {@link slimeknights.tconstruct.library.client.data.material.MaterialPartTextureGenerator}
 */
public class TinkerPartSpriteProvider extends AbstractPartSpriteProvider {
  /** Internal use stats ID to generate a repair kit texture, since many stat types could trigger a repair kit, including addon ones */
  public static final MaterialStatsId REPAIR_KIT = new MaterialStatsId(TConstruct.MOD_ID, "repair_kit");

  public TinkerPartSpriteProvider() {
    super(TConstruct.MOD_ID);
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Parts";
  }

  @Override
  protected void addAllSpites() {
    // heads
    addHead("large_plate");
    addHead("small_blade");
    // handles
    addHandle("tool_handle");
    addHandle("tough_handle");
    // misc
    addBinding("tool_binding");
    addPart("repair_kit", REPAIR_KIT);

    // tools
    // pickaxe
    buildTool("pickaxe").addBreakableHead("head").addHandle("handle").addBinding("binding");
    buildTool("sledge_hammer").withLarge().addBreakableHead("head").addBreakableHead("back").addBreakableHead("front").addHandle("handle");
    buildTool("vein_hammer").withLarge().addBreakableHead("head").addHead("back").addBreakableHead("front").addHandle("handle");
    // shovel
    buildTool("mattock").addBreakableHead("axe").addBreakableHead("pick"); // handle provided by pickaxe
    buildTool("excavator").withLarge().addBreakableHead("head").addHead("binding").addHandle("handle").addHandle("grip");
    // axe
    buildTool("hand_axe").addBreakableHead("head").addBinding("binding"); // handle provided by pickaxe
    buildTool("broad_axe").withLarge().addBreakableHead("blade").addBreakableHead("back").addHandle("handle").addBinding("binding");
    // scythe
    buildTool("kama").addBreakableHead("head").addBinding("binding"); // handle provided by pickaxe
    buildTool("scythe").withLarge().addBreakableHead("head").addHandle("handle").addHandle("accessory").addBinding("binding");
    // sword
    buildTool("dagger").addBreakableHead("blade").addHandle("crossguard");
    buildTool("sword").addBreakableHead("blade").addHandle("guard").addHandle("handle");
    buildTool("cleaver").withLarge().addBreakableHead("head").addBreakableHead("shield").addHandle("handle").addHandle("guard");
  }
}
