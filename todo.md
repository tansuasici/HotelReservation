# Hotel Reservation Multi-Agent System - Implementation TODO

## Overview
Bu proje SCOP Framework ve TNSAI entegrasyonu kullanarak bir otel rezervasyon multi-agent sistemi gelistirecek.
**GUI kullanilmayacak** - REST API endpoint yaklaşımı ile ilerlenecek (SCOP web_service modeli gibi).

---

## Phase 1: Project Setup

### 1.1 Maven Project Structure
- [ ] Create Maven project with standard structure
- [ ] Configure `pom.xml` with SCOP dependencies:
  ```xml
  <dependencies>
      <dependency>
          <groupId>ai.scop</groupId>
          <artifactId>scop-core</artifactId>
          <version>${scop-core.version}</version>
      </dependency>
      <dependency>
          <groupId>ai.scop</groupId>
          <artifactId>scop-ui</artifactId>
          <version>${scop-ui.version}</version>
      </dependency>
      <!-- Spring Boot for REST API -->
      <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-starter-web</artifactId>
      </dependency>
  </dependencies>
  ```
- [ ] Set up GitLab Maven repositories
- [ ] Create `config.json` for simulation parameters

### 1.2 Package Structure
```
src/main/java/
  hotel/
    reservation/
      HotelReservationPlayground.java       # Main playground

      agent/
        CustomerAgent.java                  # Customer agent (CNP initiator)
        HotelAgent.java                     # Hotel agent (CNP participant)

      role/
        CustomerRole.java                   # Customer role with CNP behavior
        HotelProviderRole.java              # Hotel provider role
        WeatherRole.java                    # TNSAI Weather integration
        CurrencyRole.java                   # TNSAI Currency conversion

      df/
        DirectoryFacilitator.java           # DF Environment
        DFRole.java                         # DF interaction role
        DFEntry.java                        # DF registration entry

      message/
        RoomQuery.java                      # CFP payload
        RoomProposal.java                   # Proposal payload
        ReservationRequest.java             # Accept payload
        ReservationConfirmation.java        # Confirm payload
        MessageTypes.java                   # Message type constants

      data/                                 # Dummy Data Layer
        model/
          Hotel.java                        # Hotel entity
          Location.java                     # Location model
          Room.java                         # Room model
          ContactInfo.java                  # Contact info
        repository/
          HotelRepository.java              # In-memory hotel DB
        controller/
          HotelDataController.java          # Data API endpoints

      api/                                  # Main REST API Layer
        HotelReservationApiApplication.java # Spring Boot app
        HotelReservationController.java     # Simulation controller
        WeatherController.java              # Weather API endpoints
        dto/
          HotelDTO.java
          CustomerDTO.java
          ReservationDTO.java
          ProposalDTO.java
          WeatherDTO.java
          SimulationStatusDTO.java

      negotiation/                          # (Bonus)
        NegotiationRole.java
        CounterOffer.java

src/main/resources/
  config.json                               # Simulation config
  hotel-data.json                           # Sample hotel data
  application.properties                    # Spring Boot config
```

---

## Phase 2: Dummy Hotel Data API

### 2.1 Hotel Data Models
- [ ] Create `Hotel.java` - Main hotel entity
  ```java
  public class Hotel {
      private String id;
      private String name;
      private Location location;
      private int rank;  // 1-5 stars
      private double pricePerNight;
      private String currency;
      private List<String> amenities;
      private List<String> roomTypes;
      private double rating;
      private int reviewCount;
      private List<String> images;
      private ContactInfo contactInfo;
      private boolean available;
  }
  ```

- [ ] Create `Location.java`:
  ```java
  public class Location {
      private String city;
      private String country;
      private String district;
      private Coordinates coordinates;
  }
  ```

- [ ] Create `Room.java`:
  ```java
  public class Room {
      private String id;
      private String type;  // standard, deluxe, suite
      private double price;
      private int capacity;
      private List<String> amenities;
      private boolean available;
  }
  ```

### 2.2 Hotel Data Repository
- [ ] Create `HotelRepository.java` - In-memory hotel database
  ```java
  public class HotelRepository {
      private Map<String, Hotel> hotels = new HashMap<>();

      public void initialize() {
          // Add sample hotels
          addHotel(Hotel.builder()
              .id("h001")
              .name("Grand Istanbul Hotel")
              .location(Location.of("Istanbul", "TR", "Beyoglu"))
              .rank(5)
              .pricePerNight(450)
              .amenities(List.of("wifi", "pool", "spa", "restaurant"))
              .rating(4.7)
              .build());
          // ... more hotels
      }

      public List<Hotel> findByLocation(String city);
      public List<Hotel> findByRank(int minRank);
      public List<Hotel> search(String city, int minRank, double maxPrice);
      public Optional<Hotel> findById(String id);
  }
  ```

