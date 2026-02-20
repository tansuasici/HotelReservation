# Hotel Reservation Multi-Agent System - Sistem Raporu

## İçindekiler

1. [Genel Bakış](#1-genel-bakış)
2. [Ajan Tanımları](#2-ajan-tanımları)
3. [Rol ve Sorumluluklar](#3-rol-ve-sorumluluklar)
4. [Contract Net Protocol (CNP) Akışı](#4-contract-net-protocol-cnp-akışı)
5. [Pazarlık (Negotiation) Mekanizması](#5-pazarlık-negotiation-mekanizması)
6. [Fiyatlama Stratejileri](#6-fiyatlama-stratejileri)
7. [Directory Facilitator (DF)](#7-directory-facilitator-df)
8. [Ağ Topolojisi](#8-ağ-topolojisi)
9. [REST API Endpoint'leri](#9-rest-api-endpointleri)
10. [SCOP Framework Kullanımı](#10-scop-framework-kullanımı)
11. [TNSAI Entegrasyonu](#11-tnsai-entegrasyonu)
12. [Veri Katmanı](#12-veri-katmanı)
13. [Yazılım Mühendisliği Değerlendirmesi](#13-yazılım-mühendisliği-değerlendirmesi)

---

## 1. Genel Bakış

Bu proje, **SCOP Framework** ve **TNSAI Integration** kullanılarak geliştirilmiş bir çoklu ajan sistemidir (Multi-Agent System - MAS). Otel odaları için rezervasyon sürecini **Contract Net Protocol (CNP)** ile modellemektedir.

### Teknoloji Yığını

| Katman | Teknoloji | Versiyon |
|--------|-----------|----------|
| Uygulama Çerçevesi | Spring Boot | 3.2.0 |
| Ajan Çerçevesi | SCOP Core + UI | 2026.02.17 |
| Ajan Annotation'ları | TNSAI Annotations | - |
| Dil | Java | 21 |
| Build | Maven | - |
| Konfigürasyon | dotenv-java | 3.0.0 |
| JSON | Jackson | 2.16.0 |

### Sistem Bileşen Sayıları

| Bileşen | Sayı | Kaynak |
|---------|------|--------|
| Otel Ajanı | 11 | `hotel-data.json` |
| Müşteri Ajanı | 15 | `customer-data.json` |
| DataFetcher Ajanı | 1 | Sabit |
| Directory Facilitator | 1 | Sabit |
| NetworkEnvironment | 1 | Sabit |
| **Toplam Ajan** | **29** | - |

---

## 2. Ajan Tanımları

### 2.1 HotelAgent

**Dosya:** `agent/HotelAgent.java`

HotelAgent, sistemdeki bir oteli temsil eder. CNP'de **participant** (katılımcı) rolündedir.

```
@AgentSpec(
    description = "Hotel service provider agent that handles room reservations",
    llm = @LLMSpec(provider = OLLAMA, model = "minimax-m2.1:cloud", temperature = 0.5f)
)
```

**Oluşturma:** `HotelReservationPlayground.createHotelAgents()` içinde, `DataFetcherRole` üzerinden API'den çekilen otel verilerine göre dinamik olarak oluşturulur.

**Yapısı:**
- `hotelId` - Benzersiz otel ID'si (örn: "h001")
- `hotelName` - Görüntüleme adı
- `location` - Şehir
- `rank` - Yıldız derecesi (1-5)
- `basePrice` - Gecelik fiyat (USD)
- `totalRooms` - Toplam oda kapasitesi (JSON'dan veya random 1-3)
- `availableRooms` - Müsait oda sayısı (rezervasyon yapıldıkça azalır)

**`setup()` Kodu:**

```java
@Override
protected void setup() {
    // 1. CNP participant davranışını sağlayan HotelProviderRole oluşturulur.
    //    Otelin tüm verileri (id, ad, konum, yıldız, fiyat) ve fiyatlama stratejisi verilir.
    HotelProviderRole providerRole = new HotelProviderRole(this, "HotelEnv",
        hotelId, hotelName, location, rank, basePrice, new LinearPricingStrategy());
    adopt(providerRole);  // Role'ü ajana bağla

    // 2. Chat desteği için Conversation role adopt edilir (dashboard'dan sohbet)
    adopt(new Conversation(this, getPlayground()));

    // 3. @BeforeAction validasyonu ile DF'ye kayıt (HotelProviderRole'e delege edildi)
    providerRole.registerWithDF();
}
```

**Oda Yönetimi Kodu:**

```java
// synchronized: Birden fazla müşteri aynı anda oda ayırtmaya çalışırsa
// race condition engellenir. Oda sayısı atomik olarak azaltılır.
public synchronized boolean reserveRoom() {
    if (availableRooms <= 0) return false;  // Oda kalmadıysa reddet
    availableRooms--;                        // Müsait oda sayısını azalt
    return true;                             // Başarıyla ayırtıldı
}
```

### 2.2 CustomerAgent

**Dosya:** `agent/CustomerAgent.java`

CustomerAgent, otel arayan bir müşteriyi temsil eder. CNP'de **initiator** (başlatıcı) rolündedir.

```
@AgentSpec(
    description = "Hotel reservation customer agent that searches and books hotels",
    llm = @LLMSpec(provider = OLLAMA, model = "glm-4.7:cloud", temperature = 0.7f)
)
```

**Yapısı:**
- `desiredLocation` - İstenen şehir
- `desiredRank` - Minimum yıldız derecesi
- `maxPrice` - Maksimum bütçe
- `desiredPrice` - Pazarlık için hedef fiyat (`maxPrice * 0.8`)

**`setup()` Kodu:**

```java
@Override
protected void setup() {
    // 1. CNP initiator davranışını sağlayan CustomerRole oluşturulur.
    //    Arama kriterleri (konum, yıldız, fiyat) ve pazarlık stratejisi verilir.
    adopt(new CustomerRole(this, "HotelEnv", desiredLocation, desiredRank,
        maxPrice, desiredPrice, new LinearPricingStrategy()));

    // 2. Chat desteği
    adopt(new Conversation(this, getPlayground()));
}
```

**Arama Tetikleme Kodu:**

```java
// as() metodu: SCOP'un Role casting mekanizması — ajandan role erişim sağlar.
// CNP akışını başlatan tek giriş noktası budur.
public void startSearch() {
    CustomerRole role = as(CustomerRole.class);  // Ajan → Role dönüşümü
    if (role != null) {
        role.startSearch();  // Tüm CNP zinciri buradan tetiklenir
    }
}
```

### 2.3 DataFetcherAgent

**Dosya:** `agent/DataFetcherAgent.java`

Yardımcı ajan. Otel ve müşteri verilerini REST API'den çekmekten sorumludur. `DataFetcherRole` adopt eder.

**Not:** Kendi sunucusuna (`localhost:3001`) HTTP çağrısı yapar - bu, ajan çerçevesinin dış veri kaynaklarına erişim modelini (`WEB_SERVICE` pattern) uygulamaktadır.

**`DataFetcherRole.fetchAllHotels()` Kodu:**

```java
// @Action(WEB_SERVICE): TNSAI annotation'ı bu metodun bir HTTP servisi çağrısı olduğunu belirtir.
// Java'nın HttpClient API'si kullanılarak asenkron olmayan GET isteği yapılır.
@Action(type = ActionType.WEB_SERVICE, description = "Fetch all hotels from Hotel Data API")
public List<Hotel> fetchAllHotels() {
    try {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/hotels"))  // localhost:3001/api/hotels
                .timeout(Duration.ofSeconds(5))              // 5sn timeout
                .GET().build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Jackson ObjectMapper ile JSON → List<Hotel> dönüşümü
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        }
        return Collections.emptyList();  // Hata durumunda boş liste
    } catch (Exception e) {
        return Collections.emptyList();  // Bağlantı hatası → graceful fallback
    }
}
```

---

## 3. Rol ve Sorumluluklar

### 3.1 CustomerRole

**Dosya:** `role/CustomerRole.java`

CNP initiator davranışını implemente eder. TNSAI `@RoleSpec` ile annotate edilmiştir:

```
@RoleSpec(
    responsibilities = {
        HotelSearch      - "startSearch", "queryDirectoryFacilitator"
        ProposalCollection - "handleProposalMessage", "evaluateProposals"
        Reservation      - "makeReservation", "handleConfirmMessage"
        Negotiation      - "startNegotiation", "handleCounterOfferMessage", ...
    },
    llm = @LLMSpec(provider = OLLAMA, model = "glm-4.7:cloud", temperature = 0.7f),
    communication = @Communication(tone = FRIENDLY, languages = {"tr", "en"})
)
```

**State Machine:**

```
IDLE --> SEARCHING --> WAITING_PROPOSALS --> EVALUATING --> NEGOTIATING --> RESERVING --> COMPLETED
  |                                            |              |
  +<-----------------------------------------+              |
  |                        (hiç teklif yok)                  |
  +<--------------------------------------------------------+
                           (pazarlık başarısız)
                                              FAILED
```

**Sorumluluklar:**

| Sorumluluk | Metotlar | Açıklama |
|------------|----------|----------|
| HotelSearch | `startSearch()`, `queryDirectoryFacilitator()` | DF'den eşleşen otelleri bulur |
| ProposalCollection | `handleProposalMessage()`, `evaluateProposals()` | Teklifleri toplar, shortlist oluşturur |
| Reservation | `makeReservation()`, `handleConfirmMessage()` | Seçilen otele ACCEPT gönderir |
| Negotiation | `startNegotiationWithCandidate()`, `handleCounterOfferMessage()`, `handleNegotiateAcceptMessage()`, `handleNegotiateRejectMessage()` | Fiyat pazarlığı yapar |

**`startSearch()` — CNP Başlangıç Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Initiate hotel search based on criteria")
public void startSearch() {
    // State guard: sadece IDLE veya FAILED durumunda arama başlatılabilir
    if (state != CustomerState.IDLE && state != CustomerState.FAILED) return;

    state = CustomerState.SEARCHING;
    proposals.clear();           // Önceki teklifleri temizle
    pendingResponses.clear();    // Beklenen yanıtları sıfırla
    searchStartTime = System.currentTimeMillis();  // Deadline sayacı başlat

    queryDirectoryFacilitator(); // DF sorgusu → CFP broadcast → CNP zinciri başlar
}
```

**`queryDirectoryFacilitator()` — DF Sorgulama Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Query Directory Facilitator for matching hotels")
private void queryDirectoryFacilitator() {
    // Playground üzerinden DF ortamına erişim (SCOP'un getAgent mekanizması)
    DirectoryFacilitator df = getOwner().getPlayground()
        .getAgent(DirectoryFacilitator.class, "DF");

    if (df == null) { state = CustomerState.FAILED; return; }

    // Konum, yıldız, fiyat kriterlerine göre eşleşen otelleri ara
    matchingHotels = df.search(desiredLocation, desiredRank, maxPrice);

    if (matchingHotels.isEmpty()) { state = CustomerState.FAILED; return; }

    state = CustomerState.WAITING_PROPOSALS;
    broadcastCFP(matchingHotels);  // Eşleşen tüm otellere CFP gönder
}
```

**`handleProposalMessage()` — Teklif Toplama Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Process incoming proposal from hotel")
public void handleProposalMessage(Message<RoomProposal> message) {
    RoomProposal proposal = message.getPayload();

    proposals.put(proposal.getProposalId(), proposal);            // Teklifi kaydet
    pendingResponses.remove(message.getSender().getAgentName());  // Beklenen listeden çıkar

    // @AfterAction hook: piyasa analitiği (ortalama, min/max, anomali)
    ActionParams params = new ActionParams(new HashMap<>(), "handleProposalMessage");
    params.set("proposalPrice", proposal.getPricePerNight());
    params.set("hotelName", proposal.getHotelName());
    afterHandleProposalMessage(params);

    checkProposalDeadline();  // Tüm yanıtlar geldi mi veya deadline doldu mu?
}
```

**`evaluateProposals()` — Shortlist Oluşturma Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Evaluate all proposals and shortlist top candidates")
public void evaluateProposals() {
    // Teklifleri fiyata göre sırala, eşitlikte timestamp ile FCFS uygula
    topCandidates = proposals.values().stream()
        .sorted(Comparator
            .comparingDouble(RoomProposal::getPricePerNight)
            .thenComparingLong(RoomProposal::getTimestamp))
        .limit(MAX_CANDIDATES)  // En iyi N adayı al (varsayılan 3)
        .collect(Collectors.toList());

    // Shortlist dışındaki teklifleri hemen reddet
    for (RoomProposal other : proposals.values()) {
        if (!topIds.contains(other.getProposalId())) {
            rejectProposal(other, "Not in shortlist");
        }
    }

    // En iyi fiyat <= istenen fiyat → doğrudan kabul (pazarlık gereksiz)
    if (selectedProposal.getPricePerNight() <= desiredPrice) {
        rejectRemainingCandidates(0);
        makeReservation();
    } else {
        startNegotiationWithCandidate(0);  // İlk adayla pazarlık başlat
    }
}
```

### 3.2 HotelProviderRole

**Dosya:** `role/HotelProviderRole.java`

CNP participant davranışını implemente eder:

```
@RoleSpec(
    responsibilities = {
        ProposalGeneration    - "handleCFPMessage", "sendProposal"
        ReservationManagement - "handleAcceptMessage", "handleRejectMessage"
        Negotiation           - "handleNegotiateStartMessage", "handleCounterOfferMessage", ...
    },
    llm = @LLMSpec(provider = OLLAMA, model = "minimax-m2.1:cloud", temperature = 0.5f),
    communication = @Communication(tone = PROFESSIONAL, languages = {"tr", "en"})
)
```

**Sorumluluklar:**

| Sorumluluk | Metotlar | Açıklama |
|------------|----------|----------|
| ProposalGeneration | `handleCFPMessage()`, `sendProposal()` | CFP'yi değerlendirir, teklif gönderir |
| ReservationManagement | `handleAcceptMessage()`, `handleRejectMessage()` | Rezervasyon onayı yapar |
| Negotiation | `handleNegotiateStartMessage()`, `handleCounterOfferMessage()`, `handleNegotiateAcceptMessage()` | Müşteri pazarlığını yanıtlar |

**`handleCFPMessage()` — CFP İşleme Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Process incoming CFP and decide whether to make a proposal")
public void handleCFPMessage(Message<RoomQuery> message) {
    RoomQuery query = message.getPayload();

    // 1. Otel kriterlere uyuyor mu? (konum, yıldız, fiyat kontrolü)
    if (!matchesQuery(query)) return;  // Sessizce ignore et

    // 2. Müsait oda var mı?
    HotelAgent hotelAgent = (HotelAgent) getOwner();
    if (hotelAgent.getAvailableRooms() <= 0) {
        sendRefusal(message.getSender(), "No rooms available");  // MSG_REFUSE gönder
        return;
    }

    // 3. Simülasyon: responseRate'e göre rastgele yanıt verme kararı
    if (!shouldRespond()) {
        sendRefusal(message.getSender(), "Hotel currently unavailable");
        return;
    }

    // 4. @BeforeAction hook ile dinamik fiyat hesapla, teklif gönder
    sendProposal(message.getSender());
}
```

**`handleAcceptMessage()` — Rezervasyon Onay Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Process reservation acceptance and send confirmation")
public void handleAcceptMessage(Message<ReservationRequest> message) {
    ReservationRequest request = message.getPayload();

    // synchronized reserveRoom(): Thread-safe oda ayırtma
    HotelAgent hotelAgent = (HotelAgent) getOwner();
    if (!hotelAgent.reserveRoom()) {
        sendRefusal(message.getSender(), "Room no longer available");
        return;
    }

    // Onay oluştur: confirmation number, fiyat, gece sayısı
    ReservationConfirmation confirmation = new ReservationConfirmation(
        request.getRequestId(), request.getCustomerId(),
        hotelId, hotelName, basePrice, request.getNumberOfNights()
    );

    // MSG_CONFIRM gönder
    sendMessage(MessageTypes.MSG_CONFIRM, confirmation, message.getSender());
}
```

**`getEffectiveMinPrice()` — Talep Baskısı Hesaplama Kodu:**

```java
// Doluluk oranına göre minimum kabul edilebilir fiyatı hesaplar.
// Non-linear (karesel) artış: az oda kaldıkça fiyat katlanarak artar.
private double getEffectiveMinPrice() {
    HotelAgent ha = (HotelAgent) getOwner();
    int total = ha.getTotalRooms();
    int available = ha.getAvailableRooms();
    if (total <= 0 || available <= 0) return basePrice;  // Oda yoksa tam fiyat

    double occupancyRate = 1.0 - ((double) available / total);  // 0.0–1.0
    double scarcityFactor = occupancyRate * occupancyRate;        // Karesel artış
    // baseMinPrice + (basePrice - baseMinPrice) × scarcityFactor
    // Örnek: %75 doluluk → scarcity=0.56 → fiyat %56 oranında artmış
    return baseMinPrice + (basePrice - baseMinPrice) * scarcityFactor;
}
```

### 3.3 DataFetcherRole

**Dosya:** `role/DataFetcherRole.java`

`WEB_SERVICE` tipinde action'lar ile REST API'den veri çeker:

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| `fetchAllHotels()` | `GET /api/hotels` | Tüm otelleri getirir |
| `fetchHotelById(id)` | `GET /api/hotels/{id}` | ID ile otel getirir |
| `searchHotels(city, minRank, maxPrice)` | `GET /api/hotels/search` | Otel arar |
| `fetchAllCustomers()` | `GET /api/customers` | Tüm müşterileri getirir |
| `fetchCustomerById(id)` | `GET /api/customers/{id}` | ID ile müşteri getirir |
| `searchCustomers(city, minRank, maxPrice)` | `GET /api/customers/search` | Müşteri arar |

---

## 4. Contract Net Protocol (CNP) Akışı

### 4.1 Mesaj Tipleri

**Dosya:** `message/MessageTypes.java`

| Mesaj Tipi | Sabit | Gönderen | Alan | Payload |
|------------|-------|----------|------|---------|
| Call For Proposals | `MSG_CFP` | Customer | Hotel | `RoomQuery` |
| Teklif | `MSG_PROPOSAL` | Hotel | Customer | `RoomProposal` |
| Red | `MSG_REFUSE` | Hotel | Customer | `String` |
| Kabul | `MSG_ACCEPT` | Customer | Hotel | `ReservationRequest` |
| Ret | `MSG_REJECT` | Customer | Hotel | `String` |
| Onay | `MSG_CONFIRM` | Hotel | Customer | `ReservationConfirmation` |
| Pazarlık Başlat | `MSG_NEGOTIATE_START` | Customer | Hotel | `NegotiationOffer` |
| Karşı Teklif | `MSG_COUNTER_OFFER` | Her iki taraf | Diğer taraf | `NegotiationOffer` |
| Pazarlık Kabul | `MSG_NEGOTIATE_ACCEPT` | Her iki taraf | Diğer taraf | `NegotiationOffer` / `ReservationRequest` |
| Pazarlık Red | `MSG_NEGOTIATE_REJECT` | Her iki taraf | Diğer taraf | `String` |

### 4.2 Temel CNP Akışı (Pazarlıksız)

```
Customer                         Directory Facilitator              Hotel Agents
   |                                    |                               |
   |-- queryDF(location, rank, price) ->|                               |
   |<- matchingHotels[] ---------------|                               |
   |                                                                    |
   |============= CFP BROADCAST =======================================|
   |-- MSG_CFP(RoomQuery) ------------------------------------------> Hotel-1
   |-- MSG_CFP(RoomQuery) ------------------------------------------> Hotel-2
   |-- MSG_CFP(RoomQuery) ------------------------------------------> Hotel-3
   |                                                                    |
   |<===== RESPONSES ==================================================|
   |<- MSG_PROPOSAL(RoomProposal) ---------------------------------- Hotel-1
   |<- MSG_REFUSE("No rooms") ------------------------------------- Hotel-2
   |<- MSG_PROPOSAL(RoomProposal) ---------------------------------- Hotel-3
   |                                                                    |
   |== EVALUATION =====================================================|
   | (en ucuz teklifi seç)                                             |
   |                                                                    |
   |-- MSG_ACCEPT(ReservationRequest) --------------------------------> Hotel-1
   |-- MSG_REJECT("Another hotel selected") --------------------------> Hotel-3
   |                                                                    |
   |<- MSG_CONFIRM(ReservationConfirmation) ------------------------- Hotel-1
   |                                                                    |
   | STATE: COMPLETED                                                  |
```

### 4.3 Genişletilmiş CNP Akışı (Pazarlıklı)

Eğer en iyi teklifin fiyatı `desiredPrice`'dan yüksekse, pazarlık akışı devreye girer (Bölüm 5'te detaylandırılmıştır).

### 4.4 Race Condition Korunması

`CustomerRole.broadcastCFP()` metodunda önemli bir güvenlik mekanizması vardır:

```java
// ÖNCE: Tüm beklenen yanıtları kaydet
for (DFEntry hotel : hotels) {
    pendingResponses.add(hotel.getAgentName());
}
// SONRA: CFP'leri gönder
for (DFEntry hotel : hotels) {
    sendMessage(MSG_CFP, query, hotelRole.getIdentifier());
}
```

Bu yaklaşım, hızlı bir REFUSE yanıtının tüm CFP'ler gönderilmeden değerlendirme tetiklemesini engeller.

### 4.5 Deadline Mekanizması

`PROPOSAL_DEADLINE_MS` (varsayılan 30 saniye) süresi içinde tüm yanıtlar gelmezse, mevcut tekliflerle değerlendirme yapılır:

```java
boolean allResponded = pendingResponses.isEmpty();
boolean deadlinePassed = System.currentTimeMillis() - searchStartTime > PROPOSAL_DEADLINE_MS;
if (allResponded || deadlinePassed) { evaluateProposals(); }
```

---

## 5. Pazarlık (Negotiation) Mekanizması

### 5.1 Top-N Aday Sistemi

Teklif değerlendirme aşamasında, teklifler fiyata göre sıralanır ve en iyi `MAX_CANDIDATES` (varsayılan 3) aday shortlist'e alınır. Shortlist dışındaki tüm teklifler anında reddedilir.

```java
topCandidates = proposals.values().stream()
    .sorted(Comparator.comparingDouble(RoomProposal::getPricePerNight)
                       .thenComparingLong(RoomProposal::getTimestamp))
    .limit(MAX_CANDIDATES)
    .collect(Collectors.toList());
```

Eşit fiyatlı tekliflerde `timestamp` (FCFS - First Come First Served) kullanılır.

### 5.2 Sırasal Pazarlık Akışı

```
Aday 1 (en ucuz) -----> Pazarlık başla
     |                        |
     |-- Başarılı?            |-- Evet --> Diğer adayları reddet --> Rezervasyon
     |                        |
     |-- Hayır                |
     v                        |
Aday 2 -----> Pazarlık başla  |
     |                        |
     |-- Başarılı?            |-- Evet --> Diğer adayları reddet --> Rezervasyon
     |                        |
     |-- Hayır                |
     v                        |
Aday 3 -----> Pazarlık başla  |
     |                        |
     |-- Başarılı?            |-- Evet --> Rezervasyon
     |                        |
     |-- Hayır --> FAILED     |
```

### 5.3 Leverage (Koz) Kullanımı

Müşteri, rakip adayların tekliflerini pazarlık kozu olarak kullanır:

```java
RoomProposal competing = getCompetingCandidate();  // sıradaki aday
if (competing != null && competing.getPricePerNight() < hotelOffer.getOfferedPrice()) {
    leverageMsg = "We have a competing offer at $X/night.";
}
```

### 5.4 Müşteri Tarafında Pazarlık Kararları

| Durum | Karar |
|-------|-------|
| `offeredPrice <= desiredPrice` | Hemen kabul et |
| `counterPrice >= hotelOffer` | Otel teklifini kabul et |
| `maxRounds` aşıldıysa ve `offeredPrice <= maxPrice` | Son teklifi kabul et |
| `maxRounds` aşıldıysa ve `offeredPrice > maxPrice` | Sonraki adaya geç |
| Otel pazarlığı tamamen reddettiyse ve `listedPrice <= maxPrice` | Liste fiyatından kabul et |
| Otel pazarlığı tamamen reddettiyse ve `listedPrice > maxPrice` | Sonraki adaya geç |

**`handleCounterOfferMessage()` — Müşteri Karşı Teklif Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Process hotel's counter-offer during negotiation")
public void handleCounterOfferMessage(Message<NegotiationOffer> message) {
    NegotiationOffer hotelOffer = message.getPayload();
    negotiationHistory.add(hotelOffer);

    // 1. Otel teklifi istediğimiz fiyatın altında mı? → Hemen kabul
    if (hotelOffer.getOfferedPrice() <= desiredPrice) {
        acceptNegotiation(hotelOffer.getOfferedPrice());
        return;
    }

    negotiationRound = hotelOffer.getRound() + 1;

    // 2. Maksimum tur aşıldı mı?
    if (negotiationRound > maxNegotiationRounds) {
        if (hotelOffer.getOfferedPrice() <= maxPrice) {
            acceptNegotiation(hotelOffer.getOfferedPrice());  // Bütçe dahilinde → kabul
        } else {
            rejectAndTryNext("Price too high");  // Sonraki adaya geç
        }
        return;
    }

    // 3. Strategy pattern ile karşı teklif hesapla
    double counterPrice = pricingStrategy.counterOffer(
        desiredPrice, maxPrice, negotiationRound, maxNegotiationRounds);

    // 4. Karşı teklifimiz otelin teklifinden fazlaysa → otelin teklifini kabul et
    if (counterPrice >= hotelOffer.getOfferedPrice()) {
        acceptNegotiation(hotelOffer.getOfferedPrice());
        return;
    }

    // 5. Leverage: rakip otel teklifini koz olarak kullan
    RoomProposal competing = getCompetingCandidate();
    String leverageMsg = "";
    if (competing != null && competing.getPricePerNight() < hotelOffer.getOfferedPrice()) {
        leverageMsg = String.format(" We have a competing offer at $%.0f/night.",
            competing.getPricePerNight());
    }

    sendMessage(MessageTypes.MSG_COUNTER_OFFER, counter, hotelRole.getIdentifier());
}
```

**`rejectAndTryNext()` — Sırasal Aday Geçiş Kodu:**

```java
// Mevcut otel adayını reddet ve shortlist'teki bir sonrakine geç.
// Tüm adaylar tükenirse FAILED durumuna geçer.
private void rejectAndTryNext(String reason) {
    // Mevcut otele NEGOTIATE_REJECT mesajı gönder
    sendMessage(MessageTypes.MSG_NEGOTIATE_REJECT, reason, hotelRole.getIdentifier());

    int nextIdx = currentCandidateIndex + 1;
    if (nextIdx < topCandidates.size()) {
        startNegotiationWithCandidate(nextIdx);  // Sonraki adayla pazarlık başlat
    } else {
        state = CustomerState.FAILED;  // Tüm adaylar tükendi
    }
}
```

### 5.5 Otel Tarafında Pazarlık Kararları

**Demand Pressure (Talep Baskısı):**

Otel, oda dolulığuna göre minimum kabul edilebilir fiyatını dinamik olarak ayarlar:

```java
double occupancyRate = 1.0 - ((double) available / total);
double scarcityFactor = occupancyRate * occupancyRate;  // Non-linear artış
effectiveMinPrice = baseMinPrice + (basePrice - baseMinPrice) * scarcityFactor;
```

| Doluluk | scarcityFactor | Sonuç |
|---------|----------------|-------|
| %0 | 0.00 | baseMinPrice (tam indirim mümkün) |
| %50 | 0.25 | Hafif fiyat artışı |
| %75 | 0.56 | Belirgin fiyat artışı |
| %100 (1 oda kaldı) | 1.00 | basePrice (indirim yok) |

**Otel Karar Matrisi:**

| Durum | Karar |
|-------|-------|
| `offeredPrice >= effectiveMinPrice` | Kabul et |
| Son tur (maxRounds) | Final teklif olarak `effectiveMinPrice` gönder |
| Diğer | `calculateHotelCounterOffer()` ile karşı teklif gönder |

**`handleNegotiateStartMessage()` — Otel Pazarlık Başlangıcı Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Process customer's negotiation start request")
public void handleNegotiateStartMessage(Message<NegotiationOffer> message) {
    NegotiationOffer offer = message.getPayload();
    currentNegotiations.put(offer.getProposalId(), 1);  // Pazarlık turunu takip et

    double effectiveMin = getEffectiveMinPrice();  // Doluluk bazlı minimum fiyat

    if (offer.getOfferedPrice() >= effectiveMin) {
        // Müşteri teklifi kabul edilebilir → hemen kabul et
        NegotiationOffer acceptance = new NegotiationOffer(
            offer.getProposalId(), hotelId, hotelName,
            offer.getOfferedPrice(), offer.getOriginalPrice(), ...);
        sendMessage(MessageTypes.MSG_NEGOTIATE_ACCEPT, acceptance, message.getSender());
    } else {
        // Karşı teklif: Strategy pattern ile fiyat hesapla
        double counterPrice = calculateHotelCounterOffer(1, offer.getMaxRounds());
        NegotiationOffer counter = new NegotiationOffer(
            offer.getProposalId(), hotelId, hotelName,
            counterPrice, offer.getOriginalPrice(), ...);
        sendMessage(MessageTypes.MSG_COUNTER_OFFER, counter, message.getSender());
    }
}
```

**`handleCounterOfferMessage()` — Otel Karşı Teklif İşleme Kodu:**

```java
@Action(type = ActionType.LOCAL, description = "Process customer's counter-offer during negotiation")
public void handleCounterOfferMessage(Message<NegotiationOffer> message) {
    NegotiationOffer offer = message.getPayload();
    int round = offer.getRound();
    double effectiveMin = getEffectiveMinPrice();

    if (offer.getOfferedPrice() >= effectiveMin) {
        // Müşteri teklifi kabul edilebilir → NEGOTIATE_ACCEPT gönder
        sendMessage(MessageTypes.MSG_NEGOTIATE_ACCEPT, acceptance, message.getSender());

    } else if (round >= offer.getMaxRounds()) {
        // Son tur: Son teklif olarak effectiveMinPrice gönder
        double finalPrice = effectiveMin;
        sendMessage(MessageTypes.MSG_COUNTER_OFFER, finalOffer, message.getSender());

    } else {
        // Ara tur: Strategy pattern ile yeni karşı teklif hesapla
        double counterPrice = calculateHotelCounterOffer(round, offer.getMaxRounds());
        sendMessage(MessageTypes.MSG_COUNTER_OFFER, counter, message.getSender());
    }
}
```

### 5.6 Pazarlık Mesaj Akışı

```
Customer                                Hotel
   |                                      |
   |-- MSG_NEGOTIATE_START($320) -------->|
   |   "We'd like $320. Competing         |
   |    offer at $350."                    |
   |                                      |-- offeredPrice < effectiveMin
   |<-- MSG_COUNTER_OFFER($420) ----------|   "We can offer $420"
   |                                      |
   |-- $420 > desiredPrice                |
   |-- counter = strategy(round=2)        |
   |-- MSG_COUNTER_OFFER($360) --------->|
   |   "How about $360?"                  |
   |                                      |-- $360 >= effectiveMin
   |<-- MSG_NEGOTIATE_ACCEPT($360) -------|   "Deal! $360/night"
   |                                      |
   |-- acceptNegotiation($360)            |
   |-- MSG_NEGOTIATE_ACCEPT(request) ---->|
   |                                      |-- reserveRoom()
   |<-- MSG_CONFIRM(confirmation) --------|
   |                                      |
   | COMPLETED                            |
```

---

## 6. Fiyatlama Stratejileri

### 6.1 Strategy Pattern

Fiyatlama, Strategy deseni ile soyutlanmıştır:

**Arayüzler:**

| Arayüz | Taraf | Parametreler |
|--------|-------|-------------|
| `BuyerPricingStrategy` | Müşteri | `desiredPrice, maxPrice, round, maxRounds` |
| `SellerPricingStrategy` | Otel | `basePrice, minPrice, flexibility, round, maxRounds` |

Her iki arayüz de `@FunctionalInterface` olarak işaretlenmiştir.

### 6.2 LinearPricingStrategy (Varsayılan)

Her iki arayüzü de implemente eder:

**Alıcı (Müşteri) — Java Kodu:**

```java
// Müşteri her turda desiredPrice'dan maxPrice'a doğru lineer olarak teklifini artırır.
// Round 1: desiredPrice'a yakın teklif (düşük), Round N: maxPrice'a yakın (yüksek).
@Override
public double counterOffer(double desiredPrice, double maxPrice, int round, int maxRounds) {
    double progress = (double) round / maxRounds;  // 0.0 → 1.0 lineer ilerleme
    return desiredPrice + (maxPrice - desiredPrice) * progress;
    // Örnek: desired=$300, max=$450, round=2/5 → progress=0.4
    //        counter = 300 + (450-300) × 0.4 = $360
}
```

**Satıcı (Otel) — Java Kodu:**

```java
// Otel her turda basePrice'dan minPrice'a doğru, flexibility oranında fiyat düşürür.
// flexibility 0.3–0.8 arası rastgele atanır (otel oluşturulurken).
// Yüksek flexibility → daha hızlı indirim yapar.
@Override
public double counterOffer(double basePrice, double minPrice, double flexibility,
                            int round, int maxRounds) {
    double progress = (double) round / maxRounds;
    double reduction = (basePrice - minPrice) * progress * flexibility;
    return Math.max(basePrice - reduction, minPrice);  // minPrice altına düşme
    // Örnek: base=$450, min=$382, flex=0.6, round=3/5 → progress=0.6
    //        reduction = (450-382) × 0.6 × 0.6 = $24.48
    //        counter = max(450-24.48, 382) = $425.52
}
```

### 6.3 Genişletilebilirlik

Yeni stratejiler `BuyerPricingStrategy` veya `SellerPricingStrategy` implement ederek eklenebilir (örn: `AggressivePricingStrategy`, `ExponentialPricingStrategy`).

---

## 7. Directory Facilitator (DF)

**Dosya:** `df/DirectoryFacilitator.java`

SCOP `Environment` sınıfını extend eder. FIPA standartlarındaki "yellow pages" servisine benzer şekilde, otellerin kendilerini kayıt ettirdiği ve müşterilerin arama yaptığı merkezi bir servis sunar.

### 7.1 Kayıt

Her `HotelAgent`, `setup()` aşamasında DF'ye bir `DFEntry` kaydeder:

```java
DFEntry entry = new DFEntry(agentName, agentName, hotelId, hotelName, location, rank, basePrice);
df.register(entry);
```

**`register()` Kodu:**

```java
// synchronized: Eş zamanlı kayıt denemelerinde thread safety sağlar.
// ConcurrentHashMap'e agentId → DFEntry çifti eklenir.
public synchronized boolean register(DFEntry entry) {
    if (entry == null || entry.getAgentId() == null) return false;
    registry.put(entry.getAgentId(), entry);  // agentId anahtar, DFEntry değer
    return true;
}
```

### 7.2 Arama

`CustomerRole`, arama kriterlerine göre DF'yi sorgular:

**`search()` Kodu:**

```java
// Tüm kayıtlı oteller üzerinde stream ile filtreleme yapılır.
// Kriterler: konum, minimum yıldız, maksimum fiyat.
// Sonuçlar fiyata göre artan sırada döner.
public List<DFEntry> search(String location, Integer minRank, Double maxPrice) {
    List<DFEntry> results = registry.values().stream()
        .filter(entry -> entry.matches(location, minRank, maxPrice))  // Kriterlere uyan otellar
        .sorted(Comparator.comparingDouble(DFEntry::getBasePrice))    // Ucuzdan pahalıya sırala
        .collect(Collectors.toList());
    return results;
}
```

### 7.3 Registry

`ConcurrentHashMap` tabanlı, thread-safe bir registry kullanır.

```java
// ConcurrentHashMap: Birden fazla otel aynı anda kayıt olabilir.
// Lock-free read, segment-based write garantisi sağlar.
private final Map<String, DFEntry> registry = new ConcurrentHashMap<>();
```

---

## 8. Ağ Topolojisi

**Dosya:** `HotelReservationPlayground.java` (regenerateNetwork, generateTopologyCsv)

SCOP'un `NetworkEnvironment` ve JGraphT kütüphanesi kullanılarak ajan arası ağ topolojisi oluşturulur.

### 8.1 Topoloji Kuralları

| Bağlantı Tipi | Kural |
|--------------|-------|
| Customer <-> Hotel | Her müşteri her otele bağlıdır |
| Hotel <-> Hotel | Aynı şehirdeki oteller birbirine bağlıdır |
| Customer <-> Customer | Bağlantı yok |

### 8.2 Dinamik CSV Üretimi

Topoloji, runtime'da CSV dosyasına yazılır ve `NetworkEnvironment.generateNetwork()` ile yüklenir:

**`generateTopologyCsv()` Kodu:**

```java
// Ajan listesinden CSV topoloji dosyası oluşturur.
// Geçici dosya kullanılır (JVM kapandığında silinir).
private Path generateTopologyCsv() throws IOException {
    Path csvPath = Files.createTempFile("hotel-network-", ".csv");
    csvPath.toFile().deleteOnExit();  // JVM'den çıkışta temizle

    List<String> lines = new ArrayList<>();
    lines.add("from,to");  // CSV başlığı

    // Kural 1: Her müşteri ↔ her otel bağlantısı (tam bipartite)
    for (CustomerAgent customer : customerAgents) {
        for (HotelAgent hotel : hotelAgents) {
            lines.add(customer.getName() + "," + hotel.getName());
        }
    }

    // Kural 2: Aynı şehirdeki oteller arası bağlantı
    for (int i = 0; i < hotelAgents.size(); i++) {
        for (int j = i + 1; j < hotelAgents.size(); j++) {
            HotelAgent h1 = hotelAgents.get(i);
            HotelAgent h2 = hotelAgents.get(j);
            if (h1.getLocation().equalsIgnoreCase(h2.getLocation())) {
                lines.add(h1.getName() + "," + h2.getName());
            }
        }
    }

    Files.write(csvPath, lines);
    return csvPath;
}
```

**`regenerateNetwork()` Kodu:**

```java
// CSV dosyasını SCOP'un NetworkEnvironment'ına yükler ve JGraphT graph oluşturur.
private void regenerateNetwork() {
    Path csvPath = generateTopologyCsv();
    hotelEnv.generateNetwork(csvPath, "from", "to", ',');  // SCOP'un graph builder'ı
    logNetworkMetrics();  // Ağ metriklerini logla (degree, density, clustering, vb.)
}
```

**CSV Çıktı Örneği:**

```csv
from,to
Customer-1,Hotel-h001
Customer-1,Hotel-h002
...
Hotel-h001,Hotel-h002     (aynı şehir: Istanbul)
```

### 8.3 Ağ Metrikleri

Hesaplanan metrikler:
- Node sayısı, Edge sayısı, Connected components
- Ortalama derece, Yoğunluk
- Clustering coefficient, Ortalama yol uzunluğu, Çap
- Small-world özelliği

---

## 9. REST API Endpoint'leri

### 9.1 Simülasyon Kontrolü (`SimulationController`)

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| POST | `/api/simulation?action=setup` | Playground'u oluşturur, ajanları yükler |
| POST | `/api/simulation?action=run` | Simülasyonu başlatır, tüm aramaları tetikler |
| POST | `/api/simulation?action=pause` | Simülasyonu duraklatır |
| POST | `/api/simulation?action=stop` | Simülasyonu durdurur |
| GET | `/api/simulation/status` | Durum: state, tick, ajanSayısı, kayıtlıOtel, allDone |

### 9.2 Müşteri Durumu (`CustomerStatusController`)

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| GET | `/api/customers/status` | Tüm müşterilerin durumu |
| GET | `/api/customer/{id}/status` | Tek müşteri durumu (state, proposals, negotiation, confirmation) |

### 9.3 Ağ Topolojisi (`TopologyController`)

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| GET | `/api/network/topology` | Node'lar (zenginleştirilmiş), Edge'ler |
| GET | `/api/network/metrics` | Ağ metrikleri (NetworkMetricsDTO) |

### 9.4 Aktivite Logları (`ActivityController`)

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| GET | `/api/activity?since=X` | X timestamp'inden sonraki ajan etkileşimleri |

**`ActivityLog` Kodu (paylaşılmış ajan log sistemi):**

```java
// Tüm ajan-ajan etkileşimlerini kaydeden merkezi log.
// Dashboard bu veriyi /api/activity endpoint'inden çeker.
public final class ActivityLog {

    // Java 16 Record: timestamp, from, to, type, detail alanları
    public record Entry(long timestamp, String from, String to, String type, String detail) {}

    // synchronizedList: Birden fazla ajan aynı anda log yazabilir
    private static final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    // Her role sınıfından çağrılır (CFP, PROPOSAL, NEGOTIATE, CONFIRM, vb.)
    public static void log(String from, String to, String type, String detail) {
        entries.add(new Entry(System.currentTimeMillis(), from, to, type, detail));
    }

    // Dashboard polling: belirli bir timestamp'den sonraki logları getir
    public static List<Entry> getEntriesSince(long sinceTimestamp) {
        return entries.stream()
            .filter(e -> e.timestamp() > sinceTimestamp)
            .toList();
    }
}
```

### 9.5 Ajan İşlemleri (`AgentController`)

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| GET | `/api/agents` | Tüm ajan listesi (isim, tip, LLM bilgisi) |
| GET | `/api/agents/{id}/prompt` | TNSAI annotation'larından üretilen system prompt |
| GET | `/api/agents/{id}/log` | Ajanın ActivityLog geçmişi |
| POST | `/api/agents/{id}/chat` | Ajan ile sohbet (Conversation role) |
| GET | `/api/df/entries` | DF'deki tüm kayıtlar |

### 9.6 Veri API'si (`HotelDataController`)

| Metod | Endpoint | Açıklama |
|-------|----------|----------|
| GET | `/api/hotels` veya `/api/data/hotels` | Tüm oteller |
| GET | `/api/hotels/{id}` | ID ile otel |
| GET | `/api/hotels/search?city=X&minRank=Y&maxPrice=Z` | Otel arama |
| GET | `/api/customers` | Tüm müşteriler |
| GET | `/api/customers/{id}` | ID ile müşteri |
| GET | `/api/customers/search?city=X&minRank=Y&maxPrice=Z` | Müşteri arama |
| GET | `/api/cities` veya `/api/data/hotels/cities` | Mevcut şehirler |

---

## 10. SCOP Framework Kullanımı

### 10.1 Temel SCOP Sınıfları ve Kullanım Yerleri

| SCOP Sınıfı | Projede Karşılığı | Açıklama |
|-------------|-------------------|----------|
| `Playground` | `HotelReservationPlayground` | Simülasyon kapsayıcısı; ajan ve ortam yaşam döngüsünü yönetir |
| `Agent` | `CustomerAgent`, `HotelAgent`, `DataFetcherAgent` | Otonom ajan birimleri |
| `Role` | `CustomerRole`, `HotelProviderRole`, `DataFetcherRole` | Ajan davranışlarını tanımlar |
| `Environment` | `DirectoryFacilitator` | Paylaşılmış ortam (sarı sayfalar) |
| `NetworkEnvironment` | `HotelEnv` | JGraphT tabanlı ağ topolojisi |
| `Message<T>` | CNP mesajları | Tipler arası iletişim |
| `Conversation` | Her ajan | Chat desteği |
| `Configurator` | `PlaygroundHolder.setup()` | config.json'dan parametre yükleme |
| `ExecutionState` | `PlaygroundHolder` | RUNNING, PAUSED, ENDED |

### 10.2 Playground Yaşam Döngüsü

```
PlaygroundHolder.setup()
  |-- Configurator.load("config.json")
  |-- new HotelReservationPlayground()
  |-- executor.submit(playground)      // ayrı thread'de başlatır
  |-- playground.getPausedStateLatch().await()  // setup tamamlanana kadar bekle

Playground.setup() [executor thread içinde]
  |-- create(NetworkEnvironment)
  |-- create(DirectoryFacilitator)
  |-- create(DataFetcherAgent)
  |-- createHotelAgents()     // API'den veri çek, HotelAgent oluştur
  |-- createCustomerAgents()  // API'den veri çek, CustomerAgent oluştur
  |-- regenerateNetwork()     // CSV üret, topoloji yükle

PlaygroundHolder.run()
  |-- playground.setExecutionState(RUNNING)
  |-- playground.triggerAllSearches()  // CNP başlar
```

**`setup()` Kodu (HotelReservationPlayground):**

```java
@Override
protected void setup() {
    // 1. SCOP NetworkEnvironment: JGraphT tabanlı graph topolojisi + mesajlaşma
    hotelEnv = create(new NetworkEnvironment("HotelEnv"));

    // 2. Directory Facilitator: Sarı sayfalar servisi (SCOP Environment)
    directoryFacilitator = create(new DirectoryFacilitator("DF"));

    // 3. DataFetcher: API'den otel/müşteri verisi çeken yardımcı ajan
    dataFetcherAgent = create(new DataFetcherAgent(API_PORT));
    hotelEnv.add(dataFetcherAgent);  // NetworkEnvironment'a ekle

    // 4. Otel ajanları: API'den veri çek, her otel için HotelAgent oluştur
    createHotelAgents();

    // 5. Müşteri ajanları: API'den veri çek, her müşteri için CustomerAgent oluştur
    createCustomerAgents();

    // 6. Topoloji: CSV üret → JGraphT graph yükle → metrikler hesapla
    regenerateNetwork();
}
```

**`createHotelAgents()` — Otel Ajan Oluşturma Kodu:**

```java
private void createHotelAgents() {
    // DataFetcherRole üzerinden API'den tüm otelleri çek
    DataFetcherRole fetcherRole = dataFetcherAgent.as(DataFetcherRole.class);
    List<Hotel> hotels = fetcherRole.fetchAllHotels();

    for (Hotel hotel : hotels) {
        // SCOP create(): Playground'a ajan kaydeder ve setup()'ını çağırır
        HotelAgent agent = create(new HotelAgent(
            hotel.getId(), hotel.getName(), hotel.getCity(),
            hotel.getRank(), hotel.getPricePerNight(), hotel.getTotalRooms()
        ));
        hotelEnv.add(agent);      // NetworkEnvironment graph'ına ekle
        hotelAgents.add(agent);   // İç listeye kaydet
    }
}
```

**`triggerAllSearches()` — CNP Toplu Başlatma Kodu:**

```java
// PlaygroundHolder.run() tarafından çağrılır.
// Tüm müşteri ajanlarının CNP aramasını eş zamanlı olarak tetikler.
public void triggerAllSearches() {
    for (CustomerAgent customer : customerAgents) {
        customer.startSearch();  // Her müşteri bağımsız CNP akışı başlatır
    }
}
```

**`fetchCustomerSpecs()` — API Fallback Kodu:**

```java
// Önce API'den müşteri verisi çek, başarısız olursa yerel repository'ye düş
private List<CustomerSpec> fetchCustomerSpecs() {
    try {
        DataFetcherRole fetcherRole = dataFetcherAgent.as(DataFetcherRole.class);
        List<CustomerSpec> customers = fetcherRole.fetchAllCustomers();
        if (!customers.isEmpty()) return customers;  // API başarılı
    } catch (Exception e) { /* API erişilemez */ }

    // Fallback: JSON dosyasından doğrudan yükle
    CustomerRepository repo = new CustomerRepository();
    repo.initialize();
    return repo.findAll();
}
```

### 10.3 Mesajlaşma Modeli

SCOP'un `Role.sendMessage(type, payload, receiver)` metodu kullanılır:

```java
// CustomerRole içinde:
sendMessage(MessageTypes.MSG_CFP, query, hotelRole.getIdentifier());

// HotelProviderRole içinde:
sendMessage(MessageTypes.MSG_PROPOSAL, proposal, customer);
```

Mesajlar `Role.getIdentifier()` ile adreslenir ve SCOP altyapısı üzerinden iletilir.

### 10.4 Ajan Keşfetme

```java
// Playground üzerinden tipe göre ajan bul:
DirectoryFacilitator df = getPlayground().getAgent(DirectoryFacilitator.class, "DF");

// Role içinden ajan bul:
HotelAgent hotel = getAgent(HotelAgent.class, "Hotel-h001");

// İsimle ajan bul:
Agent agent = playground.findAgent("Customer-1");
```

---

## 11. TNSAI Entegrasyonu

### 11.1 Kullanılan Annotation'lar

| Annotation | Kullanım Yeri | Amaç |
|------------|---------------|------|
| `@AgentSpec` | `CustomerAgent`, `HotelAgent` | Ajan açıklaması ve LLM konfigürasyonu |
| `@LLMSpec` | Agent ve Role sınıfları | LLM provider, model, temperature |
| `@RoleSpec` | `CustomerRole`, `HotelProviderRole`, `DataFetcherRole` | Rol açıklaması ve sorumluluklar |
| `@Responsibility` | `@RoleSpec` içinde | Sorumluluk alanları ve action bağlantıları |
| `@Action` | Rol metotları | Action tipi (LOCAL, WEB_SERVICE) ve açıklama |
| `@WebService` | `DataFetcherRole` metotları | HTTP endpoint, method, timeout |
| `@State` | Rol alanları | Ajan durumu açıklaması |
| `@Communication` | `CustomerRole`, `HotelProviderRole` | İletişim stili (ton, dil) |
| `@BeforeAction` | `CustomerRole`, `HotelProviderRole` | Action öncesi cross-cutting concern (validasyon, strateji, fiyat ayarlama) |
| `@AfterAction` | `CustomerRole`, `HotelProviderRole` | Action sonrası cross-cutting concern (loglama, analitik, audit) |

### 11.2 LLM Konfigürasyonu

| Ajan/Rol | Provider | Model | Temperature | Neden |
|----------|----------|-------|-------------|-------|
| CustomerAgent / CustomerRole | OLLAMA | glm-4.7:cloud | 0.7 | Daha yaratıcı, esnek iletişim |
| HotelAgent / HotelProviderRole | OLLAMA | minimax-m2.1:cloud | 0.5 | Daha tutarlı, profesyonel yanıt |

### 11.3 SCOPBridge Kullanımı

`AgentController.getAgentPrompt()` endpoint'inde, TNSAI annotation'larından otomatik system prompt üretilir:

```java
SCOPBridge bridge = SCOPBridge.getInstance();
String prompt = bridge.buildSystemPromptFromAnnotations(role);
```

Bu, `@RoleSpec`, `@Responsibility`, `@State` ve `@Communication` annotation'larını analiz ederek LLM için yapılandırılmış bir prompt oluşturur.

### 11.4 Action Tipleri

| Tip | Kullanım | Örnek |
|-----|----------|-------|
| `ActionType.LOCAL` | Yerel işlemler (mesaj gönderme, değerlendirme) | `startSearch()`, `handleCFPMessage()` |
| `ActionType.WEB_SERVICE` | HTTP API çağrısı | `fetchAllHotels()`, `fetchAllCustomers()` |

### 11.5 @BeforeAction / @AfterAction Hook Sistemi

TNSAI framework'ünün **Aspect-Oriented Programming (AOP)** benzeri hook mekanizması, `@Action` metotlarına cross-cutting concern'ler eklemeyi sağlar. Hook'lar `ActionParams` nesnesi üzerinden veri alışverişi yapar.

#### Mimari Yapı

```
ActionParams oluştur → @BeforeAction(params) → @Action(params kullan) → @AfterAction(params)
```

- **`@BeforeAction("actionName")`**: Action çalışmadan önce tetiklenir. `ActionParams` döndürür, parametrelere veri ekleyebilir (validasyon, fiyat ayarlama, strateji).
- **`@AfterAction("actionName")`**: Action çalıştıktan sonra tetiklenir. `ActionParams` alır ama döndürmez (loglama, analitik, audit).
- **`ActionParams`**: Hook'lar ile action arasında veri taşıyan key-value map. `set()`, `getString()`, `getDouble()`, `getInt()`, `getBoolean()`, `has()` metotları sunar.

#### CustomerRole Hook'ları (4 adet)

| # | Hook | Hedef Action | Amaç | ActionParams |
|---|------|-------------|------|--------------|
| 1 | `@BeforeAction("broadcastCFP")` | `broadcastCFP()` | **Sezonsal fiyat ayarlama** — Ayı kontrol edip maxPrice'ı ayarlar | **Giriş:** — **Çıkış:** `effectiveMaxPrice`, `seasonLabel` |
| 2 | `@AfterAction("handleProposalMessage")` | `handleProposalMessage()` | **Piyasa analitiği** — Ortalama/min/max fiyat takibi, anomali tespiti | **Giriş:** `proposalPrice`, `hotelName` **Çıkış:** — |
| 3 | `@BeforeAction("startNegotiationWithCandidate")` | `startNegotiationWithCandidate()` | **Müzakere stratejisi** — Agresiflik faktörü ve ilk teklif hesabı | **Giriş:** `candidatePrice`, `candidateCount` **Çıkış:** `aggressiveness`, `offerPrice`, `strategyLabel` |
| 4 | `@AfterAction("handleConfirmMessage")` | `handleConfirmMessage()` | **Denetim izi (audit trail)** — Tasarruf hesabı, piyasa sinyali | **Giriş:** `finalPrice`, `originalPrice`, `hotelName` **Çıkış:** — |

**Detaylı Açıklamalar:**

**1. `beforeBroadcastCFP()` — Sezonsal Fiyat Ayarlama Kodu:**

```java
@BeforeAction("broadcastCFP")
private ActionParams beforeBroadcastCFP(ActionParams params) {
    Month currentMonth = LocalDate.now().getMonth();
    double effectiveMaxPrice = maxPrice;
    String seasonLabel;

    switch (currentMonth) {
        case JUNE, JULY, AUGUST -> {
            effectiveMaxPrice = maxPrice * 1.15;  // Yaz: +15%
            seasonLabel = "SUMMER (+15%)";
        }
        case DECEMBER -> {
            effectiveMaxPrice = maxPrice * 1.20;  // Tatil: +20%
            seasonLabel = "HOLIDAY (+20%)";
        }
        case JANUARY, FEBRUARY, MARCH, NOVEMBER -> {
            effectiveMaxPrice = maxPrice * 0.90;  // Düşük sezon: -10%
            seasonLabel = "OFF_SEASON (-10%)";
        }
        default -> seasonLabel = "SHOULDER (no change)";  // Geçiş dönemi
    }

    // ActionParams'a hesaplanan değerleri yaz → broadcastCFP() okuyacak
    params.set("effectiveMaxPrice", Math.round(effectiveMaxPrice * 100.0) / 100.0);
    params.set("seasonLabel", seasonLabel);
    return params;
}
```
CFP'ye gönderilen `effectiveMaxPrice`, sezona göre dinamik olarak belirlenir. Bu sayede müşteri ajanları gerçekçi bütçe limitleriyle otellere teklif çağrısı yapar.

**2. `afterHandleProposalMessage()` — Piyasa Analitiği Kodu:**

```java
@AfterAction("handleProposalMessage")
private void afterHandleProposalMessage(ActionParams params) {
    double price = params.getDouble("proposalPrice");   // handleProposalMessage'dan gelen fiyat
    String hotelName = params.getString("hotelName");

    // Running istatistik güncelleme
    totalProposalCount++;
    proposalPriceSum += price;
    lowestProposalPrice = Math.min(lowestProposalPrice, price);
    highestProposalPrice = Math.max(highestProposalPrice, price);

    double avgPrice = proposalPriceSum / totalProposalCount;

    // Anomali tespiti: Fiyat ortalamanın %50 üzerindeyse uyarı
    if (totalProposalCount > 1 && price > avgPrice * 1.5) {
        ActivityLog.log(getOwner().getName(), hotelName, "PRICE_ANOMALY",
            String.format("$%.0f is %.0f%% above market average $%.0f", ...));
    }
}
```
Bu veriler daha sonra `beforeStartNegotiation` hook'u tarafından strateji belirlemede kullanılır.

**3. `beforeStartNegotiation()` — Müzakere Stratejisi Kodu:**

```java
@BeforeAction("startNegotiationWithCandidate")
private ActionParams beforeStartNegotiation(ActionParams params) {
    double marketAvg = totalProposalCount > 0 ? proposalPriceSum / totalProposalCount : maxPrice;
    double candidatePrice = params.getDouble("candidatePrice");
    int candidateCount = params.getInt("candidateCount");

    double aggressiveness = 0.5;  // Başlangıç (baseline)

    // Aday sayısı etkisi: çok aday → agresif, tek aday → muhafazakar
    if (candidateCount >= 3) aggressiveness += 0.15;
    else if (candidateCount == 1) aggressiveness -= 0.20;

    // Fiyat/piyasa karşılaştırması
    if (candidatePrice > marketAvg * 1.1) aggressiveness += 0.15;   // Pahalı → daha agresif
    else if (candidatePrice < marketAvg * 0.9) aggressiveness -= 0.10; // Ucuz → muhafazakar

    aggressiveness = Math.max(0.1, Math.min(0.9, aggressiveness));  // [0.1, 0.9] aralığında tut

    // İlk teklif: Agresiflik yüksekse desiredPrice'a yakın, düşükse candidatePrice'a yakın
    double offerPrice = desiredPrice + (candidatePrice - desiredPrice) * (1.0 - aggressiveness);

    params.set("aggressiveness", aggressiveness);
    params.set("offerPrice", Math.round(offerPrice * 100.0) / 100.0);
    params.set("strategyLabel", aggressiveness >= 0.7 ? "AGGRESSIVE" :
                                 aggressiveness >= 0.4 ? "MODERATE" : "CONSERVATIVE");
    return params;
}
```

**4. `afterHandleConfirmMessage()` — Denetim İzi Kodu:**

```java
@AfterAction("handleConfirmMessage")
private void afterHandleConfirmMessage(ActionParams params) {
    double finalPrice = params.getDouble("finalPrice");
    double originalPrice = params.getDouble("originalPrice");
    double marketAvg = totalProposalCount > 0 ? proposalPriceSum / totalProposalCount : finalPrice;

    // Tasarruf hesabı
    double savings = originalPrice > 0 ? originalPrice - finalPrice : 0;
    double savingsPercent = originalPrice > 0 ? (savings / originalPrice) * 100 : 0;

    // Piyasa sinyali: Son fiyat piyasa ortalamasına göre nerede?
    String marketSignal;
    if (finalPrice < marketAvg * 0.95)      marketSignal = "BELOW_MARKET";  // İyi fiyat
    else if (finalPrice > marketAvg * 1.05) marketSignal = "ABOVE_MARKET";  // Pahalı
    else                                     marketSignal = "AT_MARKET";     // Normal

    ActivityLog.log(getOwner().getName(), params.getString("hotelName"), "AUDIT",
        String.format("Final $%.0f (was $%.0f), saved $%.0f (%.1f%%) — %s (avg $%.0f)",
            finalPrice, originalPrice, savings, savingsPercent, marketSignal, marketAvg));
}
```

#### HotelProviderRole Hook'ları (4 adet)

| # | Hook | Hedef Action | Amaç | ActionParams |
|---|------|-------------|------|--------------|
| 1 | `@BeforeAction("sendProposal")` | `sendProposal()` | **Dinamik fiyatlandırma** — Doluluk oranına göre fiyat ayarlama | **Giriş:** `customerName` **Çıkış:** `dynamicPrice`, `demandLevel`, `occupancyRate` |
| 2 | `@AfterAction("sendProposal")` | `sendProposal()` | **Teklif loglama** — ActivityLog'a dinamik fiyat detayı yazma | **Giriş:** `dynamicPrice`, `demandLevel`, `customerName` **Çıkış:** — |
| 3 | `@BeforeAction("registerWithDF")` | `registerWithDF()` | **Validasyon** — DF kaydı öncesi veri doğrulama | **Giriş:** — **Çıkış:** `valid`, `issues` |
| 4 | `@AfterAction("registerWithDF")` | `registerWithDF()` | **Kayıt loglama** — DF kayıt sonucunu loglama | **Giriş:** `registered`, `issues` **Çıkış:** — |

**Detaylı Açıklamalar:**

**1. `beforeSendProposal()` — Dinamik Fiyatlandırma Kodu:**

```java
@BeforeAction("sendProposal")
private ActionParams beforeSendProposal(ActionParams params) {
    HotelAgent ha = (HotelAgent) getOwner();
    int total = ha.getTotalRooms();
    int avail = ha.getAvailableRooms();
    double occupancyRate = total > 0 ? 1.0 - ((double) avail / total) : 0.0;

    double multiplier;
    String demandLevel;
    if (occupancyRate < 0.3) {
        multiplier = 0.95;           // Düşük talep: %5 indirim
        demandLevel = "LOW";
    } else if (occupancyRate < 0.7) {
        multiplier = 1.0 + (occupancyRate - 0.3) * 0.25;  // Normal: 1.00–1.10 arası
        demandLevel = "NORMAL";
    } else {
        multiplier = 1.10 + (occupancyRate - 0.7) * 0.50;  // Yüksek: 1.10–1.25 arası
        demandLevel = "HIGH";
    }

    double dynamicPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;
    params.set("dynamicPrice", dynamicPrice);   // sendProposal() bu fiyatı kullanacak
    params.set("demandLevel", demandLevel);
    params.set("occupancyRate", occupancyRate);
    return params;
}
```
Hesaplanan `dynamicPrice`, `sendProposal` action'ı tarafından proposal fiyatı olarak kullanılır.

**2. `afterSendProposal()` — Teklif Loglama**
Gönderilen teklifin dinamik fiyat bilgisini ActivityLog'a yazar:
`"Demand: {level} — Base ${base} → Offered ${dynamic}"`

**3. `beforeRegisterWithDF()` — Validasyon Kodu:**

```java
@BeforeAction("registerWithDF")
private ActionParams beforeRegisterWithDF(ActionParams params) {
    HotelAgent ha = (HotelAgent) getOwner();
    boolean valid = true;
    StringBuilder issues = new StringBuilder();

    if (basePrice <= 0)                        { issues.append("basePrice must be > 0; "); valid = false; }
    if (ha.getTotalRooms() <= 0)               { issues.append("totalRooms must be > 0; "); valid = false; }
    if (location == null || location.isBlank()) { issues.append("location must not be null; "); valid = false; }
    if (rank < 1 || rank > 5)                  { issues.append("rank must be 1-5; "); valid = false; }

    params.set("valid", valid);      // registerWithDF() bu flag'i kontrol edecek
    params.set("issues", issues.toString());

    if (!valid) {
        ActivityLog.log(hotelName, "DirectoryFacilitator", "VALIDATION_FAIL", issues.toString());
    }
    return params;
}
```
Validasyon başarısızsa `VALIDATION_FAIL` loglanır ve kayıt engellenir.

**4. `afterRegisterWithDF()` — Kayıt Loglama**
Başarılı kayıtta: `"Hotel (★, $/night) in City"` formatında log
Başarısız kayıtta: Hata sebebiyle birlikte `REGISTER_FAIL` logu

#### Hook Veri Akışı Diyagramı

```
CustomerRole:
  broadcastCFP:
    [beforeBroadcastCFP] ──effectiveMaxPrice──→ [broadcastCFP] ──→ CFP mesajları

  handleProposalMessage:
    [handleProposalMessage] ──proposalPrice,hotelName──→ [afterHandleProposalMessage]
                                                              ↓
                                                        piyasa istatistikleri güncelle

  startNegotiationWithCandidate:
    [beforeStartNegotiation] ──offerPrice,aggressiveness──→ [startNegotiationWithCandidate]
         ↑                                                       ↓
    piyasa istatistikleri                                    NEGOTIATE mesajı

  handleConfirmMessage:
    [handleConfirmMessage] ──finalPrice,originalPrice──→ [afterHandleConfirmMessage]
                                                              ↓
                                                         audit trail

HotelProviderRole:
  sendProposal:
    [beforeSendProposal] ──dynamicPrice,demandLevel──→ [sendProposal] ──→ [afterSendProposal]
         ↑                                                                      ↓
    doluluk oranı                                                          ActivityLog

  registerWithDF:
    [beforeRegisterWithDF] ──valid,issues──→ [registerWithDF] ──→ [afterRegisterWithDF]
         ↑                                        ↓                      ↓
    veri doğrulama                          DF'ye kayıt              sonuç logu
```

---

## 12. Veri Katmanı

### 12.1 Otel Verisi (`hotel-data.json`)

11 otel, 7 şehirde:

| ID | Otel | Şehir | Yıldız | Fiyat | Oda |
|----|------|-------|--------|-------|-----|
| h001 | Grand Istanbul Hotel | Istanbul | 5 | $450 | 2 |
| h002 | Luxury Palace Istanbul | Istanbul | 5 | $400 | 2 |
| h003 | Budget Inn Istanbul | Istanbul | 3 | $150 | 1 |
| h004 | Sea View Resort | Izmir | 4 | $300 | 1 |
| h005 | Ankara Business Hotel | Ankara | 4 | $250 | 2 |
| h006 | Cappadocia Cave Hotel | Nevsehir | 5 | $350 | 1 |
| h007 | Antalya Beach Resort | Antalya | 5 | $500 | 2 |
| h008 | Bodrum Boutique Hotel | Mugla | 4 | $280 | 1 |
| h009 | Istanbul Comfort Hotel | Istanbul | 4 | $280 | 2 |
| h010 | Ankara Plaza Hotel | Ankara | 3 | $180 | 1 |
| h011 | Antalya Garden Hotel | Antalya | 3 | $200 | 1 |

**Toplam oda kapasitesi:** 15 oda, 15 müşteri — kaynak kısıtlaması ve rekabet durumu yaratır.

### 12.2 Müşteri Verisi (`customer-data.json`)

15 müşteri, 6 şehir hedefli:

| ID | Şehir | Min Yıldız | Max Fiyat | Hedef Fiyat (%80) |
|----|-------|------------|-----------|-------------------|
| c001 | Istanbul | 4 | $460 | $368 |
| c002 | Istanbul | 4 | $450 | $360 |
| c003 | Istanbul | 3 | $170 | $136 |
| c004 | Istanbul | 3 | $160 | $128 |
| c005 | Istanbul | 5 | $380 | $304 |
| c006 | Ankara | 3 | $280 | $224 |
| c007 | Ankara | 3 | $260 | $208 |
| c008 | Ankara | 4 | $270 | $216 |
| c009 | Antalya | 3 | $530 | $424 |
| c010 | Antalya | 5 | $520 | $416 |
| c011 | Izmir | 3 | $340 | $272 |
| c012 | Izmir | 3 | $320 | $256 |
| c013 | Nevsehir | 4 | $380 | $304 |
| c014 | Mugla | 3 | $300 | $240 |
| c015 | Mugla | 3 | $290 | $232 |

### 12.3 Rekabet Senaryoları

Veri, doğal rekabet durumları yaratacak şekilde tasarlanmıştır:

- **Istanbul:** 5 müşteri, 4 otel (7 oda) — yoğun rekabet
- **Ankara:** 3 müşteri, 2 otel (3 oda) — tam eşleşme
- **Antalya:** 2 müşteri, 2 otel (3 oda) — rahat
- **Izmir:** 2 müşteri, 1 otel (1 oda) — kısıtlı kaynak, biri başarısız olacak
- **Nevsehir:** 1 müşteri, 1 otel (1 oda) — bire bir
- **Mugla:** 2 müşteri, 1 otel (1 oda) — kısıtlı kaynak, biri başarısız olacak

---

## 13. Yazılım Mühendisliği Değerlendirmesi

### 13.1 Mimari Kalite

**Güçlü Yanlar:**

- **Separation of Concerns:** Agent/Role/Environment/Message katmanları net bir şekilde ayrılmıştır. Ajan sadece yapı ve setup bilgisi taşırken, tüm iş mantığı Role içindedir.
- **Strategy Pattern:** `BuyerPricingStrategy` ve `SellerPricingStrategy` arayüzleri ile fiyatlama algoritması bağımsızdır. Yeni stratejiler mevcut kodu değiştirmeden eklenebilir (Open/Closed prensibi).
- **Single Responsibility:** Her controller tek bir sorumluluk alanına sahiptir (`SimulationController`, `CustomerStatusController`, `TopologyController`, `ActivityController`, `AgentController`).
- **Annotasyon tabanlı deklaratif tanımlama:** TNSAI annotation'ları ile ajan ve rollerin özellikleri koddan ayrı, deklaratif olarak tanımlanmıştır.

**İyileştirme Alanları:**

- **Boş dizin:** `negotiation/` paketi mevcut ancak içinde dosya yok. Temizlenmeli.
- **Javadoc tutarsızlığı:** `HotelReservationPlayground.java:29`'da "Javalin REST server on port 7070" yazıyor; artık Spring Boot 3001 portunda çalıştığı için güncel değil.
- **DataFetcherRole annotation vs gerçeklik:** `@WebService` annotation'larındaki endpoint URL'leri (`localhost:7070`) fiili kullanılan `baseUrl` ile uyuşmuyor. Annotation'lar dekoratif kalıyor.

### 13.2 Eşleşme ve Bağımlılık (Coupling)

**Güçlü Yanlar:**

- Role sınıfları, ajan sınıflarından bağımsızdır. `CustomerRole`, `CustomerAgent`'a özel bir bağımlılık taşımazken, SCOP'un `getOwner()` arayüzü üzerinden çalışır.
- `PlaygroundHolder` Spring Component olarak enjekte edilir, controller'lar constructor injection kullanır.
- `ActivityLog` statik utility sınıfı olarak tasarlanmış, doğrudan enjeksiyona gerek duymadan her yerden erişilebilir.

**İyileştirme Alanları:**

- `HotelProviderRole` içinde `(HotelAgent) getOwner()` şeklinde downcasting yapılıyor (satır 116, 145, 243, 420). Bu, Role ile Agent arasında implicit bir bağımlılık yaratır.
- `getAgent(HotelAgent.class, "Hotel-" + proposalId)` şeklinde string birleştime dayalı ajan keşfetme, agent naming convention'a sıkı bağlıdır.

### 13.3 Thread Safety

**Güçlü Yanlar:**

- `ConcurrentHashMap` ve `Collections.synchronizedSet` kullanımı uygun.
- `HotelAgent.reserveRoom()` `synchronized` olarak çalışıyor.
- `DirectoryFacilitator.register()` ve `deregister()` `synchronized`.
- `ActivityLog.entries` `Collections.synchronizedList` ile korunuyor.
- CFP broadcast sırasında race condition önlenmiş (önce pending kaydı, sonra mesaj gönderimi).

**İyileştirme Alanları:**

- `CustomerRole.evaluateProposals()` içinde `proposals` map'i üzerinde stream işlemleri yapılırken, eş zamanlı yazma riski (düşük ihtimal ama vardır) `ConcurrentHashMap` ile minimize edilmiş olsa da, `snapshot` alarak işlemek daha güvenli olurdu.

### 13.4 Hata Yönetimi

**Güçlü Yanlar:**

- `DataFetcherRole`'de tüm HTTP çağrıları try-catch içinde, boş liste veya null ile graceful fallback yapılıyor.
- `PlaygroundHolder.getStatusMap()` içinde tüm erişimler try-catch ile korunmuş.
- `CustomerRole`'de DF bulunamazsa veya hiç teklif gelmezse `FAILED` state'ine geçiliyor.
- `HotelReservationPlayground.fetchCustomerSpecs()` API başarısız olursa lokal repository'ye fallback yapıyor.

**İyileştirme Alanları:**

- `HotelProviderRole.handleCFPMessage()` içinde otel kriterlere uymuyorsa sessizce ignore'lanır (müşteriyi bilgilendirmiyor). Bu, müşteri tarafında timeout beklenmesine neden olabilir.

### 13.5 Konfigürabilite

**Güçlü Yanlar:**

- `EnvConfig` sınıfı `.env` dosyasından tüm tunable parametreleri merkezi olarak yükler.
- Default değerler ile `.env` dosyası olmadan da çalışır.
- `config.json` ile Playground parametreleri (`TIMEOUT_TICK`) konfigüre edilebilir.

**Konfigürasyon Parametreleri:**

| Parametre | Default | Açıklama |
|-----------|---------|----------|
| `CNP_PROPOSAL_DEADLINE_MS` | 30,000 | Teklif toplama süresi |
| `CNP_MAX_CANDIDATES` | 3 | Shortlist boyutu |
| `CNP_MAX_NEGOTIATION_ROUNDS` | 5 | Maksimum pazarlık turu |
| `API_PORT` | 7070 | API portu (3001 olmalı) |
| `PLAYGROUND_TIMEOUT_TICK` | 100,000 | Playground timeout |
| `PLAYGROUND_STEP_DELAY` | 1,500 | Adım gecikmesi (ms) |

**Not:** `API_PORT` default değeri 7070 iken uygulama 3001'de çalışıyor. `.env` dosyasında `API_PORT=3001` set edilmeli.

### 13.6 Test Edilebilirlik

- Strategy pattern sayesinde fiyatlama algoritmaları izole olarak test edilebilir.
- `DirectoryFacilitator` bağımsız olarak unit test edilebilir.
- Role sınıfları, mock Agent ve Environment ile test edilebilir.
- Mevcut durumda unit veya integration test dosyası bulunmamaktadır.

### 13.7 Genel Değerlendirme

| Kriter | Puan (5 üzerinden) | Not |
|--------|---------------------|-----|
| Mimari | 4.5 | Katmanlı yapı, net sorumluluk ayrımı |
| Kod kalitesi | 4.0 | Temiz, okunabilir; küçük tutarsızlıklar var |
| Thread safety | 4.0 | Büyük ölçüde korunmuş; edge case'ler mevcut |
| Genişletilebilirlik | 4.5 | Strategy pattern, yeni ajan/rol ekleme kolay |
| Hata yönetimi | 3.5 | Graceful fallback var; sessiz ignore durumları mevcut |
| Konfigürabilite | 4.0 | Merkezi EnvConfig; bir default değer tutarsızlığı |
| Dokümantasyon | 3.5 | Javadoc mevcut ama yer yer güncel değil |
| Test | 2.0 | Test dosyası bulunmuyor |

---

*Bu rapor, projenin 2026-02-20 tarihli snapshot'ı üzerinden hazırlanmıştır.*
