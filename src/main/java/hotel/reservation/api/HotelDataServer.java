package hotel.reservation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import hotel.reservation.data.model.Hotel;
import hotel.reservation.data.repository.HotelRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lightweight REST API server for hotel data using Javalin.
 *
 * <p>Wraps {@link HotelRepository} and exposes hotel data over HTTP.
 *
 * <h2>Endpoints</h2>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/hotels</td><td>List all hotels</td></tr>
 *   <tr><td>GET</td><td>/api/hotels/{id}</td><td>Get hotel by ID</td></tr>
 *   <tr><td>GET</td><td>/api/hotels/search?city=X&amp;minRank=Y&amp;maxPrice=Z</td><td>Filtered search</td></tr>
 *   <tr><td>GET</td><td>/api/cities</td><td>List available cities</td></tr>
 * </table>
 */
public class HotelDataServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelDataServer.class);

    private final int port;
    private final HotelRepository repository;
    private Javalin app;

    public HotelDataServer(int port) {
        this.port = port;
        this.repository = new HotelRepository();
    }

    /**
     * Start the HTTP server.
     */
    public void start() {
        repository.initialize();

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });

        registerRoutes();

        app.start(port);
        LOGGER.info("Hotel Data API started on port {}", port);
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        if (app != null) {
            app.stop();
            LOGGER.info("Hotel Data API stopped");
        }
    }

    private void registerRoutes() {
        // GET /api/hotels - list all hotels
        app.get("/api/hotels", this::getAllHotels);

        // GET /api/hotels/search?city=X&minRank=Y&maxPrice=Z - filtered search
        // Must be registered before /api/hotels/{id} to avoid path collision
        app.get("/api/hotels/search", this::searchHotels);

        // GET /api/hotels/{id} - single hotel by ID
        app.get("/api/hotels/{id}", this::getHotelById);

        // GET /api/cities - available cities
        app.get("/api/cities", this::getCities);
    }

    private void getAllHotels(Context ctx) {
        List<Hotel> hotels = repository.findAll();
        ctx.json(hotels);
        LOGGER.debug("GET /api/hotels -> {} hotels", hotels.size());
    }

    private void getHotelById(Context ctx) {
        String id = ctx.pathParam("id");
        Optional<Hotel> hotel = repository.findById(id);
        if (hotel.isPresent()) {
            ctx.json(hotel.get());
        } else {
            ctx.status(404).json(java.util.Map.of("error", "Hotel not found: " + id));
        }
    }

    private void searchHotels(Context ctx) {
        String city = ctx.queryParam("city");
        String minRankStr = ctx.queryParam("minRank");
        String maxPriceStr = ctx.queryParam("maxPrice");

        Integer minRank = minRankStr != null ? Integer.parseInt(minRankStr) : null;
        Double maxPrice = maxPriceStr != null ? Double.parseDouble(maxPriceStr) : null;

        List<Hotel> results = repository.search(city, minRank, maxPrice);
        ctx.json(results);
        LOGGER.debug("GET /api/hotels/search?city={}&minRank={}&maxPrice={} -> {} results",
                city, minRank, maxPrice, results.size());
    }

    private void getCities(Context ctx) {
        List<String> cities = repository.getAvailableCities();
        ctx.json(cities);
    }

    /**
     * Get the port this server is running on.
     */
    public int getPort() {
        return port;
    }
}