### 2.3 Hotel API Endpoints
- [ ] Create `HotelDataController.java`:
  ```java
  @RestController
  @RequestMapping("/api/data/hotels")
  public class HotelDataController {

      @GetMapping
      public List<HotelDTO> getAllHotels();

      @GetMapping("/{id}")
      public HotelDTO getHotel(@PathVariable String id);

      @GetMapping("/search")
      public List<HotelDTO> searchHotels(
          @RequestParam String city,
          @RequestParam(required = false) Integer minRank,
          @RequestParam(required = false) Double maxPrice
      );

      @GetMapping("/{id}/rooms")
      public List<RoomDTO> getHotelRooms(@PathVariable String id);

      @GetMapping("/{id}/amenities")
      public List<String> getHotelAmenities(@PathVariable String id);

      @GetMapping("/cities")
      public List<String> getAvailableCities();
  }
  ```

### 2.4 Sample Hotel Data
- [ ] Create `hotel-data.json` with sample hotels:
  ```json
  {
    "hotels": [
      {
        "id": "h001",
        "name": "Grand Istanbul Hotel",
        "city": "Istanbul",
        "country": "TR",
        "rank": 5,
        "pricePerNight": 450,
        "amenities": ["wifi", "pool", "spa", "restaurant", "gym", "parking"],
        "rating": 4.7
      },
      {
        "id": "h002",
        "name": "Luxury Palace Istanbul",
        "city": "Istanbul",
        "country": "TR",
        "rank": 5,
        "pricePerNight": 400,
        "amenities": ["wifi", "pool", "spa", "restaurant"],
        "rating": 4.5
      },
      {
        "id": "h003",
        "name": "Budget Inn Istanbul",
        "city": "Istanbul",
        "country": "TR",
        "rank": 3,
        "pricePerNight": 150,
        "amenities": ["wifi", "breakfast"],
        "rating": 4.0
      },
      {
        "id": "h004",
        "name": "Sea View Resort",
        "city": "Izmir",
        "country": "TR",
        "rank": 4,
        "pricePerNight": 300,
        "amenities": ["wifi", "pool", "beach", "restaurant"],
        "rating": 4.6
      },
      {
        "id": "h005",
        "name": "Ankara Business Hotel",
        "city": "Ankara",
        "country": "TR",
        "rank": 4,
        "pricePerNight": 250,
        "amenities": ["wifi", "conference", "restaurant", "gym"],
        "rating": 4.3
      },
      {
        "id": "h006",
        "name": "Cappadocia Cave Hotel",
        "city": "Nevsehir",
        "country": "TR",
        "rank": 5,
        "pricePerNight": 350,
        "amenities": ["wifi", "spa", "restaurant", "terrace", "balloon_view"],
        "rating": 4.9
      },
      {
        "id": "h007",
        "name": "Antalya Beach Resort",
        "city": "Antalya",
        "country": "TR",
        "rank": 5,
        "pricePerNight": 500,
        "amenities": ["wifi", "pool", "beach", "spa", "all_inclusive"],
        "rating": 4.8
      }
    ]
  }
  ```

---

## Phase 3: Message Payload Models

### 2.1 Data Classes
- [ ] Create `RoomQuery.java` - CFP message payload
  - location (String)
  - minRank (int)
  - maxPrice (double)

- [ ] Create `RoomProposal.java` - Proposal message payload
  - hotelName (String)
  - location (String)
  - rank (int)
  - price (double)
  - roomType (String)
  - timestamp (long) - for FCFS ordering

- [ ] Create `ReservationRequest.java` - Accept message payload
  - customerId (String)
  - proposalId (String)
  - checkInDate (Date)
  - checkOutDate (Date)

- [ ] Create `ReservationConfirmation.java` - Confirm message payload
  - confirmationNumber (String)
  - hotelName (String)
  - totalPrice (double)

---

## Phase 3: Directory Facilitator

### 3.1 DF Environment
- [ ] Create `DirectoryFacilitator.java` extending `Environment`
  - Maintains registry of hotel agents
  - Provides search functionality

- [ ] Create `DFEntry.java` - Registration entry
  - agentIdentifier (Identifier)
  - serviceType (String)
  - location (String)
  - rank (int)
  - registrationTime (long)

