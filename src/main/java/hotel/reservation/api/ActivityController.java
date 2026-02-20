package hotel.reservation.api;

import hotel.reservation.ActivityLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ActivityController {

    @GetMapping("/activity")
    public List<Map<String, Object>> getActivity(
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(defaultValue = "0") int limit) {
        var stream = ActivityLog.getEntriesSince(since).stream();
        if (limit > 0) {
            // Return only the LAST N entries (most recent)
            var all = ActivityLog.getEntriesSince(since);
            int skip = Math.max(0, all.size() - limit);
            stream = all.stream().skip(skip);
        }
        return stream
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("timestamp", e.timestamp());
                    m.put("from", e.from());
                    m.put("to", e.to());
                    m.put("type", e.type());
                    m.put("detail", e.detail());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
