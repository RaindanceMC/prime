package de.raindancer.modules.prime.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.prime.service.MapArtService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/** Map art drawn while they were away is handed over a moment after they join. */
public final class JoinListener implements Listener {

    private final Plugin plugin;
    private final MapArtService mapArt;

    public JoinListener(Plugin plugin, MapArtService mapArt) {
        this.plugin = plugin;
        this.mapArt = mapArt;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        Scheduling.entityLater(plugin, player, 60, () -> mapArt.deliver(player));
    }
}