### 3.2 DF Role
- [ ] Create `DFRole.java` extending `Role`
  - `register(DFEntry entry)` - Register an agent
  - `deregister(Identifier agentId)` - Remove registration
  - `search(String location, int minRank)` - Search for hotels
  - Handle messages:
    - `handleRegisterMessage(Message<DFEntry>)`
    - `handleSearchMessage(Message<RoomQuery>)`
    - `handleSearchResultMessage(Message<List<Identifier>>)`

---

## Phase 4: Hotel Agent Implementation

### 4.1 HotelAgent Class
- [ ] Create `HotelAgent.java` extending `Agent`
  - Properties: hotelName, location, rank, basePrice
  - Constructor with initialization parameters
  - `setup()`: Adopt HotelProviderRole, register with DF

### 4.2 HotelProviderRole
- [ ] Create `HotelProviderRole.java` extending `Role`
  - Environment: Main Environment
  - Properties: availability (boolean), responseRate (double)

- [ ] Implement message handlers:
  - `handleCFPMessage(Message<RoomQuery>)`:
    - Check if query matches (location, rank)
    - Random decision to respond (simulating real behavior)
    - If responding: create and send Proposal
    - If not: send Refuse or simply don't respond

  - `handleAcceptMessage(Message<ReservationRequest>)`:
    - Process reservation
    - Send Confirm message

  - `handleRejectMessage(Message<?>)`:
    - Log rejection, clean up

---

## Phase 5: Customer Agent Implementation

### 5.1 CustomerAgent Class
- [ ] Create `CustomerAgent.java` extending `Agent`
  - Properties: desiredLocation, desiredRank, maxPrice, desiredPrice
  - Constructor with search criteria
  - `setup()`: Adopt CustomerRole

### 5.2 CustomerRole
- [ ] Create `CustomerRole.java` extending `Role`
  - State management: SEARCHING, WAITING_PROPOSALS, DECIDING, NEGOTIATING, COMPLETED

- [ ] Implement Contract Net Protocol Initiator:
  - `startSearch()`:
    - Query DF for hotel agents
    - Broadcast CFP to all discovered hotels
    - Start deadline timer (30 seconds / configurable ticks)

  - `handleSearchResultMessage(Message<List<Identifier>>)`:
    - Receive list of hotel agents from DF
    - Send CFP to each hotel

  - `handleProposalMessage(Message<RoomProposal>)`:
    - Collect proposal
    - Store with timestamp for FCFS

  - `handleRefuseMessage(Message<?>)`:
    - Note refusal, continue waiting

  - `evaluateProposals()`:
    - Called when deadline expires or all responses received
    - If no proposals: fail
    - If one proposal: accept
    - If multiple: select lowest price (FCFS for ties)

  - `makeReservation(RoomProposal selected)`:
    - Send Accept to selected hotel
    - Send Reject to others

  - `handleConfirmMessage(Message<ReservationConfirmation>)`:
    - Log success
    - Set state to COMPLETED

---

## Phase 6: Playground Setup

### 6.1 HotelReservationPlayground
- [ ] Create `HotelReservationPlayground.java` extending `Playground`

- [ ] Implement `setup()`:
  ```java
  @Override
  protected void setup() {
      // Create DF Environment
      DirectoryFacilitator df = create(new DirectoryFacilitator("DF"));

      // Create Hotel Agents with different properties
      create(new HotelAgent("Grand Hotel", "Istanbul", 5, 450));
      create(new HotelAgent("Luxury Palace", "Istanbul", 5, 400));
      create(new HotelAgent("Budget Inn", "Istanbul", 3, 150));
      create(new HotelAgent("Sea View Resort", "Izmir", 4, 300));
      create(new HotelAgent("Mountain Lodge", "Ankara", 3, 200));

      // Create Customer Agent
      create(new CustomerAgent("Customer-1", "Istanbul", 5, 500));
  }
  ```

### 6.2 Configuration
- [ ] Create `config.json`:
  ```json
  {
    "run": {
      "PLAYGROUND_NAME": "hotel.reservation.HotelReservationPlayground",
      "TIMEOUT_TICK": "200",
      "STEP_DELAY": "100",
      "NUMBER_OF_EPISODES": "1"
    },
    "cnp": {
      "PROPOSAL_DEADLINE_TICKS": "30",
      "MAX_NEGOTIATION_ROUNDS": "5"
    }
  }
  ```

---

## Phase 7: REST API Layer (Endpoint Yaklaşımı)

### 7.1 Spring Boot Application
- [ ] Create `HotelReservationApiApplication.java`:
  ```java
  @SpringBootApplication
  public class HotelReservationApiApplication {
      public static void main(String[] args) {
          SpringApplication.run(HotelReservationApiApplication.class, args);
      }
  }
  ```

