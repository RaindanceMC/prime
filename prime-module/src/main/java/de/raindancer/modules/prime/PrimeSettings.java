package de.raindancer.modules.prime;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

/** What an owner decides about Prime: where to buy it, and the map art. */
@Settings(id = "prime", topics = {
        @Topic(path = "prime", title = "Prime", icon = Material.NETHER_STAR),
        @Topic(path = "prime/mapart", title = "Map art", icon = Material.FILLED_MAP),
})
public record PrimeSettings(

        @In("prime") @Title("Store link")
        @Describe("Where /prime sends somebody who wants Prime — the Tebex store's address. Empty: no link is shown.")
        @Key("store-url")
        String storeUrl,

        @In("prime/mapart") @Title("Map art requests")
        @Describe("Whether Prime members may ask for an image as map art with /prime mapart. Staff approve every one.")
        @Key("mapart.enabled")
        boolean mapartEnabled,

        @In("prime/mapart") @Title("Requests per month") @Range(min = 0, max = 10)
        @Describe("How many map arts one Prime member may ask for each month. A refused one does not count.")
        @Key("mapart.per-month")
        int mapartPerMonth,

        @In("prime/mapart") @Title("Most maps per image") @Range(min = 1, max = 64)
        @Describe("How many maps one image may cover, width times height — 16 is up to 4×4.")
        @Key("mapart.most-maps")
        int mapartMostMaps,

        @In("prime/mapart") @Title("Largest image (MB)") @Range(min = 1, max = 32)
        @Describe("A link to anything larger is refused before it is read.")
        @Key("mapart.most-megabytes")
        int mapartMostMegabytes) {

    public static final PrimeSettings DEFAULTS = new PrimeSettings("", true, 1, 16, 8);

    public PrimeSettings {
        storeUrl = storeUrl == null ? "" : storeUrl.strip();
    }
}
