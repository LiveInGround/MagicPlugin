package com.elmakers.mine.bukkit.magic.listener;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.api.action.GUIAction;
import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.magic.Mage;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.tasks.ActivateIconTask;
import com.elmakers.mine.bukkit.tasks.WandCastTask;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.CompleteDragTask;
import com.elmakers.mine.bukkit.utility.CurrencyAmount;
import com.elmakers.mine.bukkit.utility.SafetyUtils;
import com.elmakers.mine.bukkit.utility.StringUtils;
import com.elmakers.mine.bukkit.wand.Wand;
import com.elmakers.mine.bukkit.wand.WandInventory;
import com.elmakers.mine.bukkit.wand.WandMode;

public class InventoryController implements Listener {
    private static final int DEBUG_LEVEL = 80;

    private final MagicController controller;
    private boolean enableItemHacks = true;
    private boolean dropChangesPages = false;
    private long openCooldown = 0;
    private boolean enableInventoryCasting = true;
    private boolean enableInventoryDropCasting = false;
    private boolean enableInventorySelection = true;
    private Set<InventoryType> unstashableTypes = new HashSet<>();

    // offhand, helmet, chestplate, leggings, boots
    private static final Integer[] armorSlotList = new Integer[] {39,38,37,36};
    private static final Set<Integer> armorSlots = new HashSet<>(Arrays.asList(armorSlotList));
    private static final int OFFHAND_SLOT = 40;

    public InventoryController(MagicController controller) {
        this.controller = controller;
    }

