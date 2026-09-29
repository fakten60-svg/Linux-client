package wtf.woke.lite.waypoint;

import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * Names the world and dimension the player is standing in.
 *
 * <p>A waypoint belongs to the place it was made, so every one is filed under a
 * key like {@code sp:world@minecraft:overworld} or
 * {@code mp:play.example.net@minecraft:the_nether}. The multiplayer address is
 * part of the key on purpose: two servers both called {@code world} must not
 * share waypoints.</p>
 *
 * <p>This is the only place that reads that from the client, which keeps the
 * editing rules free of Minecraft types.</p>
 */
public final class WaypointWorldKey {

    private WaypointWorldKey() {
        throw new AssertionError("No instances of " + WaypointWorldKey.class.getName());
    }

    /**
     * @return the key of the world the player is in, or empty when no world is
     *         loaded yet
     */
    public static Optional<String> current() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return Optional.empty();
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null && server.address != null && !server.address.isBlank()) {
            return Optional.of("mp:" + server.address + "@" + dimension);
        }
        IntegratedServer integrated = client.getServer();
        if (integrated != null && integrated.getSaveProperties() != null) {
            return Optional.of("sp:" + integrated.getSaveProperties().getLevelName() + "@" + dimension);
        }
        return Optional.of("local@" + dimension);
    }
}
