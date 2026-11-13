package de.raindancer.modules.prime.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Optional;
import java.util.function.Function;

/** The [PRIME] badge as players wear it, for messages that talk about it. */
public final class PrimeBadge {

    /**
     * Ranks' default Prime style ({@code hot-pink} in ranks.yml), letter for letter: Core hands back the glyph Ranks
     * already registered for this tag instead of making a new one.
     */
    public static final String DEFAULT_TAG = "<gradient:#ff3d9a:#ff9ad5><bold>[PRIME]</bold></gradient>";

    private PrimeBadge() {
    }

    /** @param register Core's badges ({@code core.badges()::register}); @param shown whether this server shows badges */
    public static Component of(Function<String, Optional<Component>> register, boolean shown) {
        Component text = MiniMessage.miniMessage().deserialize(DEFAULT_TAG);
        if (!shown) {
            return text;
        }
        try {
            return register.apply(DEFAULT_TAG).orElse(text);
        } catch (RuntimeException | LinkageError olderCore) {
            return text;
        }
    }

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
