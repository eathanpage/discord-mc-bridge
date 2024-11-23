package com.garfield.chatintegration;

import org.bukkit.plugin.java.JavaPlugin;

public class ChatIntegration extends JavaPlugin {
    private DiscordListener discordListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discordListener = new DiscordListener(this);
        getServer().getPluginManager().registerEvents(new ChatListener(this.discordListener), this);
        discordListener.sendMessageToWebhook("Server is starting", "System", discordListener.jda.getSelfUser().getAvatarUrl());
    }

    @Override
    public void onDisable() {
        if (discordListener != null) {
            discordListener.sendMessageToWebhook("Server is stopping", "System", discordListener.jda.getSelfUser().getAvatarUrl());
            discordListener.shutdown();
        }
    }
}
