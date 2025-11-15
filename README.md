# Martiangle Trading Bot - Wave Rider Scalping System v1.0

A professional cryptocurrency scalping bot implementing the Wave Rider strategy for Binance Futures.

## Project Status

**Current Phase:** Phase 1 - Foundation ✅ COMPLETED

### Completed Tasks

- ✅ Spring Boot 3.5.7 project structure with Java 17
- ✅ Maven dependency configuration (Binance, MySQL, Lombok, MapStruct, etc.)
- ✅ Comprehensive `application.yml` with all strategy parameters
- ✅ Database schema design with JPA entities
- ✅ Repository layer for data access
- ✅ Configuration property classes
- ✅ Proper package structure

## Technology Stack

- **Java 17**
- **Spring Boot 3.5.7**
- **MySQL 8** - Trade data persistence
- **Redis** - Caching and real-time data
- **Binance Futures Connector** - Exchange integration
- **TA4J** - Technical analysis library
- **MapStruct** - Object mapping
- **Lombok** - Boilerplate reduction
- **Spring Data JPA** - Data access
- **Spring Boot Actuator** - Monitoring
- **Prometheus** - Metrics

## Project Structure

```
src/main/java/com/bot/gift/x1000/martiangletradingbot/
├── config/                          # Configuration classes
│   ├── ExchangeProperties.java
│   ├── RiskProperties.java
│   └── StrategyProperties.java
├── model/
│   ├── entity/                      # JPA Entities
│   │   ├── Candle.java             # OHLCV data
│   │   ├── Trade.java              # Complete trade records
│   │   ├── Position.java           # Active positions
│   │   ├── MarketCondition.java    # Market analysis snapshots
│   │   └── DailyPerformance.java   # Daily metrics
│   ├── dto/                         # Data Transfer Objects
│   └── enums/                       # Enumerations
│       ├── TradeDirection.java
│       ├── TradeStatus.java
│       ├── ExitReason.java
│       ├── TrendDirection.java
│       ├── Timeframe.java
│       └── OrderType.java
├── repository/                      # JPA Repositories
│   ├── CandleRepository.java
│   ├── TradeRepository.java
│   ├── PositionRepository.java
│   ├── MarketConditionRepository.java
│   └── DailyPerformanceRepository.java
├── service/
│   ├── scanner/                     # Market scanning services
│   ├── signal/                      # Signal detection services
│   ├── risk/                        # Risk management services
│   ├── execution/                   # Order execution services
│   ├── monitoring/                  # Position monitoring services
│   └── notification/                # Alert/notification services
├── indicator/                       # Technical indicator calculators
├── exchange/
│   ├── binance/                     # Binance integration
│   └── model/                       # Exchange-specific models
├── util/                            # Utility classes
└── exception/                       # Custom exceptions
```

## Database Schema

### Tables Created

1. **candles** - OHLCV market data
2. **trades** - Complete trade history
3. **positions** - Active position tracking
4. **market_conditions** - Market analysis snapshots
5. **daily_performance** - Daily performance metrics

## Configuration

All configuration is in `src/main/resources/application.yml`:

- **Strategy Parameters**: EMA, RSI, ATR, ADX, Volume, Bollinger Bands
- **Risk Management**: Position sizing, daily limits, leverage
- **Entry/Exit Rules**: DCA levels, TP/SL, time-based exits
- **Market Selection**: Pair tiers, blacklist criteria
- **Exchange Settings**: Binance API configuration

## Environment Variables Required

```bash
# Database
DB_USERNAME=root
DB_PASSWORD=your_password

# Binance
BINANCE_API_KEY=your_api_key
BINANCE_SECRET_KEY=your_secret_key
BINANCE_TESTNET=true

# Redis (Optional)
REDIS_HOST=localhost
REDIS_PORT=6379

# Notifications (Optional)
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id
```

## Building the Project

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package
mvn clean package

# Run application
java -jar target/Martiangle-Trading-Bot-0.0.1-SNAPSHOT.jar
```

## Next Steps

### Phase 2: Technical Indicators (Week 1)
- [ ] Implement EMA calculator
- [ ] Implement RSI calculator with divergence detection
- [ ] Implement ATR calculator with spike detection
- [ ] Implement ADX calculator
- [ ] Implement Volume MA calculator
- [ ] Implement Bollinger Bands calculator

### Phase 3: Market Analysis (Week 1-2)
- [ ] Create MarketScanner service
- [ ] Create H1 trend confirmation logic
- [ ] Implement pair selection filters
- [ ] Implement market condition avoidance

### Phase 4: Signal Detection (Week 2)
- [ ] Create SignalDetector for LONG entries
- [ ] Create SignalDetector for SHORT entries
- [ ] Implement entry trigger detection

### Phase 5: Risk Management (Week 2)
- [ ] Implement position size calculator
- [ ] Implement daily loss limits
- [ ] Implement concurrent trade limits
- [ ] Implement ATR-based position sizing

## Strategy Overview

**Wave Rider Scalping System** is a trend-following scalping strategy that:

- Uses H4/H1 timeframes for trend identification
- Executes on 1-minute charts
- Implements DCA (Dollar Cost Averaging) for entries
- Uses multi-tier take profit system
- Enforces strict risk management (max 1.5% per trade, 3% daily)
- Targets 55-65% win rate with 1:1.5 to 1:2 risk:reward

## License

Proprietary - All rights reserved

## Author

Trading Bot Development Team
