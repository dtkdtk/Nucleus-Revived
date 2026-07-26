/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.ban.commands;

import io.github.nucleuspowered.nucleus.configurate.config.CommonPermissionLevelConfig;
import io.github.nucleuspowered.nucleus.modules.ban.BanPermissions;
import io.github.nucleuspowered.nucleus.modules.ban.config.BanConfig;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandContext;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandExecutor;
import io.github.nucleuspowered.nucleus.scaffold.command.ICommandResult;
import io.github.nucleuspowered.nucleus.scaffold.command.NucleusParameters;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.Command;
import io.github.nucleuspowered.nucleus.scaffold.command.annotation.EssentialsEquivalent;
import io.github.nucleuspowered.nucleus.services.INucleusServiceCollection;
import io.github.nucleuspowered.nucleus.services.interfaces.IReloadableService;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.GameProfileManager;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MutableMessageChannel;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.util.ban.Ban;
import org.spongepowered.api.util.ban.BanTypes;

import java.util.Optional;

@Command(
        aliases = "ban",
        basePermission = BanPermissions.BASE_BAN,
        commandDescriptionKey = "ban",
        associatedPermissions = {
                BanPermissions.BAN_OFFLINE,
                BanPermissions.BAN_EXEMPT_TARGET,
                BanPermissions.BAN_NOTIFY
        },
        associatedPermissionLevelKeys = {
                BanPermissions.BAN_LEVEL_KEY
        }
)
@EssentialsEquivalent("ban")
@NonnullByDefault
public class BanCommand implements ICommandExecutor<CommandSource>, IReloadableService.Reloadable {

    private CommonPermissionLevelConfig levelConfig = new CommonPermissionLevelConfig();
    private final String name = "name";

    @Override
    public CommandElement[] parameters(INucleusServiceCollection serviceCollection) {
        return new CommandElement[] {
                GenericArguments.firstParsing(
                        NucleusParameters.ONE_GAME_PROFILE_UUID.get(serviceCollection),
                        NucleusParameters.ONE_GAME_PROFILE.get(serviceCollection),
                        GenericArguments.onlyOne(GenericArguments.string(Text.of(this.name)))
                ),
                GenericArguments.optionalWeak(NucleusParameters.REASON)
        };
    }

    @Override
    public ICommandResult execute(ICommandContext<? extends CommandSource> context) throws CommandException {
        String reason = context.getOne(NucleusParameters.Keys.REASON, String.class)
                .orElseGet(() -> context.getMessageString("ban.defaultreason"));

        User user = null;

        Optional<GameProfile> gpOpt = context.getOne(NucleusParameters.Keys.USER, GameProfile.class);
        if (!gpOpt.isPresent()) {
            gpOpt = context.getOne(NucleusParameters.Keys.USER_UUID, GameProfile.class);
        }

        if (gpOpt.isPresent()) {
            UserStorageService uss = Sponge.getServiceManager().provideUnchecked(UserStorageService.class);
            Optional<User> u = uss.get(gpOpt.get().getUniqueId());
            if (u.isPresent()) {
                user = u.get();
            }
        }

        if (user == null) {
            String name = context.getOne(this.name, String.class).orElse(null);
            if (name != null && context.testPermission(BanPermissions.BAN_OFFLINE)) {
                return tryMojangBan(context, name, reason);
            }
            return context.errorResult("command.ban.usernotfound");
        }

        if (!user.isOnline() && !context.testPermission(BanPermissions.BAN_OFFLINE)) {
            return context.errorResult("command.ban.offline.noperms");
        }

        if (!context.isConsoleAndBypass() && context.testPermissionFor(user, BanPermissions.BAN_EXEMPT_TARGET)) {
            return context.errorResult("command.ban.exempt", user.getName());
        }

        return executeBan(context, user, reason);
    }

    private ICommandResult tryMojangBan(ICommandContext<? extends CommandSource> context, String userToFind, String reason) {
        Sponge.getScheduler().createAsyncExecutor(context.getServiceCollection().pluginContainer()).execute(() -> {
            try {
                GameProfileManager gpm = Sponge.getServer().getGameProfileManager();
                GameProfile gp = gpm.get(userToFind).get();

                Sponge.getScheduler().createSyncExecutor(context.getServiceCollection().pluginContainer()).execute(() -> {
                    UserStorageService uss = Sponge.getServiceManager().provideUnchecked(UserStorageService.class);
                    User user = uss.getOrCreate(gp);
                    context.sendMessage("gameprofile.new", user.getName());

                    try {
                        executeBan(context, user, reason);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                context.sendMessage("command.ban.profileerror", userToFind);
            }
        });

        return context.successResult();
    }

    private ICommandResult executeBan(ICommandContext<? extends CommandSource> context, User user, String reason) {
        BanService service = Sponge.getServiceManager().provideUnchecked(BanService.class);
        CommandSource src = context.getCommandSourceUnchecked();

        if (service.isBanned(user.getProfile())) {
            return context.errorResult("command.ban.alreadyset", user.getName());
        }

        Text banScreen = TextSerializers.FORMATTING_CODE.deserialize(
                context.getMessageString("ban.banscreen.permanent", reason, context.getName())
        );
        user.getPlayer().ifPresent(p -> p.kick(banScreen));

        Ban bp = Ban.builder()
                .type(BanTypes.PROFILE)
                .profile(user.getProfile())
                .source(src)
                .reason(Text.of(reason))
                .build();
        service.addBan(bp);

        MutableMessageChannel channel = context.getServiceCollection()
                .permissionService()
                .permissionMessageChannel(BanPermissions.BAN_NOTIFY)
                .asMutable();
        channel.addMember(src);
        channel.send(context.getMessage("command.ban.applied", user.getName(), src.getName()));
        channel.send(context.getMessage("command.reason.moderation", reason));

        return context.successResult();
    }

    @Override public void onReload(INucleusServiceCollection serviceCollection) {
        this.levelConfig = serviceCollection.moduleDataProvider().getModuleConfig(BanConfig.class).getLevelConfig();
    }
}
