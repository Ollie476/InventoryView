package ollie.inventoryView;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class InventoryView extends JavaPlugin implements Listener {
    public static HashMap<UUID, List<UUID>> targetViewersMap = new HashMap<>();

    @Override
    public void onEnable() {
        getCommand("view").setExecutor(new ViewCommand());

        getCommand("view").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] args) {
                if (args.length == 1) {
                    List<String> playerNames = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playerNames.add(player.getName());
                    }
                    return playerNames;
                }

                return List.of();
            }
        });

        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::updateViewers, 0, 1);

    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player viewer = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();
        ItemMeta clickedItemMeta = null;

        if (clickedItem != null) {
            clickedItemMeta = clickedItem.getItemMeta();
        }

        if (e.getView().getTitle().endsWith("'s Inventory")) {
            if (clickedItemMeta != null && clickedItemMeta.getDisplayName().equals(" ")) {
                switch (clickedItem.getType()) {
                    case Material.GRAY_STAINED_GLASS_PANE -> e.setCancelled(true);
                    case Material.RED_STAINED_GLASS_PANE -> {
                        e.setCancelled(true);
                        viewer.setItemOnCursor(null);
                    }
                }
                return;
            }

            Player target = Bukkit.getPlayer(e.getView().getTitle().split("'s ")[0]);
            int viewerIndex = e.getRawSlot();
            int targetIndex = toTargetIndex(viewerIndex);

            if (targetIndex == -1) return;

            if (viewerIndex < e.getView().getTopInventory().getSize()) {
                e.setCancelled(true);
            }

            ItemStack cursor = e.getCursor();

            if (e.isLeftClick()) {
                if (clickedItem != null && cursor != null && cursor.isSimilar(clickedItem)) {
                    ItemStack slotItem = target.getInventory().getItem(targetIndex);
                    int slotAmount = (slotItem != null) ? slotItem.getAmount() : 0;
                    int cursorAmount = cursor.getAmount();
                    int maxStackSize = cursor.getMaxStackSize();

                    int spaceLeft = maxStackSize - slotAmount;
                    int amountToAdd = Math.min(spaceLeft, cursorAmount);

                    if (slotItem != null && slotItem.getType() != Material.AIR) {
                        slotItem.setAmount(slotAmount + amountToAdd);
                        target.getInventory().setItem(targetIndex, slotItem);
                    } else {
                        ItemStack newStack = cursor.clone();
                        newStack.setAmount(amountToAdd);
                        target.getInventory().setItem(targetIndex, newStack);
                    }

                    cursor.setAmount(cursorAmount - amountToAdd);
                    viewer.setItemOnCursor(cursorAmount - amountToAdd > 0 ? cursor : null);
                } else {
                    viewer.setItemOnCursor(clickedItem);
                    target.getInventory().setItem(targetIndex, cursor);
                }
            }

            else if (e.isRightClick()) {
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if (clickedItem != null && cursor.isSimilar(clickedItem)) {
                        cursor.setAmount(cursor.getAmount() - 1);
                        viewer.setItemOnCursor(cursor);

                        clickedItem.setAmount(clickedItem.getAmount() + 1);
                        target.getInventory().setItem(targetIndex, clickedItem);
                    } else if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                        ItemStack placedItem = cursor.clone();
                        placedItem.setAmount(1);
                        target.getInventory().setItem(targetIndex, placedItem);

                        cursor.setAmount(cursor.getAmount() - 1);
                        viewer.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
                    }
                } else if (clickedItem != null && clickedItem.getAmount() > 1) {
                    int startAmount = clickedItem.getAmount();
                    int halfAmount = startAmount / 2;

                    ItemStack cursorItem = clickedItem.clone();
                    cursorItem.setAmount(startAmount - halfAmount); // ceil half
                    clickedItem.setAmount(halfAmount);              // floor half

                    viewer.setItemOnCursor(cursorItem);
                    target.getInventory().setItem(targetIndex, clickedItem);
                }
            }

        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        UUID viewerId = e.getPlayer().getUniqueId();
        targetViewersMap.values().forEach(list -> list.remove(viewerId));
    }

    private int toTargetIndex(int index) {
        if (index >= 0 && index < 4) // armour
            return 39 - index;
        else if (index == 5) // offhand
            return 40;
        else if (index >= 9 && index < 36) // main inventory
            return index;
        else if (index >= 45 && index < 54) // hotbar
            return index - 45;

        return -1;
    }

    private Player getTargetFromViewer(Player viewer) {
        UUID viewerUUID = viewer.getUniqueId();
        for(UUID targetUUID: targetViewersMap.keySet()) {
            if (targetViewersMap.get(targetUUID).contains(viewerUUID))
                return Bukkit.getPlayer(targetUUID);
        }
        return null;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) { // need to make it so it works nicely and doesnt randomly delete
        Player viewer = (Player) e.getWhoClicked();
        Player target = getTargetFromViewer(viewer);
        if (target == null)
            return;

        Inventory inventory = e.getInventory();

        int itemsRemoved = 0;

        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= inventory.getSize()) continue;
            e.setCancelled(true);

            int targetIndex = toTargetIndex(rawSlot);
            if (targetIndex == -1)
                continue;

            ItemStack replacementItem = e.getNewItems().get(rawSlot);
            inventory.setItem(rawSlot, replacementItem);
            target.getInventory().setItem(targetIndex, replacementItem);
            if (replacementItem.getAmount() != 64)
                itemsRemoved += replacementItem.getAmount();
        }

        int finalItemsRemoved = itemsRemoved;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ItemStack cursor = viewer.getItemOnCursor();

            int newAmount = cursor.getAmount() - finalItemsRemoved;
            if (newAmount > 0) {
                cursor.setAmount(newAmount);
                viewer.setItemOnCursor(cursor);
            } else {
                viewer.setItemOnCursor(null);
            }
        }, 1L);

    }

    private void updateViewers() {
        if (targetViewersMap.isEmpty())
            return;

        for (UUID targetUUID : targetViewersMap.keySet()) {
            Player target = Bukkit.getPlayer(targetUUID);

            for (UUID viewerUUID : targetViewersMap.get(targetUUID)) {
                Player viewer = Bukkit.getPlayer(viewerUUID);

                Inventory viewerInventory = viewer.getOpenInventory().getTopInventory();
                int size = viewerInventory.getSize();

                for (int i = 0; i < size; i++) {
                    int targetIndex = toTargetIndex(i);

                    if (targetIndex == -1)
                        continue;
                    viewerInventory.setItem(i, target.getInventory().getItem(targetIndex));
                }
            }
        }
    }

    @EventHandler
    public void onEntityRightClick(PlayerInteractAtEntityEvent e) {
        Player viewer = e.getPlayer();

        if (viewer.isSneaking() && e.getRightClicked() instanceof Player target) {
            ViewCommand.openViewer(viewer, target);
        }
    }
}
