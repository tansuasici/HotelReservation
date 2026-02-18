package hotel.reservation.api;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final PlaygroundHolder holder;

    public SimulationController(PlaygroundHolder holder) {
        this.holder = holder;
    }

    @PostMapping("/simulation")
    public Map<String, Object> simulationAction(@RequestParam String action) {
        switch (action.toLowerCase()) {
            case "setup" -> holder.setup();
            case "run"   -> holder.run();
            case "pause" -> holder.pause();
            case "stop"  -> holder.stop();
        }
        return holder.getStatusMap();
    }

    @GetMapping("/simulation/status")
    public Map<String, Object> getStatus() {
        return holder.getStatusMap();
    }
}
