/*
 * Copyright (C) 2016-2019 Dennis Neufeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package space.npstr.wolfia.commands;

import io.prometheus.client.Summary;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jooq.exception.DataAccessException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import space.npstr.wolfia.App;
import space.npstr.wolfia.commands.util.HelpCommand;
import space.npstr.wolfia.commands.util.InviteCommand;
import space.npstr.wolfia.config.properties.WolfiaConfig;
import space.npstr.wolfia.domain.game.GameRegistry;
import space.npstr.wolfia.events.WolfiaGuildListener;
import space.npstr.wolfia.game.Game;
import space.npstr.wolfia.game.exceptions.IllegalGameStateException;
import space.npstr.wolfia.system.metrics.MetricsRegistry;
import space.npstr.wolfia.utils.UserFriendlyException;
import space.npstr.wolfia.utils.discord.RestActions;
import space.npstr.wolfia.utils.discord.TextchatUtils;

import static io.prometheus.client.Summary.Timer;

/**
 * Created by napster on 12.05.17.
 * <p>
 * Some architectural notes:
 * Issued commands will always go through here. It is their own job to find out for which game they have been issued,
 * and make the appropriate calls or handle any user errors
 */
@Component
public class CommandHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CommandHandler.class);

    private final GameRegistry gameRegistry;
    private final CommandContextParser commandContextParser;
    private final CommRegistry commRegistry;

    public CommandHandler(GameRegistry gameRegistry, CommandContextParser commandContextParser, CommRegistry commRegistry) {
        this.gameRegistry = gameRegistry;
        this.commandContextParser = commandContextParser;
        this.commRegistry = commRegistry;
    }

    @EventListener
    public void onMessageReceived(@Nonnull final MessageReceivedEvent event) {
        Timer received = MetricsRegistry.commandRetentionTime.startTimer();
        //ignore bot accounts generally
        if (event.getAuthor().isBot()) {
            return;
        }

        //ignore channels where we don't have sending permissions, with a special exception for the help command
        if (event.isFromType(ChannelType.TEXT) && !event.getTextChannel().canTalk()
                && !event.getMessage().getContentRaw().toLowerCase().startsWith((WolfiaConfig.DEFAULT_PREFIX + HelpCommand.TRIGGER).toLowerCase())) {
            return;
        }

        //update user stats
        final Game g = this.gameRegistry.get(event.getChannel().getIdLong());
        if (g != null) g.userPosted(event.getMessage());


        final CommandContext context = this.commandContextParser.parse(this.commRegistry, event);

        if (context == null) {
            return;
        }

        //filter for _special_ ppl in the Wolfia guild
        final GuildCommandContext guildContext = context.requireGuild(false);
        if (guildContext != null && guildContext.guild.getIdLong() == App.WOLFIA_LOUNGE_ID) {
            final Category parent = guildContext.getTextChannel().getParent();
            //noinspection StatementWithEmptyBody
            if (guildContext.getTextChannel().getIdLong() == WolfiaGuildListener.SPAM_CHANNEL_ID //spam channel is k
                    || (parent != null && parent.getIdLong() == WolfiaGuildListener.GAME_CATEGORY_ID) //game channels are k
                    || context.invoker.getIdLong() == App.OWNER_ID) { //owner is k
                //allowed
            } else {
                context.replyWithMention("read the **rules** in <#" + WolfiaGuildListener.RULES_CHANNEL_ID + ">.",
                        message -> RestActions.restService.schedule(
                                () -> RestActions.deleteMessage(message), 5, TimeUnit.SECONDS)
                );
                RestActions.restService.schedule(context::deleteMessage, 5, TimeUnit.SECONDS);
                return;
            }
        }

        handleCommand(context, received);
    }

    /**
     * @param context
     *         the parsed input of a user
     */
    private void handleCommand(@Nonnull final CommandContext context, Timer received) {
        try {
            boolean canCallCommand = context.command instanceof PublicCommand || context.isOwner();
            if (!canCallCommand) {
                //not the bot owner
                log.info("user {}, channel {}, attempted issuing owner restricted command: {}",
                        context.invoker, context.channel, context.msg.getContentRaw());
                return;
            }
            log.info("user {}, channel {}, command {} about to be executed",
                    context.invoker, context.channel, context.msg.getContentRaw());

            received.observeDuration();//retention
            try (Summary.Timer ignored = MetricsRegistry.commandProcessTime.labels(context.command.getClass().getSimpleName()).startTimer()) {
                context.command.execute(context);
            }
        } catch (final UserFriendlyException e) {
            context.reply("There was a problem executing your command:\n" + e.getMessage());
        } catch (final IllegalGameStateException e) {
            context.reply(e.getMessage());
        } catch (final DataAccessException e) { //currently unreachable since db access is async, so a CompletionException would be thrown instead
            log.error("Db blew up while handling command", e);
            context.reply("The database is not available currently. Please try again later. Sorry for the inconvenience!");
        } catch (final Exception e) {
            try {
                final MessageReceivedEvent ev = context.event;
                Throwable t = e;
                while (t != null) {
                    String inviteLink = "";
                    try {
                        if (ev.isFromType(ChannelType.TEXT)) {
                            TextChannel tc = ev.getTextChannel();
                            inviteLink = TextchatUtils.getOrCreateInviteLinkForGuild(tc.getGuild(), tc);
                        } else {
                            inviteLink = "PRIVATE";
                        }
                    } catch (final Exception ex) {
                        log.error("Exception during exception handling of command creating an invite", ex);
                    }
                    String guild = ev.isFromGuild() ? ev.getGuild().toString() : "not a guild";
                    log.error("Exception `{}` while handling a command in guild {}, channel {}, user {}, invite {}",
                            t.getMessage(), guild, ev.getChannel().getIdLong(),
                            ev.getAuthor().getIdLong(), inviteLink, t);
                    t = t.getCause();
                }
                RestActions.sendMessage(ev.getChannel(),
                        String.format("%s, an internal exception happened while executing your command:"
                                        + "\n`%s`"
                                        + "\nSorry about that. The issue has been logged and will hopefully be fixed soon."
                                        + "\nIf you want to help solve this as fast as possible, please join our support guild."
                                        + "\nSay `%s` to receive an invite.",
                                ev.getAuthor().getAsMention(), context.msg.getContentRaw(), WolfiaConfig.DEFAULT_PREFIX + InviteCommand.TRIGGER));
            } catch (final Exception ex) {
                log.error("Exception during exception handling of command", ex);
                log.error("Original exception", e);
            }
        }
    }
}
