package de.rettedasplanet.chestlogger;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChestListener implements Listener {

    private final ChestLogger plugin;

    public ChestListener(ChestLogger plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChestPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            String ownerUUID = event.getPlayer().getUniqueId().toString();
            String ownerName = event.getPlayer().getName();
            String world = block.getWorld().getName();
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            String sql = "INSERT INTO chests(owner_uuid, owner_name, world, x, y, z) VALUES(?,?,?,?,?,?)";

            try (Connection conn = plugin.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, ownerUUID);
                pstmt.setString(2, ownerName);
                pstmt.setString(3, world);
                pstmt.setInt(4, x);
                pstmt.setInt(5, y);
                pstmt.setInt(6, z);
                pstmt.executeUpdate();

                plugin.getLogger().info("Chest placed by " + ownerName + " at (" + x + "," + y + "," + z + ")");

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to insert chest into database.");
                e.printStackTrace();
            }
        }
    }

    Integer getChestId(Block block) {
        String sql = "SELECT id FROM chests WHERE world=? AND x=? AND y=? AND z=?";
        try (Connection conn = plugin.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, block.getWorld().getName());
            pstmt.setInt(2, block.getX());
            pstmt.setInt(3, block.getY());
            pstmt.setInt(4, block.getZ());

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            } else {
                return null; // Chest not found in database
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        if (event.hasBlock()) {
            Block block = event.getClickedBlock();
            assert block != null;
            if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                Integer chestId = getChestId(block);
                if (chestId == null) {
                    return;
                }
                String playerUUID = event.getPlayer().getUniqueId().toString();
                String playerName = event.getPlayer().getName();
                String action = "open";

                String sql = "INSERT INTO log(chest_id, player_uuid, player_name, action) VALUES(?,?,?,?)";

                try (Connection conn = plugin.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setInt(1, chestId);
                    pstmt.setString(2, playerUUID);
                    pstmt.setString(3, playerName);
                    pstmt.setString(4, action);
                    pstmt.executeUpdate();

                    plugin.getLogger().info(playerName + " opened chest ID " + chestId);

                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to log chest open action.");
                    e.printStackTrace();
                }
            }
        }
    }

    @EventHandler
    public void onChestInteract(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Chest) {
            Chest chest = (Chest) holder;
            Block block = chest.getBlock();
            Integer chestId = getChestId(block);
            if (chestId == null) {
                return;
            }

            String playerUUID = event.getWhoClicked().getUniqueId().toString();
            String playerName = event.getWhoClicked().getName();
            String action;
            String item = event.getCurrentItem() != null ? event.getCurrentItem().getType().toString() : "nothing";

            if (event.isShiftClick() && event.getClickedInventory() != event.getWhoClicked().getInventory()) {
                action = "add_item";
            } else if (event.getClickedInventory() == event.getWhoClicked().getInventory()) {
                action = "remove_item";
            } else {
                return;
            }

            String sql = "INSERT INTO log(chest_id, player_uuid, player_name, action, item) VALUES(?,?,?,?,?)";

            try (Connection conn = plugin.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, chestId);
                pstmt.setString(2, playerUUID);
                pstmt.setString(3, playerName);
                pstmt.setString(4, action);
                pstmt.setString(5, item);
                pstmt.executeUpdate();

                plugin.getLogger().info(playerName + " " + action + " " + item + " in chest ID " + chestId);

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log chest item interaction.");
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == org.bukkit.Material.CHEST || block.getType() == org.bukkit.Material.TRAPPED_CHEST) {
            Integer chestId = getChestId(block);
            if (chestId == null) {
                return;
            }

            String sql = "DELETE FROM chests WHERE id=?";

            try (Connection conn = plugin.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, chestId);
                pstmt.executeUpdate();

                plugin.getLogger().info("Chest ID " + chestId + " removed from database.");

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove chest from database.");
                e.printStackTrace();
            }
        }
    }

}
