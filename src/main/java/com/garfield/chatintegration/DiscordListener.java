package com.garfield.chatintegration;
import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;

public class DiscordListener extends ListenerAdapter {
    public JDA jda;
    private final TextChannel textChannel;
    private final WebhookClient webhookClient;
    private final ChatIntegration plugin;
    private final JsonObject playersMap;

    public DiscordListener(ChatIntegration plugin) {
        this.plugin = plugin;
        this.jda = setupJDA();
        this.textChannel = setupTextChannel();
        this.webhookClient = setupWebhookClient();
        this.playersMap = loadPlayersMap();
    }



    private JDA setupJDA() {
        try {
            JDA jda = JDABuilder.createDefault(plugin.getConfig().getString("token"),
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS)
                    .enableCache(CacheFlag.MEMBER_OVERRIDES)
                    .disableCache(
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.SCHEDULED_EVENTS
                    )
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            return jda;
        } catch (InterruptedException e) {
            plugin.getLogger().severe("JDA setup failed: " + e.getMessage());
            return null;
        }
    }


    private TextChannel setupTextChannel() {
        Guild guild = jda.getGuildById(plugin.getConfig().getString("guildId"));
        if (guild != null) {
            return guild.getTextChannelById(plugin.getConfig().getString("channelId"));
        }
        return null;
    }

    private WebhookClient setupWebhookClient() {
        TextChannel channel = textChannel; // Replace with your TextChannel instance
        List<net.dv8tion.jda.api.entities.Webhook> webhooks = channel.retrieveWebhooks().complete();
        if (!webhooks.isEmpty()) {
            return WebhookClient.withUrl(webhooks.get(0).getUrl()); // Use the first webhook found (you might want to handle multiple webhooks)
        } else {
            try {
                net.dv8tion.jda.api.entities.Webhook webhook = channel.createWebhook("Your Webhook Name").complete(); // Create a webhook if none exist
                return WebhookClient.withUrl(webhook.getUrl());
            } catch (ErrorResponseException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private JsonObject loadPlayersMap() {
        File playersFile = new File(plugin.getDataFolder(), "discord.json");
        if (!playersFile.exists()) {
            plugin.getLogger().warning("discord.json file not found!");
            return new JsonObject();
        }
        try (FileReader reader = new FileReader(playersFile)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            e.printStackTrace();
            return new JsonObject();
        }
    }

    private Map<String, String> loadUserCache() {
        File userCacheFile = new File(plugin.getServer().getWorldContainer(), "usercache.json");
        Map<String, String> userCache = new HashMap<>();

        if (!userCacheFile.exists()) {
            plugin.getLogger().warning("usercache.json file not found!");
            return userCache;
        }

        try (FileReader reader = new FileReader(userCacheFile)) {
            JsonArray userCacheArray = JsonParser.parseReader(reader).getAsJsonArray();

            for (int i = 0; i < userCacheArray.size(); i++) {
                JsonObject userCacheEntry = userCacheArray.get(i).getAsJsonObject();
                String uuid = userCacheEntry.get("uuid").getAsString();
                String name = userCacheEntry.get("name").getAsString();
                userCache.put(uuid, name);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return userCache;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.getGuild().getId().equals(plugin.getConfig().getString("guildId"))) {
            return; // Ignore messages not from the specified guild
        }

        if (!event.getChannel().getId().equals(plugin.getConfig().getString("channelId"))) {
            return; // Ignore messages not from the specified channel
        }

        if(event.getAuthor().isBot()) {
            return;
        }

        User author = event.getAuthor();
        String discordId = author.getId();
        String playerId = null;
        for (String key : playersMap.keySet()) {
            if (playersMap.get(key).getAsString().equals(discordId)) {
                playerId = key;
                break;
            }
        }


        if(playerId != null) {
            String messageContent = event.getMessage().getContentDisplay();
            Component message;

            // Send message to Minecraft
            String minecraftName = getMinecraftName(playerId);
            if (minecraftName != null) {

                Component namePrefix = Component.text(String.format("<%s> ", minecraftName), NamedTextColor.BLUE).hoverEvent(HoverEvent.showText(Component.text(event.getMember().getEffectiveName())));

                if (!event.getMessage().getAttachments().isEmpty()) {
                    for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                        String fileUrl = attachment.getUrl();
                        String fileName = attachment.getFileName();
                        Component messageToSend = namePrefix
                                .append(Component.text("[" + fileName + "]", NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.openUrl(fileUrl))
                                        .hoverEvent(HoverEvent.showText(Component.text("Open in your browser"))));
                        plugin.getServer().broadcast(messageToSend);
                    }
                }
                if(messageContent.isEmpty()) return;
                message = namePrefix.append(Component.text(messageContent, NamedTextColor.WHITE));
                plugin.getServer().broadcast(message);
            } else {
                plugin.getLogger().warning("Failed to fetch Minecraft name for player with Discord ID: " + playerId);
            }
        }
    }

    public String getMinecraftName(String playerId) {
        Member member = getDiscordMemberFromUUID(playerId);
        if (member != null) {
            // Fetch Minecraft username from usercache.json
            return fetchMinecraftNameFromCache(playerId);
        }
        return member.getEffectiveName();
    }

    public Member getDiscordMemberFromUUID(String playerId) {
        if (playersMap.has(playerId)) {
            String discordId = playersMap.get(playerId).getAsString();
            try {
                User user = jda.retrieveUserById(discordId).complete();
                Guild guild = jda.getGuildById(Objects.requireNonNull(plugin.getConfig().getString("guildId")));
                return (guild != null) ? guild.retrieveMember(user).complete() : null;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch Discord member for UUID: " + playerId + ". Error: " + e.getMessage());
            }
        }
        return null;
    }

    public String getDiscordIdFromUUID(String playerId) {
        if (playersMap.has(playerId)) {
            return playersMap.get(playerId).getAsString();
        } else return null;
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


    private String fetchMinecraftNameFromCache(String uuid) {
        Map<String, String> userCache = loadUserCache();
        return userCache.get(uuid);
    }

    public void sendMessageToWebhook(String message, String username, String avatarUrl) {
        if (webhookClient != null) {

            WebhookMessageBuilder builder = new WebhookMessageBuilder()
                    .setAllowedMentions(AllowedMentions.none())
                    .setUsername(username)
                    .setAvatarUrl(avatarUrl)
                    .setContent(message);

            webhookClient.send(builder.build());
        }
    }

    public synchronized void shutdown() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }

        if (webhookClient != null) {
            webhookClient.close();
        }
    }
}
