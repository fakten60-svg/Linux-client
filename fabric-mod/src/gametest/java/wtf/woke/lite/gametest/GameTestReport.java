package wtf.woke.lite.gametest;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the result of each visual check, prints it, and fails the run once
 * everything has been reported.
 *
 * <p>Printing before failing is the point: a gametest that throws on the first
 * bad check says nothing about the other three, and the reason to run this at
 * all is the line per check. The run still fails, because a test that cannot
 * fail is not one.</p>
 */
final class GameTestReport {

    private final List<String> failures = new ArrayList<>();

    /** Records and prints one check. */
    void check(String name, boolean passed, String detail) {
        System.out.println((passed ? "PASS" : "FAIL") + " " + name + " -- " + detail);
        if (!passed) {
            failures.add(name + ": " + detail);
        }
    }

    /** Records a check that could not be run at all. */
    void blocked(String name, String reason) {
        check(name, false, "could not run: " + reason);
    }

    /** @throws AssertionError listing every failed check when there was one */
    void summarise() {
        System.out.println("--- woke.wtf Lite client gametest: " + failures.size() + " failed check(s) ---");
        if (failures.isEmpty()) {
            System.out.println("ALL CHECKS PASSED");
            return;
        }
        throw new AssertionError("client gametest failures: " + failures);
    }
}