### 7.2 REST Controller
- [ ] Create `HotelReservationController.java`:
  ```java
  @RestController
  @RequestMapping("/api/hotel")
  @CrossOrigin(origins = "*")
  public class HotelReservationController {

      private Playground playground = null;

      // Simulation Control Endpoints
      @PostMapping("/simulation")
      // action: setup, run, pause, stop

      @GetMapping("/simulation/status")
      // Get current simulation state

      // Hotel Management Endpoints
      @GetMapping("/hotels")
      // List all registered hotels

      @GetMapping("/hotels/{name}")
      // Get specific hotel info

      @PostMapping("/hotels")
      // Add new hotel at runtime

      // Customer Endpoints
      @PostMapping("/search")
      // Trigger search for hotels
      // Body: { location, rank, maxPrice }

      @GetMapping("/proposals")
      // Get current proposals for a customer

      @PostMapping("/reserve")
      // Make reservation
      // Body: { customerId, hotelName }

      // Reservation Endpoints
      @GetMapping("/reservations")
      // List all reservations

      @GetMapping("/reservations/{id}")
      // Get specific reservation

      // DF Endpoints
      @GetMapping("/df/entries")
      // List all DF registrations

      @GetMapping("/df/search")
      // Search DF: ?location=Istanbul&minRank=4
  }
  ```

### 7.3 DTO Classes
- [ ] Create `HotelDTO.java`:
  - name, location, rank, price, available

- [ ] Create `CustomerDTO.java`:
  - name, desiredLocation, desiredRank, maxPrice, state

- [ ] Create `ProposalDTO.java`:
  - hotelName, price, rank, timestamp

- [ ] Create `ReservationDTO.java`:
  - confirmationNumber, customerName, hotelName, price, status

- [ ] Create `SearchRequestDTO.java`:
  - location, minRank, maxPrice

- [ ] Create `SimulationStatusDTO.java`:
  - state, currentTick, agentCount, activeReservations

---

## Phase 8: Contract Net Protocol Implementation

### 8.1 Message Flow
- [ ] Define message type constants:
  ```java
  public class MessageTypes {
      public static final String MSG_CFP = "CFP";
      public static final String MSG_PROPOSAL = "Proposal";
      public static final String MSG_REFUSE = "Refuse";
      public static final String MSG_ACCEPT = "Accept";
      public static final String MSG_REJECT = "Reject";
      public static final String MSG_CONFIRM = "Confirm";
      public static final String MSG_DF_REGISTER = "DFRegister";
      public static final String MSG_DF_SEARCH = "DFSearch";
      public static final String MSG_DF_RESULT = "DFResult";
  }
  ```

### 8.2 Deadline Management
- [ ] Implement deadline timer in CustomerRole:
  - Use SCOP's Action scheduling: `executeAt(tick + deadline)`
  - When deadline fires: call `evaluateProposals()`

### 8.3 Proposal Selection Algorithm
- [ ] Implement selection logic:
  ```java
  RoomProposal selectBestProposal(List<RoomProposal> proposals) {
      if (proposals.isEmpty()) return null;
      if (proposals.size() == 1) return proposals.get(0);

      // Sort by price, then by timestamp (FCFS)
      proposals.sort(Comparator
          .comparingDouble(RoomProposal::getPrice)
          .thenComparingLong(RoomProposal::getTimestamp));

      return proposals.get(0);
  }
  ```

---

## Phase 9: TNSAI Integration - Weather & External Services

