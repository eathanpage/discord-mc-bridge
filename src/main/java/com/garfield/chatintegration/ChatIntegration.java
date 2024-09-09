package com.garfield.chatintegration;

import org.bukkit.plugin.java.JavaPlugin;

public class ChatIntegration extends JavaPlugin {
    private DiscordBot discordBot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.discordBot = new DiscordBot(this);
        getServer().getPluginManager().registerEvents(new ChatListener(this.discordBot), this);
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.sendMessageToWebhook("Server is restarting", "System", discordBot.jda.getSelfUser().getAvatarUrl());
            discordBot.shutdown();

        }
    }
}
