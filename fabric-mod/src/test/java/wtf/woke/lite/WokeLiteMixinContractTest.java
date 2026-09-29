package wtf.woke.lite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Checks that every method the mixins inject into still exists in the game.
 *
 * <p>A mixin whose target has moved does not fail loudly: the class simply
 * never receives the injection, and the feature it carries is quietly absent
 * until somebody notices in game. These assertions turn that into a build
 * failure, which is the only cheap place to catch a Minecraft or mapping bump.</p>
 *
 * <p>The targets below mirror the injection points declared in
 * <code>wtf.woke.lite.mixin.client</code>. An injection moved to another method
 * has to be moved here as well, and the failure message says so.</p>
 */
class WokeLiteMixinContractTest {

    @Test
    void theScreenshotHookTargetsTheRecordersSaveCall() throws Exception {
        Method target = requireMethod("net.minecraft.client.util.ScreenshotRecorder", "saveScreenshot",
                "(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;"
                        + "ILjava/util/function/Consumer;)V");

        assertTrue(Modifier.isStatic(target.getModifiers()),
                "the hook is a static @ModifyVariable and needs a static target");
        assertEquals("java.util.function.Consumer", target.getParameterTypes()[4].getName(),
                "the message receiver the hook wraps is the fifth argument, index 4");
        assertEquals("void", target.getReturnType().getName());
    }

    @Test
    void theChatHooksTargetTheHudsAddMethods() throws Exception {
        Method timestamped = requireMethod("net.minecraft.client.gui.hud.ChatHud", "addMessage",
                "(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;"
                        + "Lnet/minecraft/client/gui/hud/MessageIndicator;)V");

        // The mixin names the message's local slot, and this is an instance
        // method: 'this' holds slot 0, so the first parameter is slot 1 while it
        // is still parameter 0 here. Getting that off by one is what would make
        // the timestamp land on the signature instead of the message.
        assertFalse(Modifier.isStatic(timestamped.getModifiers()), "instance method, as assumed above");
        assertEquals("net.minecraft.text.Text", timestamped.getParameterTypes()[0].getName());

        requireMethod("net.minecraft.client.gui.hud.ChatHud", "addMessage",
                "(Lnet/minecraft/client/gui/hud/ChatHudLine;)V");
        requireMethod("net.minecraft.client.gui.hud.ChatHud", "addVisibleMessage",
                "(Lnet/minecraft/client/gui/hud/ChatHudLine;)V");
    }

    @Test
    void theBookHookTargetsTheScreensRender() throws Exception {
        requireMethod("net.minecraft.client.gui.screen.ingame.BookEditScreen", "render",
                "(Lnet/minecraft/client/gui/DrawContext;IIF)V");
    }

    private static Method requireMethod(String className, String methodName, String descriptor) {
        Class<?> owner;
        try {
            owner = Class.forName(className);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(className + " is gone; the mixin targeting it no longer applies", missing);
        }
        Method found = Arrays.stream(owner.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> descriptorOf(method).equals(descriptor))
                .findFirst()
                .orElse(null);
        if (found == null) {
            throw new AssertionError("no " + className + "." + methodName + descriptor
                    + " any more; the mixin injecting into it does nothing until this is fixed");
        }
        return found;
    }

    private static String descriptorOf(Method method) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            descriptor.append(descriptorOf(parameter));
        }
        return descriptor.append(')').append(descriptorOf(method.getReturnType())).toString();
    }

    private static String descriptorOf(Class<?> type) {
        if (type == void.class) {
            return "V";
        }
        if (type == int.class) {
            return "I";
        }
        if (type == boolean.class) {
            return "Z";
        }
        if (type == long.class) {
            return "J";
        }
        if (type == double.class) {
            return "D";
        }
        if (type == float.class) {
            return "F";
        }
        if (type == short.class) {
            return "S";
        }
        if (type == byte.class) {
            return "B";
        }
        if (type == char.class) {
            return "C";
        }
        if (type.isArray()) {
            return "[" + descriptorOf(type.getComponentType());
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
