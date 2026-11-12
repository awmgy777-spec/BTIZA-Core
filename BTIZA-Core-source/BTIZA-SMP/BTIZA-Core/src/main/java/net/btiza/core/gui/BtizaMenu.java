package net.btiza.core.gui;

import net.btiza.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for every BTIZA 54-slot menu. Clicks are cancelled everywhere by MenuListener,
 * so items cannot be taken or moved. Only slots registered with button() respond to clicks.
 * Each open creates a fresh menu instance, so no state leaks between players.
 */
public abstract class BtizaMenu implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_CLOSE = 49;
    public static final int SLOT_NEXT = 50;

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    // The holder must be this menu, so the inventory can route clicks back to it.
    @SuppressWarnings("this-escape")
    protected BtizaMenu(String title) {
        this.inventory = Bukkit.createInventory(this, SIZE, Text.mini(title));
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    public final void open(Player player) {
        render();
        player.openInventory(inventory);
    }

    /** Re-renders the open menu, for example after a page change or a balance update. */
    public final void refresh() {
        render();
    }

    /** Lays out the menu contents. Called on every render. */
    protected abstract void build();

    protected void onClose(Player player) {
    }

    protected final void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        actions.remove(slot);
    }

    protected final void button(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    protected final void closeButton() {
        button(SLOT_CLOSE,
                ItemBuilder.of(Material.BARRIER).name("<red><bold>Close").lore("<gray>Click to close this menu.").build(),
                event -> event.getWhoClicked().closeInventory());
    }

    final void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    final void handleClose(Player player) {
        onClose(player);
    }

    private void render() {
        actions.clear();
        inventory.clear();
        ItemStack filler = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        build();
    }
}
