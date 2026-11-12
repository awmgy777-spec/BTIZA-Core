package net.btiza.core.gui;

import net.btiza.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Fluent builder for GUI items. Names and lore use MiniMessage. */
public final class ItemBuilder {

    private final ItemStack item;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        return this;
    }

    public ItemBuilder name(String miniMessage) {
        item.editMeta(meta -> meta.displayName(Text.mini(miniMessage)));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> components = lines.stream().map(Text::mini).toList();
        item.editMeta(meta -> meta.lore(components));
        return this;
    }

    /** Adds a visual glint without a real enchantment effect. */
    public ItemBuilder glow() {
        item.editMeta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
