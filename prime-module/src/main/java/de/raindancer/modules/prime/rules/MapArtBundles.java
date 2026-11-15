package de.raindancer.modules.prime.rules;

import java.util.ArrayList;
import java.util.List;

/** How a map art's maps are handed over: in bundles, tile order kept. */
public final class MapArtBundles {

    /** A map stacks to 64, so it fills a sixty-fourth of a bundle. */
    public static final int PER_BUNDLE = 64;

    private MapArtBundles() {
    }

    /** @return the tile indexes for each bundle; one bundle unless there are more than {@value #PER_BUNDLE} maps */
    public static List<List<Integer>> pack(int maps) {
        List<List<Integer>> bundles = new ArrayList<>();
        for (int first = 0; first < maps; first += PER_BUNDLE) {
            List<Integer> bundle = new ArrayList<>();
            for (int index = first; index < Math.min(maps, first + PER_BUNDLE); index++) {
                bundle.add(index);
            }
            bundles.add(List.copyOf(bundle));
        }
        return List.copyOf(bundles);
    }
}
