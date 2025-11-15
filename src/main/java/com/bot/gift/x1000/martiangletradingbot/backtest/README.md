# Backtesting Module

Comprehensive backtesting system for validating the Wave Rider Scalping Strategy on historical data.

## Features

✅ **Realistic Simulation**
- Random slippage between 0.05-0.1%
- Maker fees: 0.02% / Taker fees: 0.04%
- Multi-tier profit taking (50%/30%/20%)
- Trailing stop for final position
- Time-based exits (max 30min hold)

✅ **Performance Metrics**
- Win rate and profit factor
- Average/largest wins and losses
- Maximum drawdown (% and USD)
- Sharpe and Sortino ratios
- R-multiples per trade
- Fee and slippage analysis
- Holding time statistics
- Consecutive win/loss streaks

✅ **Data Sources**
- Database (primary)
- CSV files (import historical data)
- Binance API (placeholder)
- Sample data generation (testing)

## Quick Start

### 1. Run a Quick Backtest (Last 30 Days)

```bash
curl http://localhost:8080/api/backtest/quick?symbol=BTCUSDT&initialBalance=10000
```

### 2. Run a Custom Backtest

```bash
curl -X POST http://localhost:8080/api/backtest/run \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "startTime": "2024-01-01T00:00:00Z",
    "endTime": "2024-03-31T23:59:59Z",
    "initialBalance": 10000
  }'
```

### 3. Get Text Report

```bash
curl -X POST http://localhost:8080/api/backtest/report \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "startTime": "2024-01-01T00:00:00Z",
    "endTime": "2024-03-31T23:59:59Z",
    "initialBalance": 10000
  }'
```

## Loading Historical Data

### Option 1: From Database

The backtest engine will automatically use candle data from the database if available.

```sql
-- Check available data
SELECT timeframe, COUNT(*) as count, MIN(open_time), MAX(close_time)
FROM candle
WHERE symbol = 'BTCUSDT'
GROUP BY timeframe;
```

### Option 2: From CSV Files

Prepare CSV files with format: `timestamp,open,high,low,close,volume`

```csv
1704067200000,42500.00,42800.00,42400.00,42700.00,1250.50
1704067260000,42700.00,42900.00,42650.00,42850.00,980.25
1704067320000,42850.00,43000.00,42800.00,42950.00,1105.75
```

Load via code:
```java
List<Candle> candles = dataLoader.loadFromCsv(
    Path.of("/path/to/BTCUSDT_1m.csv"),
    "BTCUSDT",
    Timeframe.M1
);

// Save to database
candleRepository.saveAll(candles);
```

### Option 3: Sample Data Generation

For testing without historical data:

```java
List<Candle> candles = dataLoader.generateSampleData(
    "BTCUSDT",
    Timeframe.M1,
    50000,  // Number of candles
    50000,  // Starting price
    1       // Trend: 1=up, -1=down, 0=sideways
);
```

## Downloading Historical Data from Binance

### Using Python Script

```python
import requests
import csv
from datetime import datetime, timedelta

def download_klines(symbol, interval, start_date, end_date):
    """Download kline data from Binance"""
    url = "https://api.binance.com/api/v3/klines"

    start_ms = int(start_date.timestamp() * 1000)
    end_ms = int(end_date.timestamp() * 1000)

    all_klines = []
    current_start = start_ms

    while current_start < end_ms:
        params = {
            "symbol": symbol,
            "interval": interval,
            "startTime": current_start,
            "endTime": end_ms,
            "limit": 1000
        }

        response = requests.get(url, params=params)
        klines = response.json()

        if not klines:
            break

        all_klines.extend(klines)
        current_start = klines[-1][0] + 1

        print(f"Downloaded {len(all_klines)} candles...")

    return all_klines

def save_to_csv(klines, filename):
    """Save klines to CSV"""
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['timestamp', 'open', 'high', 'low', 'close', 'volume'])

        for kline in klines:
            writer.writerow([
                kline[0],      # timestamp
                kline[1],      # open
                kline[2],      # high
                kline[3],      # low
                kline[4],      # close
                kline[5]       # volume
            ])

# Download 3 months of data
symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT']
intervals = ['1m', '1h', '4h']

end_date = datetime.now()
start_date = end_date - timedelta(days=90)

for symbol in symbols:
    for interval in intervals:
        print(f"Downloading {symbol} {interval}...")
        klines = download_klines(symbol, interval, start_date, end_date)
        save_to_csv(klines, f"{symbol}_{interval}.csv")
        print(f"Saved {len(klines)} candles to {symbol}_{interval}.csv")
```

## Backtest Results

### Sample Output

