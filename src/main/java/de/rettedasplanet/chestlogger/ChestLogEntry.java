package de.rettedasplanet.chestlogger;

public class ChestLogEntry {
    private String playerUUID;
    private String action;
    private String item;
    private String timestamp;

    public ChestLogEntry(String playerName, String playerUUID, String action, String item, String timestamp) {
        this.playerUUID = playerUUID;
        this.action = action;
        this.item = item;
        this.timestamp = timestamp;
    }

    public String getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(String playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
