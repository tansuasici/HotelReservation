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

**`setup()` Sırasında:**
1. `HotelProviderRole` adopt edilir (CNP participant davranışı)
2. `Conversation` role adopt edilir (chat desteği)
3. Directory Facilitator'a kayıt yapılır (`registerWithDF`)

**Oda Yönetimi:** `reserveRoom()` metodu `synchronized` olarak çalışarak, eş zamanlı erişimde thread safety sağlar.

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

**`setup()` Sırasında:**
1. `CustomerRole` adopt edilir (CNP initiator davranışı)
2. `Conversation` role adopt edilir (chat desteği)
3. `LinearPricingStrategy` ile fiyatlama stratejisi atanır

**Arama Tetikleme:** `startSearch()` metodu, `CustomerRole.startSearch()` çağrısını yapar ve tüm CNP akışını başlatır.

### 2.3 DataFetcherAgent

**Dosya:** `agent/DataFetcherAgent.java`

Yardımcı ajan. Otel ve müşteri verilerini REST API'den çekmekten sorumludur. `DataFetcherRole` adopt eder.

**Not:** Kendi sunucusuna (`localhost:3001`) HTTP çağrısı yapar - bu, ajan çerçevesinin dış veri kaynaklarına erişim modelini (`WEB_SERVICE` pattern) uygulamaktadır.

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

**Alıcı formülü:**
```
counterOffer = desiredPrice + (maxPrice - desiredPrice) * (round / maxRounds)
```
Yorum: Her turda lineer olarak desiredPrice'dan maxPrice'a yaklaşır.

**Satıcı formülü:**
```
reduction = (basePrice - minPrice) * (round / maxRounds) * flexibility
counterOffer = max(basePrice - reduction, minPrice)
```
Yorum: Her turda basePrice'dan minPrice'a doğru, flexibility oranında azalır.

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

### 7.2 Arama

`CustomerRole`, arama kriterlerine göre DF'yi sorgular:

```java
matchingHotels = df.search(desiredLocation, desiredRank, maxPrice);
```

Arama sonuçları fiyata göre sıralanır.

### 7.3 Registry

`ConcurrentHashMap` tabanlı, thread-safe bir registry kullanır.

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
