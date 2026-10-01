package wtf.woke.lite.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Reads a gametest screenshot and counts the pixels in it.
 *
 * <p>The client gametest API can compare a screenshot against a stored template
 * image, but a template has to exist before the check does, and this mod ships
 * none. Counting pixels of a colour the test itself chose is the alternative
 * that needs no stored image: it is exact, and a failure can say how many pixels
 * were expected and how many were found.</p>
 *
 * <p>No Minecraft types, so nothing here needs a running game to be read.</p>
 */
final class PixelChecks {

    private PixelChecks() {
        throw new AssertionError("No instances of " + PixelChecks.class.getName());
    }

    /** @throws IOException when the file is missing or is not a readable image */
    static BufferedImage read(Path screenshot) throws IOException {
        BufferedImage image = ImageIO.read(screenshot.toFile());
        if (image == null) {
            throw new IOException("not a readable PNG image: " + screenshot);
        }
        return image;
    }

    /**
     * Counts the pixels of a box that match {@code argb} within {@code tolerance}
     * on every channel.
     *
     * <p>The box is half-open and is clipped to the image, so a caller that
     * guesses the window size wrong counts fewer pixels instead of throwing.</p>
     *
     * @return how many pixels matched
     */
    static int countNear(BufferedImage image, int argb, int tolerance, int x0, int y0, int x1, int y1) {
        int targetRed = (argb >> 16) & 0xFF;
        int targetGreen = (argb >> 8) & 0xFF;
        int targetBlue = argb & 0xFF;
        int right = Math.min(image.getWidth(), x1);
        int bottom = Math.min(image.getHeight(), y1);
        int matches = 0;
        for (int y = Math.max(0, y0); y < bottom; y++) {
            for (int x = Math.max(0, x0); x < right; x++) {
                int rgb = image.getRGB(x, y);
                if (Math.abs(((rgb >> 16) & 0xFF) - targetRed) <= tolerance
                        && Math.abs(((rgb >> 8) & 0xFF) - targetGreen) <= tolerance
                        && Math.abs((rgb & 0xFF) - targetBlue) <= tolerance) {
                    matches++;
                }
            }
        }
        return matches;
    }

    /**
     * Counts the pixels of a box that are brighter than {@code minChannel} on
     * every channel.
     *
     * <p>Used where an exact colour cannot be relied on: the chat hud draws its
     * text at partial opacity over the world, so what is literally on screen for
     * a white line is a blended grey rather than white. "Clearly brighter than
     * the dark plate behind it" survives that, and still counts every glyph of a
     * line and nothing else on it.</p>
     *
     * @return how many pixels were that bright
     */
    static int countBrighterThan(BufferedImage image, int minChannel, int x0, int y0, int x1, int y1) {
        int right = Math.min(image.getWidth(), x1);
        int bottom = Math.min(image.getHeight(), y1);
        int matches = 0;
        for (int y = Math.max(0, y0); y < bottom; y++) {
            for (int x = Math.max(0, x0); x < right; x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) >= minChannel && ((rgb >> 8) & 0xFF) >= minChannel
                        && (rgb & 0xFF) >= minChannel) {
                    matches++;
                }
            }
        }
        return matches;
    }

    /** @return the colour as {@code #AARRGGBB}, for a readable failure message */
    static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