    public void loadProperties(ConfigurationSection properties) {
        enableItemHacks = properties.getBoolean("enable_custom_item_hacks", false);
        dropChangesPages = properties.getBoolean("drop_changes_pages", false);
        enableInventoryCasting = properties.getBoolean("allow_inventory_casting", true);
        enableInventoryDropCasting = properties.getBoolean("allow_inventory_drop_casting", false);
        enableInventorySelection = properties.getBoolean("allow_inventory_selection", true);
        openCooldown = properties.getInt("open_cooldown", 0);
        List<String> unstashableTypeKeys = properties.getStringList("unstashable_containers");
        unstashableTypes.clear();
        if (unstashableTypeKeys != null) {
            for (String typeKey : unstashableTypeKeys) {
                try {
                    InventoryType type = InventoryType.valueOf(typeKey.toUpperCase());
                    unstashableTypes.add(type);
                } catch (Exception ex) {
                    controller.getLogger().info("Unknown container type in unstashable_containers list: " + typeKey);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        HumanEntity clicked = event.getWhoClicked();
        Mage mage = controller.getRegisteredMage(clicked);
        if (mage == null) return;
        if (mage.getDebugLevel() >= DEBUG_LEVEL) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            mage.sendDebugMessage("CREATIVE: " + event.getAction()  + " of " + event.getClick() + " cursor: "
                + (cursor == null ? "(Nothing)" : cursor.getType().name())
                + (current == null ? "(Current" : current.getType().name())
                );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity clicked = event.getWhoClicked();
        Mage mage = controller.getMage(clicked);
        if (mage.getDebugLevel() >= DEBUG_LEVEL) {
            mage.sendDebugMessage("DRAG: " + event.getType()  + " in " + event.getInventory().getType() + " slots: " + StringUtils.join(event.getInventorySlots(), ",") +  " raw: " + StringUtils.join(event.getRawSlots(), ","));
        }
        GUIAction activeGUI = mage.getActiveGUI();
        if (activeGUI != null) {
            activeGUI.dragged(event);
            return;
        }
        // Not sure this actually happens in a way we care about, the drag event only seems to fire
        // on grindstones for inventory interaction, but just in case.
        if (event.getInventory().getType().name().equals("GRINDSTONE")) {
            ItemStack oldCursor = event.getOldCursor();
            oldCursor = oldCursor.hasItemMeta() ? CompatibilityLib.getItemUtils().makeReal(oldCursor) : oldCursor;
            if (Wand.isSpecial(oldCursor)) {
                for (int slot : event.getRawSlots()) {
                    if (slot == 1 || slot == 0) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        Wand activeWand = mage.getActiveWand();
        boolean isSpellInventory = activeWand != null && activeWand.isInventoryOpen() && activeWand.getMode() == WandMode.INVENTORY;
        if (isSpellInventory && event.getRawSlots().contains(45)) {
            event.setCancelled(true);
            return;
        }

        boolean isSkillInventory = activeWand != null && activeWand.isInventoryOpen() && activeWand.getMode() == WandMode.SKILLS;
        if (isSkillInventory) {
            // Unfortunately this event gives us a shallow copy of the item so we need to dig a little bit.
            ItemStack oldCursor = event.getOldCursor();
            oldCursor = oldCursor.hasItemMeta() ? CompatibilityLib.getItemUtils().makeReal(oldCursor) : oldCursor;
            boolean isSpell = Wand.isSpell(oldCursor);
            isSpellInventory = false;
            Set<Integer> slots = event.getRawSlots();
            int spellInventoryStart = event.getInventory().getSize();
            for (int slot : slots) {
                if (slot < spellInventoryStart) {
                    isSpellInventory = true;
                }
            }
            if (!isSpell && isSpellInventory) {
                event.setCancelled(true);
            }
            return;
        }

        // Only check if clicking an armor slot or the held item slot
        Set<Integer> slots = event.getInventorySlots();
        boolean isArmorSlot = !Collections.disjoint(slots, armorSlots);
        boolean isOffhandSlot = slots.contains(OFFHAND_SLOT);
        boolean isHeldSlot = slots.contains(clicked.getInventory().getHeldItemSlot());
        if (isArmorSlot || isOffhandSlot || isHeldSlot) {
            // This is intentionally copied from above to avoid doing it if we don't need to
            ItemStack oldCursor = event.getOldCursor();
            oldCursor = oldCursor.hasItemMeta() ? CompatibilityLib.getItemUtils().makeReal(oldCursor) : oldCursor;

            // Prevent wearing spells
            if (isArmorSlot && Wand.isSpell(oldCursor)) {
                event.setCancelled(true);
                return;
            }

            // Update armor if moving magic items around
            if (Wand.isWand(oldCursor)) {
                // Check to see if this wand is wearable in the target slot
                if (isArmorSlot) {
                    Wand wand = controller.getWand(oldCursor);
                    if (wand.hasWearable()) {
                        for (int slot : slots) {
                            if (!wand.isWearableInSlot(slot)) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
                controller.onArmorUpdated(mage);
            }
        }

        if (!enableItemHacks) return;

        // this is a huge hack! :\
        // I apologize for any weird behavior this causes.
        // Bukkit, unfortunately, will blow away NBT data for anything you drag
        // Which will nuke a wand or spell.
        // To make matters worse, Bukkit passes a copy of the item in the event, so we can't
        // even check for metadata and only cancel the event if it involves one of our special items.
        // The best I can do is look for metadata at all, since Bukkit will retain the name and lore.

        // I have now decided to copy over the CB default handler for this, and cancel the event.
        // The only change I have made is that *real* ItemStack copies are made, instead of shallow Bukkit ones.
        ItemStack oldStack = event.getOldCursor();
        HumanEntity entity = event.getWhoClicked();
        if (oldStack != null && oldStack.hasItemMeta() && entity instanceof Player) {
            // Only do this if we're only dragging one item, since I don't
            // really know what happens or how you would drag more than one.
            Map<Integer, ItemStack> draggedSlots = event.getNewItems();
            if (draggedSlots.size() != 1) return;

            event.setCancelled(true);

            // Cancelling the event will put the item back on the cursor,
            // and skip updating the inventory.

            // So we will wait one tick and then fix this up using the original item.
            InventoryView view = event.getView();
            for (Integer dslot : draggedSlots.keySet()) {
                CompleteDragTask completeDrag = new CompleteDragTask((Player)entity, view, dslot);
                completeDrag.runTaskLater(controller.getPlugin(), 1);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player)event.getWhoClicked();
        final Mage mage = controller.getMage(player);
        InventoryType.SlotType slotType = event.getSlotType();
        Integer slot = event.getSlot();

        if (mage.getDebugLevel() >= DEBUG_LEVEL) {
            mage.sendDebugMessage("CLICK: " + event.getAction() + ", " + event.getClick() + " on " + slotType + " in " + event.getInventory().getType() + " slots: " + slot + ":" + event.getRawSlot());
        }

        GUIAction gui = mage.getActiveGUI();
        if (gui != null)
        {
            gui.clicked(event);
            return;
        }

        // Check for temporary items and skill items
        InventoryAction action = event.getAction();
        InventoryType inventoryType = event.getInventory().getType();
        boolean isPlayerInventory = inventoryType == InventoryType.CRAFTING || inventoryType == InventoryType.PLAYER;
        ItemStack clickedItem = event.getCurrentItem();

        // TODO: Use the enum
        boolean isSwap = event.getClick().name().equals("SWAP_OFFHAND");
        boolean isDrop = event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP;
        boolean isSkill = clickedItem != null && Wand.isSkill(clickedItem);

        // Check for right-click-to-use
        boolean isRightClick = action == InventoryAction.PICKUP_HALF;
        boolean isLeftClick = !isDrop && !isRightClick;
        if (isSkill && isRightClick)
        {
            mage.useSkill(clickedItem);
            player.closeInventory();
            event.setCancelled(true);
            return;
        }

        if (clickedItem != null && CompatibilityLib.getItemUtils().isTemporary(clickedItem)) {
            String message = CompatibilityLib.getItemUtils().getTemporaryMessage(clickedItem);
            if (message != null && message.length() > 1) {
                mage.sendMessage(message);
            }
            ItemStack replacement = CompatibilityLib.getItemUtils().getReplacement(clickedItem);
            event.setCurrentItem(replacement);
            event.setCancelled(true);
            mage.armorUpdated();
            return;
        }
        if (CompatibilityLib.getNBTUtils().getBoolean(clickedItem, "unmoveable", false)) {
            event.setCancelled(true);
            return;
        }

        // Look at the wand in hand
        ItemStack heldItem = event.getCursor();
        boolean heldWand = Wand.isWand(heldItem);
        boolean clickedWand = Wand.isWand(clickedItem);
        boolean isCraftableWand = clickedWand && CompatibilityLib.getNBTUtils().getBoolean(clickedItem, "craftable", false);;

        boolean isHotbar = action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD;
        // Pressing the swap hands button also looks like a hotbar_swap ... for some reason .. I guess the offhand is considered part of the hotbar?
        if (isSwap) {
            isHotbar = false;
        }

        // I'm not sure why or how this happens, but sometimes we can get a hotbar event without a slot number?
        int hotbarButton = event.getHotbarButton();
        if (isHotbar && hotbarButton < 0) return;

        // Check for putting wands in a grindstone since they will re-apply their enchantments
        boolean isGrindstone = inventoryType.name().equals("GRINDSTONE");
        if (isGrindstone && heldWand && (slot == 1 || slot == 0) && slotType == InventoryType.SlotType.CRAFTING) {
            event.setCancelled(true);
            return;
        }
        if (isGrindstone && clickedWand && action == InventoryAction.MOVE_TO_OTHER_INVENTORY && slotType != InventoryType.SlotType.CRAFTING) {
            event.setCancelled(true);
            return;
        }
        if (isGrindstone && isHotbar) {
            ItemStack item =  mage.getPlayer().getInventory().getItem(hotbarButton);
            if (Wand.isWand(item)) {
                event.setCancelled(true);
                return;
            }
        }

        // Check for wearing spells or wands
        boolean heldSpell = Wand.isSpell(heldItem);
        if (slotType == InventoryType.SlotType.ARMOR)
        {
            if (heldSpell) {
                event.setCancelled(true);
                return;
            }

            if (heldWand) {
                Wand wand = controller.getWand(heldItem);
                if (wand.hasWearable()) {
                    if (wand.isWearableInSlot(slot)) {
                        ItemStack existing = player.getInventory().getItem(slot);
                        player.getInventory().setItem(slot, heldItem);
                        event.setCursor(existing);
                        event.setCancelled(true);
                        controller.onArmorUpdated(mage);
                        return;
                    } else {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // Notify mage that armor was updated, but wait one tick to do it
            controller.onArmorUpdated(mage);
        }

        boolean tryingToWear = action == InventoryAction.MOVE_TO_OTHER_INVENTORY && (inventoryType == InventoryType.PLAYER || inventoryType == InventoryType.CRAFTING) && (slotType == InventoryType.SlotType.CONTAINER || slotType == InventoryType.SlotType.QUICKBAR);
        if (clickedWand && tryingToWear) {
            Wand wand = null;
            if (slot == player.getInventory().getHeldItemSlot()) {
                wand = mage.getActiveWand();
            }
            if (wand == null) {
                wand = controller.getWand(clickedItem);
            }
            if (wand.tryToWear(mage)) {
                player.getInventory().setItem(slot, null);
                event.setCancelled(true);
                mage.checkWand();
                return;
            }
        }

        // Another check for wearing spells
        boolean clickedSpell = Wand.isSpell(clickedItem);
        boolean clickedBrush = Wand.isBrush(clickedItem);
        boolean clickedWearable = controller.isWearable(clickedItem);
        if (tryingToWear && clickedSpell && clickedWearable)
        {
            event.setCancelled(true);
            return;
        }

        if (isHotbar && slotType == InventoryType.SlotType.ARMOR)
        {
            ItemStack item =  mage.getPlayer().getInventory().getItem(hotbarButton);
            if (item != null && Wand.isSpell(item))
            {
                event.setCancelled(true);
                return;
            }
            controller.onArmorUpdated(mage);
        }

        Wand activeWand = mage.getActiveWand();
        boolean isWandInventoryOpen = activeWand != null && activeWand.isInventoryOpen();

        // Check to see if we just grabbed the active wand, we'll need to deactivate it so it saves
        if (activeWand != null && !isWandInventoryOpen && clickedWand && controller.isSameItem(clickedItem, activeWand.getItem())) {
            activeWand.deactivate();
            return;
        }

        // Preventing putting skills in containers
        if (isSkill && !isPlayerInventory && !isWandInventoryOpen) {
            if (!isDrop) {
                event.setCancelled(true);
            }
            return;
        }

        // Get rid of glitched spells and brushes
        if (!isWandInventoryOpen && !isSkill && (clickedBrush || clickedSpell) && !Wand.isAbsorbable(clickedItem)) {
            event.setCurrentItem(new ItemStack(Material.AIR));
            event.setCancelled(true);
            return;
        }

        boolean isFurnace = inventoryType == InventoryType.FURNACE;
        boolean isContainer = unstashableTypes.contains(inventoryType);
        boolean isEnderChest = inventoryType == InventoryType.ENDER_CHEST;
        boolean isChest = inventoryType == InventoryType.CHEST;
        boolean isContainerSlot = isChest && event.getSlot() == event.getRawSlot();
        boolean isWatchedSlot = !isChest || isContainerSlot;

        if (isWandInventoryOpen)
        {
            if (clickedSpell && clickedItem.getAmount() != 1) {
                clickedItem.setAmount(1);
            }

            // Check for swapping into the offhand, we have to prevent this in the wand inventory
            if (isSwap) {
                String spellName = Wand.getSpell(clickedItem);
                if (spellName != null && !activeWand.isManualQuickCastDisabled() && enableInventoryCasting) {
                    final Spell spell = mage.getSpell(spellName);
                    if (spell != null) {
                        // Delay this one tick because cancelling this event is going to mean the spell icon
                        // gets put back in the player inventory, and this can cause strange side effects with
                        // spells levelling up, or spells with GUIs or other inventories.
                        final Wand castWand = activeWand;
                        controller.getPlugin().getServer().getScheduler().runTaskLater(controller.getPlugin(),
                                new WandCastTask(castWand, spell), 1);
                    }
                }
                event.setCancelled(true);
            }

            // Check for page/hotbar cycling by clicking the active wand
            if (clickedWand) {
                event.setCancelled(true);
                if ((dropChangesPages && isDrop) || isLeftClick) {
                    activeWand.cycleInventory();
                } else {
                    activeWand.cycleHotbar(1);
                }
                return;
            }

            // Prevent certain things in inventory and chest mode, mainly to avoid
            // Players getting spell items out
            if (activeWand.getMode() == WandMode.INVENTORY) {
                // Don't allow the offhand slot to be messed with while the spell inventory is open
                if (event.getRawSlot() == 45)
                {
                    event.setCancelled(true);
                    return;
                }

                // Don't allow putting spells in a crafting slot
                if (slotType == InventoryType.SlotType.CRAFTING && heldSpell) {
                    event.setCancelled(true);
                    return;
                }

                // So many ways to try and move the wand around, that we have to watch for!
                if (isHotbar && Wand.isWand(player.getInventory().getItem(event.getHotbarButton()))) {
                    event.setCancelled(true);
                    return;
                }

                // Safety check for something that ought not to be possible
                // but perhaps happens with lag?
                if (Wand.isWand(event.getCursor())) {
                    activeWand.closeInventory();
                    event.setCursor(null);
                    event.setCancelled(true);
                    return;
                }
            } else {
                // Prevent moving items into the skill inventory via the hotbar buttons
                if (isHotbar && isWatchedSlot && !CompatibilityLib.getItemUtils().isEmpty(player.getInventory().getItem(event.getHotbarButton()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        } else if (activeWand != null) {
            // Check for changes that could have been made to the active wand
            int activeSlot = player.getInventory().getHeldItemSlot();
            if (event.getSlot() == activeSlot || (isHotbar && event.getHotbarButton() == activeSlot))
            {
                mage.checkWand();
                activeWand = mage.getActiveWand();
            }
        }

        // Don't allow smelting wands
        if (isFurnace && clickedWand && !isCraftableWand) {
            event.setCancelled(true);
            return;
        }

        if (isFurnace && isHotbar) {
            ItemStack destinationItem = player.getInventory().getItem(event.getHotbarButton());
            boolean swapToWand = Wand.isWand(destinationItem);
            boolean swapToCraftableWand = swapToWand && CompatibilityLib.getNBTUtils().getBoolean(destinationItem, "craftable", false);;

            if (swapToWand && !swapToCraftableWand) {
                event.setCancelled(true);
                return;
            }
        }

        if (isHotbar) {
            ItemStack destinationItem = player.getInventory().getItem(event.getHotbarButton());
            if (CompatibilityLib.getNBTUtils().getBoolean(destinationItem, "unmoveable", false)) {
                event.setCancelled(true);
                return;
            }
            if (isContainer && CompatibilityLib.getNBTUtils().getBoolean(destinationItem, "unstashable", false) && !player.hasPermission("magic.wand.override_stash")) {
                event.setCancelled(true);
                return;
            }
        }

        // Check for unstashable wands
        if (isContainer && !isContainerSlot && !player.hasPermission("magic.wand.override_stash")) {
            if (CompatibilityLib.getNBTUtils().getBoolean(clickedItem, "unstashable", false)) {
                event.setCancelled(true);
                return;
            }
        }

        // Check for taking bound wands out of chests
        if (isContainer && isWatchedSlot && Wand.isBound(clickedItem)) {
            Wand testWand = controller.getWand(clickedItem);
            if (!testWand.canUse(player)) {
                event.setCancelled(true);
                return;
            }
        }

        // Check for armor changing
        if (tryingToWear && clickedItem != null)
        {
            if (clickedWearable) {
                controller.onArmorUpdated(mage);
            }
        }

        // Check for dropping items out of a wand's inventory
        // or dropping undroppable wands
        if (isDrop) {
            String dropAction = CompatibilityLib.getNBTUtils().getString(clickedItem, "drop");
            if (dropAction != null && !dropAction.isEmpty()) {
                if (dropAction.equals("destroy")) {
                    event.setCurrentItem(new ItemStack(Material.AIR));
                    mage.checkWand();
                    mage.armorUpdated();
                } else {
                    controller.info("Unknown wand drop action: " + dropAction, 5);
                }
                event.setCancelled(true);
                return;
            }
            if (CompatibilityLib.getNBTUtils().getBoolean(clickedItem, "undroppable", false)) {
                event.setCancelled(true);
                if (activeWand != null) {
                    if (activeWand.getHotbarCount() > 1) {
                        activeWand.cycleHotbar(1);
                    } else {
                        activeWand.closeInventory();
                    }
                }
                return;
            }
            if (isWandInventoryOpen) {
                ItemStack droppedItem = clickedItem;

                if (!Wand.isSpell(droppedItem)) {
                    mage.giveItem(droppedItem);
                    event.setCurrentItem(null);
                    event.setCancelled(true);
                    return;
                }

                // This is a hack to deal with spells on cooldown disappearing,
                // Since the event handler doesn't match the zero-count itemstacks
                int heldSlot = player.getInventory().getHeldItemSlot();
                WandInventory hotbar = activeWand.getHotbar();
                if (hotbar != null && slot >= 0 && slot <= hotbar.getSize() && slot != heldSlot && activeWand.getMode() == WandMode.INVENTORY)
                {
                    if (slot > heldSlot) slot--;
                    if (slot < hotbar.getSize())
                    {
                        droppedItem = hotbar.getItem(slot);
                    }
                    else
                    {
                        slot = null;
                    }
                }
                else
                {
                    slot = null;
                }

                if (!controller.isSpellDroppingEnabled()) {
                    player.closeInventory();
                    String spellName = Wand.getSpell(droppedItem);
                    if (spellName != null && !activeWand.isManualQuickCastDisabled() && enableInventoryDropCasting) {
                        final Spell spell = mage.getSpell(spellName);
                        if (spell != null) {
                            // Delay this one tick because cancelling this event is going to mean the spell icon
                            // gets put back in the player inventory, and this can cause strange side effects with
                            // spells levelling up, or spells with GUIs or other inventories.
                            final Wand castWand = activeWand;
                            // This will also fire an arm swing animation event which we want to ignore
                            // Sadly this didn't work because the animation event arrives first
                            mage.checkLastClick(0);
                            controller.getPlugin().getServer().getScheduler().runTaskLater(controller.getPlugin(),
                                new WandCastTask(castWand, spell), 1);
                        }
                    }
                    event.setCancelled(true);

                    // This is needed to avoid spells on cooldown disappearing from the hotbar
                    if (hotbar != null && slot != null && mage.getActiveGUI() == null)
                    {
                        player.getInventory().setItem(event.getSlot(), droppedItem);
                        CompatibilityLib.getDeprecatedUtils().updateInventory(player);
                    }

                    return;
                }
                ItemStack newDrop = controller.removeItemFromWand(activeWand, droppedItem);

                if (newDrop != null) {
                    Location location = player.getLocation();
                    Item item = location.getWorld().dropItem(location, newDrop);
                    SafetyUtils.setVelocity(item, location.getDirection().normalize());
                } else {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Check for wand cycling with active inventory
        if (activeWand != null) {
            WandMode wandMode = activeWand.getMode();
            boolean isChestInventory = wandMode == WandMode.CHEST || wandMode == WandMode.SKILLS;
            if ((wandMode == WandMode.INVENTORY && isPlayerInventory)
                    || (isChestInventory && inventoryType == InventoryType.CHEST)) {
                if (activeWand.isInventoryOpen()) {
                    if (event.getAction() == InventoryAction.NOTHING) {
                        int direction = event.getClick() == ClickType.LEFT ? 1 : -1;
                        activeWand.cycleInventory(direction);
                        event.setCancelled(true);
                        return;
                    }

                    if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                        event.setCancelled(true);
                        return;
                    }

                    // Chest mode falls back to selection from here.
                    boolean isInventoryQuickSelect = isRightClick && wandMode == WandMode.INVENTORY && enableInventorySelection;
                    if (isInventoryQuickSelect || wandMode == WandMode.CHEST) {
                        // Closing the inventory inside this event will make it look like we're dropping
                        // the item that was clicked on, which (due to some really @^%#4 stupid decision on Mojang's part)
                        // will send left-click animate packets and cause a spell cast.
                        // So delay the close one tick.
                        Plugin plugin = controller.getPlugin();
                        plugin.getServer().getScheduler().runTaskLater(plugin, new ActivateIconTask(mage, activeWand, clickedItem), 1);
                        event.setCancelled(true);
                    }

                    // Prevent putting any non-skill item back into a skill inventory
                    if (wandMode == WandMode.SKILLS && isWatchedSlot
                        && !CompatibilityLib.getItemUtils().isEmpty(heldItem) && !Wand.isSkill(heldItem)) {
                        event.setCancelled(true);
                    }
                }
            }
        }

        // Check for dropping upgrades onto a wand
        if (!isWandInventoryOpen && clickedWand && !isContainer && !isEnderChest
            && (Wand.isUpgrade(heldItem) || Wand.isSpell(heldItem) || Wand.isSP(heldItem) || Wand.isBrush(heldItem))) {
            if (activeWand != null) {
                activeWand.deactivate();
            }
            Wand wand = controller.createWand(clickedItem);
            wand.setMage(mage);
            CurrencyAmount currency = CompatibilityLib.getInventoryUtils().getCurrencyAmount(heldItem);
            if (wand.addItem(heldItem)) {
                event.setCancelled(true);
                // Currency items will always absorb the full stack
                if (heldItem.getAmount() > 1 && currency == null) {
                    heldItem.setAmount(heldItem.getAmount() - 1);
                    event.setCursor(heldItem);
                } else {
                    event.setCursor(null);
                }
                event.getClickedInventory().setItem(event.getSlot(), wand.getItem());
            }
            if (activeWand != null) {
                mage.checkWand();
            }
        }
    }

    @EventHandler
    public void onInventoryClosed(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        // Update the active wand, it may have changed around
        Player player = (Player)event.getPlayer();
        Mage mage = controller.getRegisteredMage(player);
        if (mage == null) return;

        Wand previousWand = mage.getActiveWand();

        // Prevent spells getting smuggled out via crafting slots
        Inventory inventory = event.getInventory();
        if (inventory instanceof CraftingInventory && previousWand != null && previousWand.wasInventoryOpen()) {
            CraftingInventory craftingInventory = (CraftingInventory)inventory;
            ItemStack[] matrix = craftingInventory.getMatrix();
            for (int i = 0; i < matrix.length; i++) {
                matrix[i] = new ItemStack(Material.AIR);
            }
            craftingInventory.setMatrix(matrix);
        }

        GUIAction gui = mage.getActiveGUI();
        if (gui != null)
        {
            mage.onGUIDeactivate();
        }

        // Save the inventory state of the current wand if its spell inventory is open
        // This is just to make sure we don't lose changes made to the inventory
        if (previousWand != null && previousWand.isInventoryOpen()) {
            if (previousWand.getMode() == WandMode.INVENTORY) {
                previousWand.saveInventory();
                // Update hotbar names
                previousWand.updateHotbar();
            } else if (previousWand.getMode() == WandMode.CHEST || previousWand.getMode() == WandMode.SKILLS) {
                // Close and save an open chest mode inventory
                previousWand.closeInventory();
            }
        } else {
            if (previousWand != null && !previousWand.wasInventoryOpen() && previousWand.isAutoAbsorb()) {
                previousWand.checkInventoryForUpgrades();
            }
            mage.checkWand();
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player)event.getPlayer();
        Mage mage = controller.getRegisteredMage(player);
        if (mage == null) return;

        mage.setOpenCooldown(openCooldown);

        Wand wand = mage.getActiveWand();
        GUIAction gui = mage.getActiveGUI();
        if (wand != null && gui == null) {
            // NOTE: The type will never actually be CRAFTING, at least for now.
            // But we can hope for server-side player inventory open notification one day, right?
            // Anyway, check for opening another inventory and close the wand.
            if (event.getView().getType() != InventoryType.CRAFTING) {
                if (wand.getMode() == WandMode.INVENTORY || !wand.isInventoryOpen()) {
                    wand.deactivate(false);
                }
            }
        }
    }
}