```
═══════════════════════════════════════════════════════════════
                    BACKTEST RESULTS
═══════════════════════════════════════════════════════════════

Symbol: BTCUSDT
Period: 2024-01-01T00:00:00Z to 2024-03-31T23:59:59Z
Initial Balance: $10000.00
Final Balance: $12450.00

PERFORMANCE METRICS
───────────────────────────────────────────────────────────────
Total Trades: 145
Winning Trades: 89 (61.4%) ✓
Losing Trades: 56
Win Rate: 61.38% ✓
Profit Factor: 2.15 ✓

PROFIT/LOSS
───────────────────────────────────────────────────────────────
Net Profit: $2450.00 (24.50%)
Average Win: $45.50
Average Loss: $28.75
Largest Win: $185.00
Largest Loss: $75.50
Average R-Multiple: 1.85R

RISK METRICS
───────────────────────────────────────────────────────────────
Max Drawdown: 8.50% ($850.00) ✓
Sharpe Ratio: 2.35

TRADE DURATION
───────────────────────────────────────────────────────────────
Average Hold: 845 seconds (14.1 min)
Max Hold: 1800 seconds (30.0 min)
Min Hold: 125 seconds (2.1 min)

COSTS
───────────────────────────────────────────────────────────────
Total Fees: $145.50
Total Slippage: $87.25

CONSISTENCY
───────────────────────────────────────────────────────────────
Max Consecutive Wins: 8
Max Consecutive Losses: 4

═══════════════════════════════════════════════════════════════
OVERALL: ✓ PASSED ALL CRITERIA
═══════════════════════════════════════════════════════════════
```

## Success Criteria

The backtest must meet these criteria to be considered successful:

- ✅ **Win Rate > 55%**
- ✅ **Profit Factor > 1.5**
- ✅ **Max Drawdown < 15%**

If any criterion fails, parameters should be optimized.

## Parameter Optimization

If backtest fails, adjust these parameters in `application.yml`:

### RSI Zones
```yaml
strategy:
  indicators:
    rsi:
      lower-bound: 50  # Try 45-55
      upper-bound: 80  # Try 75-85
```

### Take Profit Levels
```yaml
strategy:
  exit:
    take-profit:
      tp1:
        profit-target-percent: 0.8  # Try 0.6-1.0
      tp2:
        profit-target-percent: 1.5  # Try 1.2-1.8
```

### Stop Loss
```yaml
strategy:
  exit:
    stop-loss:
      initial-percent: 1.5  # Try 1.2-2.0
```

### ADX Thresholds
```yaml
strategy:
  indicators:
    adx:
      h4-threshold: 25  # Try 20-30
      h1-threshold: 20  # Try 15-25
```

## Testing Across Market Conditions

Run backtests on different market periods:

```bash
# Bull market (Jan-Mar 2024)
curl -X POST http://localhost:8080/api/backtest/run \
  -d '{"symbol": "BTCUSDT", "startTime": "2024-01-01T00:00:00Z", "endTime": "2024-03-31T23:59:59Z"}'

# Bear market (Jun-Aug 2023)
curl -X POST http://localhost:8080/api/backtest/run \
  -d '{"symbol": "BTCUSDT", "startTime": "2023-06-01T00:00:00Z", "endTime": "2023-08-31T23:59:59Z"}'

# Sideways market (Sep-Nov 2023)
curl -X POST http://localhost:8080/api/backtest/run \
  -d '{"symbol": "BTCUSDT", "startTime": "2023-09-01T00:00:00Z", "endTime": "2023-11-30T23:59:59Z"}'
```

## Code Structure

```
backtest/
├── BacktestTrade.java           # Trade record with P/L
├── BacktestResult.java          # Performance metrics
├── BacktestPosition.java        # Position simulation
├── BacktestingEngine.java       # Main simulation engine
├── HistoricalDataLoader.java   # Data loading utilities
├── BacktestController.java      # REST API
└── README.md                    # This file
```

## Integration with Trading System

The backtest engine uses the same components as live trading:
- `MarketScannerService` - Trend analysis
- `SignalDetectorService` - Entry signals
- `RiskManagerService` - Position sizing

This ensures backtested results are representative of live trading performance.

## Limitations

- ⚠️ Slippage is simulated randomly (0.05-0.1%)
- ⚠️ Market impact is not modeled
- ⚠️ Assumes infinite liquidity
- ⚠️ Does not account for exchange outages
- ⚠️ Binance API integration is placeholder only

## Next Steps

1. Download 3+ months of historical data for each symbol
2. Run backtests on all 5 pairs (BTC, ETH, BNB, SOL, XRP)
3. Validate results meet success criteria
4. Optimize parameters if needed
5. Proceed to paper trading on Binance Testnet

## Support

For issues or questions about backtesting:
- Check logs in `logs/trading-bot.log`
- Review backtest results in API response
- Adjust parameters in `application.yml`
- Generate sample data if no historical data available
