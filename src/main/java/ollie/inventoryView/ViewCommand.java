package ollie.inventoryView;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class ViewCommand implements CommandExecutor {
    public static ItemStack separatorGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
    public static ItemStack removeGlass = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);

    static {
        ItemMeta separatorMeta = separatorGlass.getItemMeta();
        separatorMeta.setDisplayName(" ");
        separatorGlass.setItemMeta(separatorMeta);

        ItemMeta removeMeta = removeGlass.getItemMeta();
        removeMeta.setDisplayName(" ");
        removeGlass.setItemMeta(removeMeta);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player viewer = (Player) sender;
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Op is required");
            return true;
        }

        if (args.length == 0) {
            viewer.sendMessage(ChatColor.RED + "A player name is needed");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            openViewer(viewer, target);
        }
        else {
            viewer.sendMessage(ChatColor.RED + "Invalid Player");
        }

        return true;
    }

    public static void openViewer(Player viewer, Player target) {
        Inventory viewInventory = Bukkit.createInventory(null, 54, String.format("%s's Inventory", target.getName()));
        PlayerInventory targetInventory = target.getInventory();

        for (int i = 0; i < 4; i++) { // armour
            viewInventory.setItem(i, targetInventory.getArmorContents()[3-i]);
        }

        for (int i = 4; i < 8; i++) {
            if (i == 5)
                continue;
            viewInventory.setItem(i, separatorGlass);
        }

        viewInventory.setItem(5, targetInventory.getItemInOffHand()); // offhand
        viewInventory.setItem(8, removeGlass); // remove slot

        ItemStack[] contents = targetInventory.getStorageContents();

        for (int i = 0; i < 27; i++) { // main inventory
            viewInventory.setItem(i + 9, contents[i + 9]);
        }

        for (int i = 0; i < 9; i++) { // hotbar
            viewInventory.setItem(i + 45, contents[i]);
        }

        for (int i = 35; i < 45; i++) {
            viewInventory.setItem(i, separatorGlass);
        }


        InventoryView.targetViewersMap.computeIfAbsent(target.getUniqueId(), uuid -> new ArrayList<>()).add(viewer.getUniqueId());
        viewer.openInventory(viewInventory);
    }

}
