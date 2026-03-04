package hotel.reservation.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint to reset the SCOP playground for re-setup.
 * Called by the frontend before sending a "setup" action to SCOP.
 */
@RestController
@RequestMapping("/api/playground")
public class PlaygroundResetController {

    private final PlaygroundAccess holder;

    public PlaygroundResetController(PlaygroundAccess holder) {
        this.holder = holder;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        holder.resetPlayground();
        return Map.of("success", true);
    }
}
