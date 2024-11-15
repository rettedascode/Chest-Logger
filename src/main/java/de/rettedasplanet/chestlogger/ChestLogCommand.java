package de.rettedasplanet.chestlogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ChestLogCommand implements CommandExecutor {

    private final ChestLogger plugin;

    public ChestLogCommand(ChestLogger plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Ensure the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("check")) {
            Player player = (Player) sender;

            Block targetBlock = player.getTargetBlockExact(5); // Adjust distance as needed

            if (targetBlock == null || !(targetBlock.getState() instanceof Chest)) {
                player.sendMessage("You are not looking at a chest.");
                return true;
            }

            Integer chestId = plugin.getChestListener().getChestId(targetBlock);
            if (chestId == null) {
                player.sendMessage("This chest is not tracked by the ChestLogger.");
                return true;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String sql = "SELECT player_name, player_uuid, action, item, timestamp FROM log WHERE chest_id=? ORDER BY timestamp DESC LIMIT 100";
                try (Connection conn = plugin.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setInt(1, chestId);
                    ResultSet rs = pstmt.executeQuery();

                    List<ChestLogEntry> logs = new ArrayList<>();

                    while (rs.next()) {
                        String playerName = rs.getString("player_name");
                        String playerUUID = rs.getString("player_uuid");
                        String action = rs.getString("action");
                        String item = rs.getString("item");
                        String timestamp = rs.getString("timestamp");

                        ChestLogEntry entry = new ChestLogEntry(playerName, playerUUID, action, item, timestamp);
                        logs.add(entry);
                    }

                    if (logs.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("No logs found for this chest.");
                        });
                        return;
                    }

                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String json = gson.toJson(logs);

                    File dataFolder = plugin.getDataFolder();
                    if (!dataFolder.exists()) {
                        dataFolder.mkdir();
                    }
                    File jsonFile = new File(dataFolder, "chestlog_" + chestId + ".json");
                    try (FileWriter writer = new FileWriter(jsonFile)) {
                        writer.write(json);
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to write logs to JSON file.");
                        e.printStackTrace();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("An error occurred while writing the logs to a file.");
                        });
                        return;
                    }

                    // Upload the file to Gofile.io
                    String downloadLink = uploadFile(jsonFile);
                    if (downloadLink == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage("Failed to upload the logs file.");
                        });
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§6Chest logs for Chest ID :" + chestId + "have been saved and uploaded.");
                        player.sendMessage("§aDownload link: §e" + downloadLink);
                    });

                    jsonFile.delete();

                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to retrieve chest logs.");
                    e.printStackTrace();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("An error occurred while retrieving the chest logs.");
                    });
                }
            });

            return true;
        }

        // If the command syntax is incorrect
        sender.sendMessage("Usage: /chestlog check");
        return true;
    }

    private String uploadFile(File file) {
        String uploadURL = "https://store1.gofile.io/contents/uploadfile";
        try {
            // Prepare the HTTP connection
            URL url = new URL(uploadURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            String boundary = "----WebKitFormBoundary" + Long.toHexString(System.currentTimeMillis());
            String CRLF = "\r\n";

            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (
                    OutputStream output = connection.getOutputStream();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
            ) {
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"").append(CRLF);
                writer.append("Content-Type: application/json").append(CRLF);
                writer.append(CRLF).flush();
                Files.copy(file.toPath(), output);
                output.flush();
                writer.append(CRLF).flush();

                writer.append("--" + boundary + "--").append(CRLF).flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder responseSB = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        responseSB.append(line);
                    }

                    Gson gson = new Gson();
                    GofileResponse response = gson.fromJson(responseSB.toString(), GofileResponse.class);

                    if ("ok".equalsIgnoreCase(response.status)) {
                        String downloadPage = response.data.downloadPage;
                        return downloadPage;
                    } else {
                        plugin.getLogger().severe("File upload failed: " + response.status);
                        return null;
                    }
                }
            } else {
                plugin.getLogger().severe("File upload failed with HTTP response code: " + responseCode);
                return null;
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to upload file to Gofile.io");
            e.printStackTrace();
            return null;
        }
    }

    private class GofileResponse {
        String status;
        GofileData data;
    }

    private class GofileData {
        String downloadPage;
        String fileName;
    }
}