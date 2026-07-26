package io.github.nucleuspowered.nucleus.modules.ban.listeners;

import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.scaffold.listener.ListenerBase;
import io.github.nucleuspowered.nucleus.services.INucleusServiceCollection;
import io.github.nucleuspowered.nucleus.services.interfaces.IMessageProviderService;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.ban.Ban;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class BannedJoinListener implements ListenerBase {

    private final IMessageProviderService messageProvider;

    @Inject
    public BannedJoinListener(INucleusServiceCollection serviceCollection) {
        this.messageProvider = serviceCollection.messageProvider();
    }

    @Listener(order = Order.EARLY)
    public void onPlayerAuth(ClientConnectionEvent.Auth event) {
        BanService bans = Sponge.getServiceManager().provideUnchecked(BanService.class);
        Optional<Ban.Profile> maybeBanData = bans.getBanFor(event.getProfile());
        if (!maybeBanData.isPresent()) return;

        Text banScreen;
        Ban.Profile ban = maybeBanData.get();
        String reason = ban.getReason().map(Text::toPlain).orElseGet(() ->
                messageProvider.getMessageString("ban.defaultreason"));
        String source = ban.getBanSource().map(Text::toPlain).orElse("???");
        boolean isPermanent = ban.isIndefinite();

        if (isPermanent && !ban.getExpirationDate().isPresent()) {
            banScreen = TextSerializers.FORMATTING_CODE.deserialize(
                    messageProvider.getMessageString("ban.banscreen.permanent", reason, source)
            );
        } else {
            Instant expires = ban.getExpirationDate().get();
            Duration remainsDur = Duration.between(Instant.now(), expires);

            // Trim insignificant time units
            if (remainsDur.compareTo(Duration.ofDays(1)) > 0) {
                remainsDur = Duration.ofDays(remainsDur.toDays());
            } else if (remainsDur.compareTo(Duration.ofHours(1)) > 0) {
                remainsDur = Duration.ofHours(remainsDur.toHours());
            } else if (remainsDur.compareTo(Duration.ofMinutes(1)) > 0) {
                remainsDur = Duration.ofMinutes(remainsDur.toMinutes());
            }

            String remainsStr = messageProvider.getTimeString(
                    messageProvider.getDefaultLocale(), remainsDur.toMillis() / 1000
            );
            banScreen = TextSerializers.FORMATTING_CODE.deserialize(
                    messageProvider.getMessageString("ban.banscreen.temporary", reason, remainsStr, source)
            );
        }

        event.setMessage(banScreen);
        event.setCancelled(true);
    }
}
