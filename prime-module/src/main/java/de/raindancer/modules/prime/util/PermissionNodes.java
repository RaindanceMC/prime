package de.raindancer.modules.prime.util;

import org.bukkit.Server;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/** What this module asks about somebody. Registered in code, since a hosted module has no descriptor. */
public final class PermissionNodes {

    /** Asking for map art — every Prime member has it through rains.prime. */
    public static final String MAPART = "rainsprime.mapart";
    /** Seeing requests, approving and refusing them. */
    public static final String MAPART_REVIEW = "rainsprime.mapart.review";

    private PermissionNodes() {
    }

    public static List<Permission> declared() {
        return List.of(
                new Permission(MAPART, "Ask for an image as map art (every Prime member has it)", PermissionDefault.OP),
                new Permission(MAPART_REVIEW, "See, approve and refuse map art requests", PermissionDefault.OP));
    }

    /** Registers whatever is not registered yet, and gives map art to Prime. @return how many were added */
    public static int register(Server server) {
        if (server == null) {
            return 0;
        }
        int added = 0;
        for (Permission permission : declared()) {
            if (server.getPluginManager().getPermission(permission.getName()) != null) {
                continue;
            }
            try {
                server.getPluginManager().addPermission(permission);
                added++;
            } catch (IllegalArgumentException alreadyThere) {
                // Registered by another copy between the check and the add. Nothing to do.
            }
        }
        Permission prime = de.raindancer.core.social.support.PrimePermission.permission(server.getPluginManager());
        prime.getChildren().put(MAPART, true);
        prime.recalculatePermissibles();
        return added;
    }
}
