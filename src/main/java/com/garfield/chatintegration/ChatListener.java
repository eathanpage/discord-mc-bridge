package com.garfield.chatintegration;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.dv8tion.jda.api.entities.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


public class ChatListener implements Listener {
    private final DiscordListener discordListener;
    private final JSONObject translations;

    public ChatListener(DiscordListener discordListener) {
        this.discordListener = discordListener;
        this.translations = loadTranslations();
    }

    private void sendSystemMessage(String message) {
        discordListener.sendMessageToWebhook(message, "System", discordListener.jda.getSelfUser().getAvatarUrl());
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
        Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());

        String senderName = (member != null) ? member.getEffectiveName() : player.getName();
        String avatarUrl = (member != null) ? discordListener.getAvatarUrl(member) : String.format("https://mc-heads.net/avatar/%s", player.getUniqueId());

        discordListener.sendMessageToWebhook(message, senderName, avatarUrl);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());

        String joinMessage = (member != null)
                ? "<@" + member.getId() + "> has joined the game"
                : player.getName() + " has joined the game";

        sendSystemMessage(joinMessage);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());
        String leaveMessage = (member != null)
                ? "<@" + member.getId() + "> has left the game"
                : player.getName() + " has left the game";
        sendSystemMessage(leaveMessage);
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();

        if (display != null) {
            String titleKey = getTranslationKey(display.title());
            String descriptionKey = getTranslationKey(display.description());

            String translatedTitle = (titleKey != null && translations.has(titleKey))
                    ? translations.getString(titleKey)
                    : display.title().toString();
            String translatedDescription = (descriptionKey != null && translations.has(descriptionKey))
                    ? translations.getString(descriptionKey)
                    : display.description().toString();

            // Check for linked Discord account
            Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());
            String senderName = (member != null) ? member.getEffectiveName() : player.getName();

            String advancementMessage = String.format(
                    "%s has made the advancement [**%s**]\n-# Description: %s",
                    senderName,
                    translatedTitle,
                    translatedDescription
            );

            sendSystemMessage(advancementMessage);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String deathMessage = event.getDeathMessage();
        assert deathMessage != null;
        String trimmedMessage = deathMessage.replaceAll("§[0-9a-fA-FklmnoK-LOrR]", "");;
        sendSystemMessage(trimmedMessage);
    }
}