### 9.1 Weather Service Integration
- [ ] Create `WeatherRole.java` using TNSAI WEB_SERVICE action type:
  ```java
  @RoleSpec(
      name = "WeatherChecker",
      description = "Checks weather conditions for travel destinations"
  )
  public class WeatherRole extends Role {

      @State
      private String lastWeatherData;

      @State
      private String lastCity;

      /**
       * Get current weather for a city using OpenWeatherMap API.
       * Uses WEB_SERVICE action type for direct HTTP API calls.
       */
      @Action(
          type = ActionType.WEB_SERVICE,
          description = "Get current weather for a city",
          webService = @WebService(
              endpoint = "https://api.openweathermap.org/data/2.5/weather",
              method = HttpMethod.GET,
              timeout = 5000,
              auth = AuthType.API_KEY,
              authTokenEnv = "OPENWEATHER_API_KEY",
              queryParams = {
                  @QueryParam(name = "q", value = "{city}"),
                  @QueryParam(name = "units", value = "metric"),
                  @QueryParam(name = "appid", value = "{OPENWEATHER_API_KEY}")
              }
          )
      )
      public WeatherResponse checkWeather(@Param("city") String city) {
          // Response is automatically parsed from JSON
          // Returns: temp, humidity, wind, description, etc.
          this.lastCity = city;
          return null; // Framework handles the response
      }

      /**
       * Get 5-day weather forecast for a city.
       */
      @Action(
          type = ActionType.WEB_SERVICE,
          description = "Get weather forecast for a city",
          webService = @WebService(
              endpoint = "https://api.openweathermap.org/data/2.5/forecast",
              method = HttpMethod.GET,
              timeout = 5000,
              auth = AuthType.API_KEY,
              authTokenEnv = "OPENWEATHER_API_KEY",
              queryParams = {
                  @QueryParam(name = "q", value = "{city}"),
                  @QueryParam(name = "units", value = "metric"),
                  @QueryParam(name = "cnt", value = "{days}"),
                  @QueryParam(name = "appid", value = "{OPENWEATHER_API_KEY}")
              }
          )
      )
      public ForecastResponse getForecast(
          @Param("city") String city,
          @Param("days") int days
      ) {
          return null; // Framework handles the response
      }

      @Action(
          type = ActionType.LOCAL,
          description = "Recommend based on weather"
      )
      public String getWeatherRecommendation(WeatherResponse weather) {
          if (weather == null) return "Weather data not available";

          double temp = weather.getMain().getTemp();
          String description = weather.getWeather().get(0).getDescription();

          StringBuilder recommendation = new StringBuilder();
          recommendation.append("Weather in ").append(lastCity).append(": ");
          recommendation.append(description).append(", ").append(temp).append("°C\n");

          // Recommendations based on weather
          if (temp > 25) {
              recommendation.append("☀️ Hot weather! Hotels with pool are recommended.");
          } else if (temp < 10) {
              recommendation.append("❄️ Cold weather! Hotels with spa/sauna are recommended.");
          } else {
              recommendation.append("🌤️ Pleasant weather for sightseeing!");
          }

          if (description.contains("rain")) {
              recommendation.append("\n🌧️ Rain expected - consider hotels with indoor activities.");
          }

          return recommendation.toString();
      }
  }
  ```

### 9.1.1 Weather Response Models
- [ ] Create `WeatherResponse.java`:
  ```java
  public class WeatherResponse {
      private Main main;
      private List<Weather> weather;
      private Wind wind;
      private String name;  // city name

      // Nested classes
      public static class Main {
          private double temp;
          private double feels_like;
          private int humidity;
          private int pressure;
          // getters/setters
      }

      public static class Weather {
          private String main;
          private String description;
          private String icon;
          // getters/setters
      }

      public static class Wind {
          private double speed;
          private int deg;
          // getters/setters
      }
  }
  ```

- [ ] Create `ForecastResponse.java`:
  ```java
  public class ForecastResponse {
      private List<ForecastItem> list;
      private City city;

      public static class ForecastItem {
          private long dt;  // timestamp
          private Main main;
          private List<Weather> weather;
          private String dt_txt;
      }

      public static class City {
          private String name;
          private String country;
      }
  }
  ```

### 9.2 Currency Exchange Integration
- [ ] Create `CurrencyRole.java` using WEB_SERVICE:
  ```java
  @RoleSpec(
      name = "CurrencyConverter",
      description = "Converts prices between currencies"
  )
  public class CurrencyRole extends Role {

      @State
      private Map<String, Double> ratesCache = new HashMap<>();

      /**
       * Get exchange rates for a base currency.
       * Uses free Exchange Rate API.
       */
      @Action(
          type = ActionType.WEB_SERVICE,
          description = "Get exchange rates",
          webService = @WebService(
              endpoint = "https://api.exchangerate-api.com/v4/latest/{base}",
              method = HttpMethod.GET,
              timeout = 5000
          )
      )
      public ExchangeRateResponse getExchangeRates(@Param("base") String baseCurrency) {
          return null; // Framework handles the response
      }

      /**
       * Convert price from one currency to another.
       */
      @Action(
          type = ActionType.LOCAL,
          description = "Convert currency amount"
      )
      public CurrencyConversion convertPrice(double amount, String from, String to) {
          ExchangeRateResponse rates = getExchangeRates(from);
          if (rates == null || rates.getRates() == null) {
              return new CurrencyConversion(amount, from, amount, to, 1.0, false);
          }

          Double rate = rates.getRates().get(to);
          if (rate == null) {
              return new CurrencyConversion(amount, from, amount, to, 1.0, false);
          }

          double convertedAmount = amount * rate;
          return new CurrencyConversion(amount, from, convertedAmount, to, rate, true);
      }

      /**
       * Format price with currency symbol.
       */
      @Action(
          type = ActionType.LOCAL,
          description = "Format price display"
      )
      public String formatPrice(double amount, String currency) {
          return switch (currency.toUpperCase()) {
              case "USD" -> String.format("$%.2f", amount);
              case "EUR" -> String.format("€%.2f", amount);
              case "TRY" -> String.format("₺%.2f", amount);
              case "GBP" -> String.format("£%.2f", amount);
              default -> String.format("%.2f %s", amount, currency);
          };
      }
  }
  ```

