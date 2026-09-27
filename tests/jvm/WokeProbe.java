import net.minecraft.class_310;

/**
 * Native-layer verification probe: loads libwoke.so through the JVM
 * (triggering JNI_OnLoad + the constructor bootstrap) with a live stub
 * class_310 world on the classpath, then gives the native init chain time
 * to resolve and log real intermediary handles.
 *
 * Evidence lands in stdout + logs/latest.log:
 *   class  minecraft_client -> net/minecraft/class_310 @ 0x...
 *   method minecraft_client.getInstance -> method_1551 @ 0x...
 *   field  minecraft_client.player -> field_1724 @ 0x...
 *   game context: client=0x... player=0x... world=0x...
 */
public class WokeProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("[probe] loading libwoke.so via System.load");
        System.load(System.getProperty("user.dir") + "/" + args[0]);
        System.out.println("[probe] loaded — waiting for native init chain");

        Thread.sleep(2500);

        class_310 client = class_310.method_1551();
        System.out.println("[probe] client instance  = " + client);
        System.out.println("[probe] player instance  = " + client.field_1724);
        System.out.println("[probe] world  instance  = " + client.field_1687);
        System.out.println("[probe] done");
    }
}
