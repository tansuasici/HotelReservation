package hotel.reservation.agent;

import ai.scop.core.Agent;
import ai.scop.core.Conversation;
import com.tnsai.annotations.AgentSpec;
import hotel.reservation.role.DataFetcherRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Fetcher Agent - Fetches hotel and customer data from the REST API.
 *
 * <p>This agent adopts a {@link DataFetcherRole} that provides
 * {@code @Action(WEB_SERVICE)} methods for all data retrieval endpoints.
 */
@AgentSpec(description = "Data fetcher agent for hotel and customer data")
public class DataFetcherAgent extends Agent {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataFetcherAgent.class);

    private final int apiPort;

    public DataFetcherAgent(int port) {
        super("DataFetcher");
        this.apiPort = port;
    }

    @Override
    protected void setup() {
        LOGGER.info("[{}] DataFetcher Agent starting (API port: {})", getName(), apiPort);

        adopt(new DataFetcherRole(this, "HotelEnv", apiPort));
        adopt(new Conversation(this, getPlayground()));

        LOGGER.info("[{}] DataFetcher Agent ready", getName());
    }
}