### 9.2.1 Currency Response Models
- [ ] Create `ExchangeRateResponse.java`:
  ```java
  public class ExchangeRateResponse {
      private String base;
      private String date;
      private Map<String, Double> rates;
      // getters/setters
  }
  ```

- [ ] Create `CurrencyConversion.java`:
  ```java
  public class CurrencyConversion {
      private double originalAmount;
      private String originalCurrency;
      private double convertedAmount;
      private String targetCurrency;
      private double exchangeRate;
      private boolean success;

      public CurrencyConversion(double original, String from,
                                double converted, String to,
                                double rate, boolean success) {
          this.originalAmount = original;
          this.originalCurrency = from;
          this.convertedAmount = converted;
          this.targetCurrency = to;
          this.exchangeRate = rate;
          this.success = success;
      }

      public String getFormattedConversion() {
          return String.format("%.2f %s = %.2f %s (rate: %.4f)",
              originalAmount, originalCurrency,
              convertedAmount, targetCurrency, exchangeRate);
      }
  }
  ```

### 9.3 Enhanced CustomerAgent with TNSAI
- [ ] Update `CustomerAgent.java`:
  ```java
  @AgentSpec(
      description = "Smart customer agent with weather awareness and currency conversion"
  )
  public class CustomerAgent extends Agent {

      @State
      private WeatherResponse lastWeather;

      @State
      private String preferredCurrency = "USD";

      @Override
      protected void setup() {
          // Core roles
          adopt(new CustomerRole(this, "HotelEnv"));

          // TNSAI-powered roles with WEB_SERVICE
          adopt(new WeatherRole(this, "HotelEnv"));
          adopt(new CurrencyRole(this, "HotelEnv"));

          // Enable conversation
          playConversation();
      }

      /**
       * Pre-booking: Check weather and adjust preferences.
       */
      public void prepareForBooking(String city) {
          WeatherRole weatherRole = as(WeatherRole.class);

          // Get weather via WEB_SERVICE
          WeatherResponse weather = weatherRole.checkWeather(city);
          this.lastWeather = weather;

          if (weather != null) {
              String recommendation = weatherRole.getWeatherRecommendation(weather);
              getLogger().info("Weather recommendation for {}: {}", city, recommendation);
          }
      }

      /**
       * Convert hotel price to preferred currency.
       */
      public CurrencyConversion convertPrice(double price, String hotelCurrency) {
          CurrencyRole currencyRole = as(CurrencyRole.class);
          return currencyRole.convertPrice(price, hotelCurrency, preferredCurrency);
      }

      public void setPreferredCurrency(String currency) {
          this.preferredCurrency = currency;
      }
  }
  ```

### 9.4 Weather-Aware Booking Flow
- [ ] Update CustomerRole to use weather data from WEB_SERVICE:
  ```java
  public void startSmartSearch() {
      // 1. Check weather first via WEB_SERVICE
      WeatherRole weatherRole = getOwner().as(WeatherRole.class);
      WeatherResponse weather = weatherRole.checkWeather(desiredLocation);

      // 2. Adjust preferences based on weather data
      if (weather != null && weather.getMain() != null) {
          double temp = weather.getMain().getTemp();
          String description = weather.getWeather().get(0).getDescription();

          getLogger().info("Weather in {}: {}°C, {}",
              desiredLocation, temp, description);

          // Hot weather - prefer pool
          if (temp > 25) {
              preferredAmenities.add("pool");
              getLogger().info("Added 'pool' to preferences due to hot weather");
          }

          // Cold weather - prefer spa
          if (temp < 10) {
              preferredAmenities.add("spa");
              getLogger().info("Added 'spa' to preferences due to cold weather");
          }

          // Rainy weather - prefer indoor activities
          if (description.contains("rain")) {
              preferredAmenities.add("restaurant");
              preferredAmenities.add("gym");
              getLogger().info("Added indoor amenities due to rain forecast");
          }
      }

      // 3. Convert budget to hotel currency if needed
      CurrencyRole currencyRole = getOwner().as(CurrencyRole.class);
      if (!budgetCurrency.equals("USD")) {
          CurrencyConversion conversion = currencyRole.convertPrice(maxPrice, budgetCurrency, "USD");
          if (conversion.isSuccess()) {
              maxPriceUSD = conversion.getConvertedAmount();
              getLogger().info("Budget converted: {}", conversion.getFormattedConversion());
          }
      }

      // 4. Continue with normal search
      startSearch();
  }
  ```

