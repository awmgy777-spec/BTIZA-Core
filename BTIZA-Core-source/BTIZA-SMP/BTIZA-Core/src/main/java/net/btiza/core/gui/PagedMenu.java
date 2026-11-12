package net.btiza.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A 54-slot menu that shows up to 28 entries per page in the central area,
 * with previous, next, back, and close controls along the bottom row.
 */
public abstract class PagedMenu<T> extends BtizaMenu {

    public static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    public static final int PAGE_SIZE = CONTENT_SLOTS.length;

    private int page;

    protected PagedMenu(String title) {
        super(title);
    }

    /** Current entries. Called on every render, so lists can change while the menu is open. */
    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void onSelect(InventoryClickEvent event, T entry);

    protected boolean hasBack() {
        return false;
    }

    protected void onBack(Player player) {
    }

    public final int page() {
        return page;
    }

    @Override
    protected void build() {
        List<T> list = entries();
        int pages = pageCount(list.size());
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < list.size(); i++) {
            T entry = list.get(start + i);
            button(CONTENT_SLOTS[i], icon(entry), event -> onSelect(event, entry));
        }
        buildNavigation(pages);
    }

    protected int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    protected void buildNavigation(int pages) {
        set(4, ItemBuilder.of(Material.PAPER).name("<gold>Page " + (page + 1) + "/" + pages).build());
        if (page > 0) {
            button(SLOT_PREV, ItemBuilder.of(Material.ARROW).name("<gold>Previous page").build(), event -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            button(SLOT_NEXT, ItemBuilder.of(Material.ARROW).name("<gold>Next page").build(), event -> {
                page++;
                refresh();
            });
        }
        if (hasBack()) {
            button(SLOT_BACK, ItemBuilder.of(Material.OAK_SIGN).name("<gold>Back").build(),
                    event -> onBack((Player) event.getWhoClicked()));
        }
        closeButton();
    }
}
