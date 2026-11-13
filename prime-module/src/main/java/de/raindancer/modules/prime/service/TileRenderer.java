package de.raindancer.modules.prime.service;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;

/** Draws one tile of a map art once; the client keeps it after that. Re-attached after every restart. */
public final class TileRenderer extends MapRenderer {

    private final BufferedImage tile;
    private volatile boolean drawn;

    public TileRenderer(BufferedImage tile) {
        super(false);
        this.tile = tile;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (drawn) {
            return;
        }
        canvas.drawImage(0, 0, tile);
        drawn = true;
    }
}