### 9.5 Weather API Endpoints
- [ ] Add weather endpoints to controller:
  ```java
  @GetMapping("/weather/{city}")
  public ResponseEntity<WeatherDTO> getWeather(@PathVariable String city);

  @GetMapping("/weather/forecast/{city}")
  public ResponseEntity<ForecastDTO> getForecast(
      @PathVariable String city,
      @RequestParam int days
  );
  ```

### 9.6 Chat Endpoint with Weather Support
- [ ] Add chat capability to controller:
  ```java
  @PostMapping("/chat/select")
  // Select agent for chat

  @PostMapping("/chat/message")
  // Send message to selected agent
  // Supports weather queries: "What's the weather in Istanbul?"

  @GetMapping("/chat/history")
  // Get conversation history
  ```

### 9.7 Environment Variables
- [ ] Document required environment variables:
  ```
  # Weather API
  OPENWEATHER_API_KEY=your_api_key_here

  # Optional: LLM providers
  OLLAMA_BASE_URL=http://localhost:11434
  OPENAI_API_KEY=your_key_here  # if using OpenAI
  ```

---

## Phase 10: Bonus - Negotiation Mechanism

### 10.1 NegotiationRole
- [ ] Create `NegotiationRole.java`:
  - `startNegotiation(HotelIdentifier, currentPrice, desiredPrice)`
  - `handleCounterOfferMessage(Message<CounterOffer>)`
  - `makeCounterOffer(double currentOffer, double targetPrice)`
  - Configurable concession strategy

### 10.2 Negotiation Protocol
- [ ] Define negotiation message types:
  - `MSG_NEGOTIATE_START`
  - `MSG_COUNTER_OFFER`
  - `MSG_NEGOTIATE_ACCEPT`
  - `MSG_NEGOTIATE_REJECT`

### 10.3 Negotiation Endpoints
- [ ] Add negotiation endpoints:
  ```java
  @PostMapping("/negotiate/start")
  // Body: { customerId, hotelName, offerPrice }

  @GetMapping("/negotiate/{customerId}/status")
  // Get negotiation status

  @PostMapping("/negotiate/{customerId}/counter")
  // Body: { newPrice }
  ```

---

## Phase 11: Testing & Console Output

### 11.1 Console Output
- [ ] Implement meaningful logging:
  - Agent creation and initialization
  - DF registration events
  - CFP broadcasts
  - Proposal/Refusal responses
  - Decision making process
  - Final reservation result

### 11.2 Unit Tests
- [ ] Test DF registration and search
- [ ] Test CNP message flow
- [ ] Test proposal selection algorithm
- [ ] Test deadline handling
- [ ] Test REST endpoints
- [ ] Test negotiation (if implemented)

### 11.3 Integration Test
- [ ] Create `HotelReservationTest.java`:
  - Full scenario test via API calls
  - Verify reservation is made correctly
  - Test edge cases (no responses, all same price, etc.)

---

## Execution

### Run Commands
```bash
# Build
mvn clean install -Pshade

# Run as Web Service (Endpoint yaklaşımı)
java -jar target/model-fat.jar exec-web --config config.json

# Or run with Spring Boot directly
mvn spring-boot:run -Dconfig=config.json

# CLI mode (for debugging)
java -jar target/model-fat.jar exec-cli --config config.json
```

