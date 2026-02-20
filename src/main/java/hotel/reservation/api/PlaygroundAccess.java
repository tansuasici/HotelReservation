package hotel.reservation.api;

import ai.scop.core.Playground;
import ai.scop.ui.command.impl.exec.web_service.Controller;
import hotel.reservation.HotelReservationPlayground;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * Minimal bridge to read the SCOP Playground instance via reflection.
 * All lifecycle actions (setup/run/pause/stop) are handled by SCOP's
 * own Controller at /api/scop/playground.
 */
@Component
public class PlaygroundAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaygroundAccess.class);

    @Autowired
    private Controller scopController;

    /**
     * Get the playground instance from SCOP Controller via reflection.
     */
    public HotelReservationPlayground get() {
        try {
            Field f = Controller.class.getDeclaredField("playground");
            f.setAccessible(true);
            Playground pg = (Playground) f.get(scopController);
            if (pg instanceof HotelReservationPlayground hrp) {
                return hrp;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read playground from SCOP Controller: {}", e.getMessage());
        }
        return null;
    }

    public boolean isActive() {
        return get() != null;
    }
}
