package hotel.reservation.agent;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import com.tnsai.annotations.AgentSpec;
import hotel.reservation.role.DataFetcherRole;

/**
 * Data Fetcher Agent - Fetches hotel and customer data from the REST API.
 *
 * <p>This agent adopts a {@link DataFetcherRole} that provides
 * {@code @ActionSpec(WEB_SERVICE)} methods for all data retrieval endpoints.
 */
@AgentSpec(description = "Data fetcher agent for hotel and customer data")
public class DataFetcherAgent extends Agent {

    private final int apiPort;

    public DataFetcherAgent(int port) {
        super("DataFetcher");
        this.apiPort = port;
    }

    @Override
    protected void setup() {
        getLogger().info("[{}] DataFetcher Agent starting (API port: {})", getName(), apiPort);

        adopt(new DataFetcherRole(this, "HotelEnv", apiPort));
        adopt(new Conversation(this, getPlayground()));

        getLogger().info("[{}] DataFetcher Agent ready", getName());
    }
}
