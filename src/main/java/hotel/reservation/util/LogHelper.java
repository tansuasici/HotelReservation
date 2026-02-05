package hotel.reservation.util;

import org.slf4j.Logger;

/**
 * Helper class for formatted logging.
 */
public class LogHelper {

    public static void section(Logger logger, String title) {
        logger.info("┌─────────────────────────────────────────┐");
        logger.info("│ {}", String.format("%-39s", title) + " │");
        logger.info("└─────────────────────────────────────────┘");
    }

    public static void step(Logger logger, int step, String message) {
        logger.info("  [{}] {}", step, message);
    }

    public static void arrow(Logger logger, String from, String to, String message) {
        logger.info("  {} → {} : {}", from, to, message);
    }

    public static void result(Logger logger, String message) {
        logger.info("  ✓ {}", message);
    }

    public static void error(Logger logger, String message) {
        logger.error("  ✗ {}", message);
    }
}