### API Usage Example
```bash
# ==========================================
# DUMMY DATA API (Phase 2)
# ==========================================

# List all hotels in database
curl "http://localhost:8080/api/data/hotels"

# Get specific hotel
curl "http://localhost:8080/api/data/hotels/h001"

# Search hotels by criteria
curl "http://localhost:8080/api/data/hotels/search?city=Istanbul&minRank=4&maxPrice=500"

# Get hotel rooms
curl "http://localhost:8080/api/data/hotels/h001/rooms"

# Get available cities
curl "http://localhost:8080/api/data/hotels/cities"

# ==========================================
# WEATHER API (TNSAI Integration)
# ==========================================

# Check weather for a city
curl "http://localhost:8080/api/weather/Istanbul"

# Get weather forecast
curl "http://localhost:8080/api/weather/forecast/Istanbul?days=5"

# ==========================================
# SIMULATION CONTROL
# ==========================================

# 1. Setup simulation
curl -X POST "http://localhost:8080/api/hotel/simulation?action=setup"

# 2. Check status
curl "http://localhost:8080/api/hotel/simulation/status"

# 3. Run simulation
curl -X POST "http://localhost:8080/api/hotel/simulation?action=run"

# 4. Pause simulation
curl -X POST "http://localhost:8080/api/hotel/simulation?action=pause"

# ==========================================
# BOOKING FLOW
# ==========================================

# 1. Check weather before booking (optional)
curl "http://localhost:8080/api/weather/Istanbul"

# 2. Search for hotels via CNP
curl -X POST "http://localhost:8080/api/hotel/search" \
  -H "Content-Type: application/json" \
  -d '{"location":"Istanbul","minRank":4,"maxPrice":500}'

# 3. Get proposals from hotel agents
curl "http://localhost:8080/api/hotel/proposals?customerId=Customer-1"

# 4. Make reservation
curl -X POST "http://localhost:8080/api/hotel/reserve" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"Customer-1","hotelName":"Luxury Palace"}'

# 5. Get reservation confirmation
curl "http://localhost:8080/api/hotel/reservations"

# ==========================================
# AGENT CHAT (TNSAI Conversation)
# ==========================================

# Select agent for chat
curl -X POST "http://localhost:8080/api/chat/select?agent=Customer-1"

# Send message (supports weather queries)
curl -X POST "http://localhost:8080/api/chat/message" \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the weather like in Istanbul?"}'

# Get chat history
curl "http://localhost:8080/api/chat/history"

# ==========================================
# DIRECTORY FACILITATOR
# ==========================================

# List all registered services
curl "http://localhost:8080/api/hotel/df/entries"

# Search for hotel agents
curl "http://localhost:8080/api/hotel/df/search?location=Istanbul&minRank=4"
```

---

## Progress Tracking

| Phase | Status | Notes |
|-------|--------|-------|
| 1. Project Setup | [x] **DONE** | Maven, dependencies, package structure |
| 2. Dummy Hotel Data API | [x] **DONE** | Mock data layer with 8 hotels |
| 3. Message Payload Models | [x] **DONE** | CNP message payloads (RoomQuery, RoomProposal, etc.) |
| 4. Directory Facilitator | [x] **DONE** | Agent discovery (DF Environment) |
| 5. Hotel Agent | [x] **DONE** | Hotel provider with HotelProviderRole |
| 6. Customer Agent | [x] **DONE** | CNP initiator with CustomerRole |
| 7. Playground Setup | [x] **DONE** | HotelReservationPlayground |
| 8. REST API Layer | [x] **DONE** | Spring Boot REST endpoints |
| 9. CNP Implementation | [x] **DONE** | Protocol flow in roles |
| 10. Weather API | [x] **DONE** | WEB_SERVICE pattern (WeatherController) |
| 11. Negotiation | [ ] Pending | Bonus feature |
| 12. Testing | [ ] Pending | Integration tests |

---

## References

### SCOP Framework
- SCOP Core: `/Users/tansuasici/Projeler/SCOP/scop-core`
- SCOP Web Service Controller: `/Users/tansuasici/Projeler/SCOP/scop-ui/src/main/java/ai/scop/ui/command/impl/exec/web_service/Controller.java`
- SCOP Template Project: `/Users/tansuasici/Projeler/SCOP/scop-template`

### TNSAI Framework
- TNSAI Core: `/Users/tansuasici/Projeler/TnsAI/tnsai-core`
- TNSAI Integration (SCOPBridge): `/Users/tansuasici/Projeler/TnsAI/tnsai-integration/src/main/java/com/tnsai/integration/scop/SCOPBridge.java`
- TNSAI Tools: `/Users/tansuasici/Projeler/TnsAI/tnsai-tools`
- Weather Tool: `/Users/tansuasici/Projeler/TnsAI/tnsai-tools/src/main/java/com/tnsai/tools/realtime/OpenWeatherMapTool.java`
- BuiltInTools Enum: `/Users/tansuasici/Projeler/TnsAI/tnsai-core/src/main/java/com/tnsai/enums/BuiltInTool.java`

### Specifications
- Contract Net Protocol (FIPA specification)
- OpenWeatherMap API: https://openweathermap.org/api

### Environment Variables
```bash
# Required for Weather functionality
export OPENWEATHER_API_KEY=your_api_key_here

# Optional: LLM providers
export OLLAMA_BASE_URL=http://localhost:11434
export OPENAI_API_KEY=your_key_here
```
