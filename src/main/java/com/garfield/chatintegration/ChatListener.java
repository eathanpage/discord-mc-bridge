package com.garfield.chatintegration;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


public class ChatListener implements Listener {
    private final DiscordBot discordBot;
    private final JSONObject translations;

    public ChatListener(DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.translations = loadTranslations();
    }

    private void sendSystemMessage(String message) {
        discordBot.sendMessageToWebhook(message, "System", discordBot.jda.getSelfUser().getAvatarUrl());
    }

    private JSONObject loadTranslations() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("en_us.json")) {
            if (is == null) {
                throw new RuntimeException("Cannot find en_us.json resource");
            }
            Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name());
            String jsonText = scanner.useDelimiter("\\A").next();
            return new JSONObject(jsonText);
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }
    private String getTranslationKey(Component component) {
        if (component instanceof TranslatableComponent) {
            return ((TranslatableComponent) component).key();
        }
        return null;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        Member member = discordBot.getDiscordMemberFromUUID(player.getUniqueId().toString());
        discordBot.sendMessageToWebhook(message, member.getEffectiveName(), getAvatarUrl(member));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Member member = discordBot.getDiscordMemberFromUUID(player.getUniqueId().toString());
        sendSystemMessage("<@" + member.getId() + "> has joined the game");
    }
    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Member member = discordBot.getDiscordMemberFromUUID(player.getUniqueId().toString());
        sendSystemMessage("<@" + member.getId() + "> has left the game");
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();

        if (display != null) {
            String titleKey = getTranslationKey(display.title());
            String descriptionKey = getTranslationKey(display.description());

            if (titleKey != null && descriptionKey != null) {
                Member member = discordBot.getDiscordMemberFromUUID(player.getUniqueId().toString());
                String translatedTitle = translations.getString(titleKey);
                String translatedDescription = translations.getString(descriptionKey);

                sendSystemMessage("<@" + member.getId() + "> has made the advancement [**" + translatedTitle + "**]\n-# Description: " + translatedDescription);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Member member = discordBot.getDiscordMemberFromUUID(player.getUniqueId().toString());
        String deathMessage = event.getDeathMessage();
        assert deathMessage != null;
        String trimmedMessage = deathMessage.replaceAll("§[0-9a-fA-FklmnoK-LOrR]", "");;
        sendSystemMessage(trimmedMessage);
    }



    public String getAvatarUrl(Member user) {
        if (user != null) {
            String avatarId = user.getUser().getAvatarId();
            if (avatarId != null) {
                return String.format("https://cdn.discordapp.com/avatars/%s/%s.png", user.getId(), avatarId);
            } else {
                return user.getDefaultAvatarUrl();
            }
        }
        return null;
    }
}
