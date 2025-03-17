package com.garfield.chatintegration;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.ShowEntity;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.json.JSONObject;

import java.awt.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class ChatListener implements Listener {
    private final DiscordListener discordListener;
    private final JSONObject translations;
    private final Map<String, Color> colours;

    public ChatListener(DiscordListener discordListener) {
        this.discordListener = discordListener;
        this.colours = loadColours();
        this.translations = loadTranslations();
    }

    private void sendSystemMessage(String message) {
        discordListener.sendMessageToWebhook(message, "System", discordListener.jda.getSelfUser().getAvatarUrl());
    }

    private Map<String, Color> loadColours() {
        Map<String, Color> colourMap = new HashMap<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("colours.json")) {
            if (is == null) {
                throw new RuntimeException("Cannot find colours.json resource");
            }
            Scanner scanner = new Scanner(is, StandardCharsets.UTF_8);
            String jsonText = scanner.useDelimiter("\\A").next();
            JSONObject jsonObject = new JSONObject(jsonText);

            for (String key : jsonObject.keySet()) {
                String hex = jsonObject.getString(key);
                Color color = Color.decode(hex);
                colourMap.put(key, color);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return colourMap;
    }

    private JSONObject loadTranslations() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("en_us.json")) {
            if (is == null) {
                throw new RuntimeException("Cannot find en_us.json resource");
            }
            Scanner scanner = new Scanner(is, StandardCharsets.UTF_8);
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
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());
        String senderName = (member != null) ? member.getEffectiveName() : player.getName();
        String avatarUrl = (member != null) ? discordListener.getAvatarUrl(member) : String.format("https://mc-heads.net/avatar/%s", player.getUniqueId());
        if (message.startsWith("xaero-waypoint:")) {
            String[] parts = message.split(":");
            if (parts.length >= 10) {
                String waypointName = parts[1];
                if (parts[9].contains("overworld")) {
                    parts[9] = "Overworld";
                } else if (parts[9].contains("nether")) {
                    parts[9] = "Nether";
                } else if (parts[9].contains("end")) {
                    parts[9] = "End";
                }
                String seed = String.valueOf(Objects.requireNonNull(Bukkit.getWorld("world")).getSeed());
                String dimension = parts[9];
                String x = parts[3];
                String y = parts[4];
                String z = parts[5];
                String colour = parts[6];
                String chunkbaseUrl = String.format("https://www.chunkbase.com/apps/seed-map#seed=%s&platform=java_1_21_4&dimension=%s&x=%s&z=%s&pinX=%s&pinZ=%s&zoom=0.5", seed, dimension, x, z, x, z);
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("Waypoint");
                embedBuilder.setDescription("**" + waypointName + "**");
                embedBuilder.addField("Dimension", dimension, false);
                embedBuilder.addField("Seed Map", "[Click Here!](" + chunkbaseUrl + ")", true);
                embedBuilder.addField("Co-ordinates", "**x**: " + x + ", **y**: " + y + ", **z**: " + z, false);
                embedBuilder.setColor(colours.getOrDefault(colour, Color.RED));
                embedBuilder.setFooter(message);
                MessageEmbed embed = embedBuilder.build();
                discordListener.sendMessageToWebhook(embed, senderName, avatarUrl);
            } else {
                discordListener.sendMessageToWebhook("Invalid waypoint format", senderName, avatarUrl);
            }
        } else {
            discordListener.sendMessageToWebhook(message, senderName, avatarUrl);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());
        String joinMessage = (member != null) ? "<@" + member.getId() + "> has joined the game" : player.getName() + " has joined the game";
        sendSystemMessage(joinMessage);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Member member = discordListener.getDiscordMemberFromUUID(player.getUniqueId().toString());
        String leaveMessage = (member != null) ? "<@" + member.getId() + "> has left the game" : player.getName() + " has left the game";
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

            if (titleKey != null && translations.has(titleKey) && descriptionKey != null && translations.has(descriptionKey)) {
                String translatedTitle = translations.getString(titleKey);
                String translatedDescription = translations.getString(descriptionKey);

                String discordId = discordListener.getDiscordIdFromUUID(player.getUniqueId().toString());
                String senderName = (discordId != null) ? "<@" + discordId + ">" : player.getName();

                String advancementMessage = String.format("%s has made the advancement [**%s**]\n-# Description: %s", senderName, translatedTitle, translatedDescription);

                sendSystemMessage(advancementMessage);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Component deathMessage = event.deathMessage();
        if (deathMessage == null) {
            return;
        }

        String plainTextDeathMessage;
        if (deathMessage instanceof TranslatableComponent translatable) {
            String key = translatable.key();
            String translation = translations.optString(key, key);
            List<Component> args = translatable.args();
            plainTextDeathMessage = replacePlaceholdersWithMentions(translation, args);
        } else {
            plainTextDeathMessage = PlainTextComponentSerializer.plainText().serialize(deathMessage);
        }
        String trimmedMessage = plainTextDeathMessage.replaceAll("§[0-9a-fA-FklmnoK-LOrR]", "");
        sendSystemMessage(trimmedMessage);
    }

    private String replacePlaceholdersWithMentions(String translation, List<Component> args) {
        String result = translation;

        for (int i = 0; i < args.size(); i++) {
            Component arg = args.get(i);
            String uuid = extractUUID(arg);
            if (uuid != null) {
                String discordId = discordListener.getDiscordIdFromUUID(uuid);
                if (discordId != null) {
                    result = result.replace("%" + (i + 1) + "$s", "<@" + discordId + ">");
                    continue;
                }
            }
            String argText = PlainTextComponentSerializer.plainText().serialize(arg);
            result = result.replace("%" + (i + 1) + "$s", argText);
        }

        return result;
    }

    private String extractUUID(Component component) {
        if (component instanceof TextComponent) {
            HoverEvent<?> hoverEvent = component.style().hoverEvent();
            if (hoverEvent != null && hoverEvent.action() == HoverEvent.Action.SHOW_ENTITY) {
                ShowEntity showEntity = (ShowEntity) hoverEvent.value();
                return showEntity.id().toString();
            }
        }
        return null;
    }
}