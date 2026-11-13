package de.raindancer.modules.prime;

import de.raindancer.core.RainsCore;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.prime.service.MapArtService;

import java.util.function.Supplier;

/** What this module built, handed to its command. */
public record PrimeServices(Messages messages, RainsCore core, MapArtService mapArt, Supplier<PrimeSettings> settings) {
}
