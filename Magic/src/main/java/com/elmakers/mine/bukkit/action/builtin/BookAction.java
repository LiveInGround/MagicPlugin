package com.elmakers.mine.bukkit.action.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import com.elmakers.mine.bukkit.action.BaseSpellAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.Messages;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.api.wand.Wand;
import com.elmakers.mine.bukkit.utility.CompatibilityLib;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.StringUtils;

public class BookAction extends BaseSpellAction {

    @Nonnull
    private String title = "";
    @Nonnull
    private String author = "";
    @Nullable
    private String bookItem;
    private ConfigurationSection pages;
    private boolean giveBook;
    private boolean virtualBook;

    private ItemStack updateBook(CastContext context, Mage targetMage, ItemStack book) {
        BookMeta meta = (BookMeta) book.getItemMeta();

        List<String> pages = replaceContents(context, targetMage);

        meta.setTitle(title);
        meta.setAuthor(author);
        meta.setPages(pages);

        book.setItemMeta(meta);

        return book;
    }

    private List<String> replaceContents(CastContext context, Mage targetMage) {
        List<String> newContents = new ArrayList<>();
        Set<String> pageKeys = pages.getKeys(false);
        for (String pageKey : pageKeys) {
            int pageNumber = 0;
            try {
                pageNumber = Integer.parseInt(pageKey) - 1;
            } catch (NumberFormatException ex) {
                context.getController().getLogger().warning("Invalid page number: " + pageKey);
                continue;
            }
            String pageText = "";
            List<String> lines = ConfigurationUtils.getStringList(pages, pageKey);
            if (lines == null) {
                pageText = pages.getString(pageKey);
            } else {
                pageText = StringUtils.join(lines, "\n");
            }
            pageText = context.parameterize(pageText);
            while (newContents.size() <= pageNumber) newContents.add("");
            newContents.set(pageNumber, CompatibilityLib.getCompatibilityUtils().translateColors(pageText));
        }

        return newContents;
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);

        Messages messages = context.getController().getMessages();
        String titleParam = parameters.getString("title", "");
        String authorParam = parameters.getString("author", context.getMage().getName());

        title = messages.get(titleParam, CompatibilityLib.getCompatibilityUtils().translateColors(titleParam));
        author = messages.get(authorParam, CompatibilityLib.getCompatibilityUtils().translateColors(authorParam));
        pages = parameters.getConfigurationSection("pages");
        giveBook = parameters.getBoolean("give_book", false);
        bookItem = parameters.getString("book_item");
        virtualBook = parameters.getBoolean("virtual_book", !giveBook && bookItem != null);
    }

    @Nullable
    private ItemStack getBook(CastContext context, Mage targetMage) {
        if (bookItem != null) {
            return context.getController().createItem(bookItem, targetMage);
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        updateBook(context, targetMage, book);
        return book;
    }

    private boolean checkBook(ItemStack book) {
        if (CompatibilityLib.getItemUtils().isEmpty(book)) {
            return false;
        }
        return book.getType() == Material.WRITTEN_BOOK;
    }

    public SpellResult performGive(CastContext context) {
        Entity target = context.getTargetEntity();
        if (target == null) {
            return SpellResult.NO_TARGET;
        }
        if (!(target instanceof InventoryHolder)) {
            return SpellResult.NO_TARGET;
        }

        Mage targetMage = context.getController().getMage(target);
        ItemStack book = getBook(context, targetMage);
        if (!checkBook(book)) {
            return SpellResult.FAIL;
        }
        targetMage.giveItem(book);

        return SpellResult.CAST;
    }

    public SpellResult performVirtual(CastContext context) {
        Entity target = context.getTargetEntity();
        if (target == null) {
            return SpellResult.NO_TARGET;
        }
        if (!(target instanceof Player)) {
            return SpellResult.NO_TARGET;
        }

        Mage targetMage = context.getController().getMage(target);
        ItemStack book = getBook(context, targetMage);
        if (!checkBook(book)) {
            return SpellResult.FAIL;
        }
        boolean success = CompatibilityLib.getCompatibilityUtils().openBook((Player)target, book);

        return success ? SpellResult.CAST : SpellResult.FAIL;
    }

    @Override
    public SpellResult perform(CastContext context) {
        if (pages == null && bookItem == null) {
            return SpellResult.FAIL;
        }
        if (giveBook) {
            return performGive(context);
        }
        if (virtualBook) {
            return performVirtual(context);
        }
        Mage mage = context.getMage();
        Wand wand = context.getWand();
        if (wand == null || wand.getItem().getType() != Material.WRITTEN_BOOK) {
            return SpellResult.NO_TARGET;
        }

        ItemStack book = wand.getItem();
        updateBook(context, mage, book);
        if (wand == mage.getActiveWand()) {
            mage.getPlayer().getInventory().setItemInMainHand(wand.getItem());
        } else if (wand == mage.getOffhandWand()) {
            mage.getPlayer().getInventory().setItemInOffHand(wand.getItem());
        }

        return SpellResult.CAST;
    }
}
