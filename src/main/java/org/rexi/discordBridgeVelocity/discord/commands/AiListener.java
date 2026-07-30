package org.rexi.discordBridgeVelocity.discord.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.rexi.discordBridgeVelocity.DiscordBridgeVelocity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AiListener extends ListenerAdapter {

    private final DiscordBridgeVelocity plugin;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public AiListener(DiscordBridgeVelocity plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "AiListener-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    // ---------- Comando /ai (o /help) ----------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        List<String> commands = plugin.getConfig("AI.commands", List.of("ai", "help"));
        if (!commands.contains(event.getName())) return;

        if (!plugin.getConfig("AI.enabled", false)) {
            event.reply(plugin.getConfig("discord_messages.feature_disabled", "❌ This feature is disabled"))
                    .setEphemeral(true).queue();
            return;
        }

        boolean isDm = !event.isFromGuild();
        boolean allowDms = plugin.getConfig("AI.allow_dms", true);

        if (isDm) {
            if (!allowDms) {
                event.reply(plugin.getConfig("discord_messages.only_server", "❌ This command can only be used on a server.")).setEphemeral(true).queue();
                return;
            }
        }

        OptionMapping option = event.getOption("mensaje");
        if (option == null) {
            event.reply(plugin.getConfig("discord_messages.ai_usage", "Usage: `/"+ event.getName() +" <message>`")
                            .replace("{command}", "/"+event.getName()))
                    .setEphemeral(true).queue();
            return;
        }

        String prompt = option.getAsString();
        event.deferReply().queue();

        List<JsonObject> history = new ArrayList<>();
        history.add(userMessage(prompt));

        requestCompletion(history).whenComplete((response, error) -> {
            if (error != null) {
                plugin.logger.warn("Error consulting the AI: " + error.getMessage());
                event.getHook().editOriginal(plugin.getConfig("discord_messages.ai_error", "❌ Error trying to send your message to the AI. Please try again later.")).queue();
                return;
            }
            sendInChunks(response, text -> event.getHook().editOriginal(text).queue());
        });
    }

    // ---------- Menciones y respuestas ----------

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!plugin.getConfig("AI.enabled", false)) return;

        Message message = event.getMessage();
        long selfId = event.getJDA().getSelfUser().getIdLong();

        boolean isDm = !event.isFromGuild();
        boolean allowDms = plugin.getConfig("AI.allow_dms", true);
        boolean allowMentions = plugin.getConfig("AI.allow_mentions", true);

        boolean mentioned = allowMentions && message.getMentions().isMentioned(event.getJDA().getSelfUser());
        boolean isReplyToBot = isReplyToSelf(message, selfId);

        if (isDm) {
            if (!allowDms) {
                plugin.getJDA().retrieveUserById(event.getAuthor().getId())
                        .flatMap(user -> user.openPrivateChannel())
                        .flatMap(channel -> channel.sendMessage(plugin.getConfig("discord_messages.only_server", "❌ This command can only be used on a server.")))
                        .queue();
                return;
            }
        } else if (!mentioned && !isReplyToBot) {
            return;
        }

        String content = stripMention(message.getContentRaw(), selfId).trim();
        if (content.isEmpty() && !isReplyToBot) return;

        event.getChannel().sendTyping().queue();

        CompletableFuture.supplyAsync(() -> buildHistory(message, content), executor)
                .thenCompose(this::requestCompletion)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        plugin.logger.warn("Error consulting AI: " + error.getMessage());
                        message.reply(plugin.getConfig("discord_messages.ai_error", "❌ Error trying to send your message to the AI. Please try again later.")).queue();
                        return;
                    }
                    sendInChunks(response, text -> message.reply(text).queue());
                });
    }

    // ---------- Construcción del contexto ----------

    private boolean isReplyToSelf(Message message, long selfId) {
        MessageReference ref = message.getMessageReference();
        if (ref == null) return false;

        Message resolved = ref.getMessage();
        if (resolved != null) return resolved.getAuthor().getIdLong() == selfId;

        try {
            Message fetched = ref.resolve().complete();
            return fetched.getAuthor().getIdLong() == selfId;
        } catch (Exception e) {
            return false;
        }
    }

    private List<JsonObject> buildHistory(Message triggerMessage, String triggerContent) {
        int maxLength = plugin.getConfig("AI.max_conversation_length", 5);
        long selfId = triggerMessage.getJDA().getSelfUser().getIdLong();

        List<Message> chain = new ArrayList<>();
        chain.add(triggerMessage);

        Message current = triggerMessage;
        int collected = 0;
        while (collected < maxLength) {
            MessageReference ref = current.getMessageReference();
            if (ref == null) break;

            Message previous = ref.getMessage();
            if (previous == null) {
                try {
                    previous = ref.resolve().complete();
                } catch (Exception e) {
                    break;
                }
            }
            if (previous == null) break;

            chain.add(previous);
            current = previous;
            collected++;
        }

        Collections.reverse(chain); // más antiguo -> más reciente

        List<JsonObject> history = new ArrayList<>();
        for (Message m : chain) {
            boolean isBot = m.getAuthor().getIdLong() == selfId;
            String text = (m == triggerMessage) ? triggerContent : stripMention(m.getContentRaw(), selfId).trim();
            if (text.isEmpty()) continue;
            history.add(isBot ? assistantMessage(text) : userMessage(text));
        }
        return history;
    }

    private String stripMention(String content, long selfId) {
        return content.replace("<@" + selfId + ">", "").replace("<@!" + selfId + ">", "");
    }

    // ---------- Llamada a la API ----------

    private JsonObject userMessage(String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "user");
        obj.addProperty("content", content);
        return obj;
    }

    private JsonObject assistantMessage(String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", "assistant");
        obj.addProperty("content", content);
        return obj;
    }

    private CompletableFuture<String> requestCompletion(List<JsonObject> history) {
        String baseUrl = plugin.getConfig("AI.base_url", "https://api.openai.com/v1");
        String apiKey = plugin.getConfig("AI.api_key", "");
        String systemContext = plugin.getConfig("AI.context", "");
        String model = plugin.getConfig("AI.model", "");
        double temperature = plugin.getConfig("AI.temperature", 0.7);
        int maxTokens = plugin.getConfig("AI.max_tokens", 500);

        JsonArray messages = new JsonArray();
        if (!systemContext.isBlank()) {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", systemContext);
            messages.add(system);
        }
        history.forEach(messages::add);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", temperature);
        body.addProperty("max_tokens", maxTokens);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new RuntimeException("La API respondió " + response.statusCode() + ": " + response.body());
                    }
                    JsonObject json = com.google.gson.JsonParser
                            .parseString(response.body())
                            .getAsJsonObject();

                    return json.getAsJsonArray("choices")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content")
                            .getAsString()
                            .trim();
                });
    }

    private void sendInChunks(String text, Consumer<String> sender) {
        int limit = 2000; // límite de Discord por mensaje
        if (text.length() <= limit) {
            sender.accept(text);
            return;
        }
        for (int i = 0; i < text.length(); i += limit) {
            sender.accept(text.substring(i, Math.min(text.length(), i + limit)));
        }
    }
}