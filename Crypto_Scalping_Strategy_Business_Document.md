# CRYPTO SCALPING STRATEGY - BUSINESS DOCUMENT
## Wave Rider Scalping System v1.0

**Document Version:** 1.0  
**Date:** November 14, 2025  
**Strategy Type:** Trend-Following Scalping with DCA  
**Target Market:** Cryptocurrency Futures (Binance/Bybit)  
**Timeframe:** Multi-timeframe (H4/H1 for trend, 1m for execution)

---

## TABLE OF CONTENTS
1. [Executive Summary](#executive-summary)
2. [Strategy Overview](#strategy-overview)
3. [Technical Requirements](#technical-requirements)
4. [Market Selection Rules](#market-selection-rules)
5. [Entry Rules](#entry-rules)
6. [Exit Rules](#exit-rules)
7. [Risk Management](#risk-management)
8. [Trade Management](#trade-management)
9. [Performance Metrics](#performance-metrics)
10. [Implementation Checklist](#implementation-checklist)
11. [Appendix: Technical Indicators](#appendix-technical-indicators)

---

## 1. EXECUTIVE SUMMARY

### Strategy Philosophy
Wave Rider Scalping System is a trend-following scalping strategy designed to capture small price waves within established trends. The system uses multi-timeframe analysis to identify strong trends, then executes rapid entries and exits on 1-minute charts with strict risk management.

### Key Statistics (Target)
- **Win Rate:** 55-65%
- **Risk:Reward Ratio:** 1:1.5 to 1:2
- **Average Holding Time:** 5-20 minutes
- **Max Daily Trades:** 10-15 trades
- **Max Daily Risk:** 3% of account
- **Risk Per Trade:** 1-1.5% of account

### Core Advantages
- ✅ Rides strong trends with minimal pullback risk
- ✅ DCA mechanism to average down during healthy corrections
- ✅ Multi-layer exit strategy for profit maximization
- ✅ Strict filters to avoid choppy/low-liquidity markets
- ✅ Time-based stops to prevent capital tie-up

---

## 2. STRATEGY OVERVIEW

### 2.1 Strategy Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     STRATEGY WORKFLOW                        │
└─────────────────────────────────────────────────────────────┘

Step 1: MARKET SCANNING (H4 & H1)
   ↓
   └─→ Filter trending pairs using EMA 34/89 + ADX + Volume
   
Step 2: TREND CONFIRMATION (H1)
   ↓
   └─→ Verify trend strength and direction
   
Step 3: ENTRY SETUP MONITORING (1m)
   ↓
   └─→ Wait for pullback conditions (Red candle + RSI + ATR)
   
Step 4: SCALED ENTRY EXECUTION
   ↓
   └─→ Enter 1-3 positions with proper spacing
   
Step 5: ACTIVE MANAGEMENT
   ↓
   └─→ Multi-tier TP + Dynamic SL + Time-based exit
   
Step 6: POST-TRADE ANALYSIS
   ↓
   └─→ Log trade metrics and adjust parameters
```

### 2.2 Market Philosophy
- **Only trade with the trend** - Never counter-trend scalp
- **Quality over quantity** - 5 good setups > 20 mediocre setups
- **Respect volatility** - Adjust position size based on ATR
- **Preserve capital** - Cut losses fast, let winners run (within scalping limits)

---

## 3. TECHNICAL REQUIREMENTS

### 3.1 Exchange Requirements
- **Preferred:** Java 17, Spring boot, MySQL, Binance Futures (Binance futures connector java lib), Mapstruct, Lombok, Spring data JPA, following modern and best practices techstacks.
- **API Requirements:** 
  - Market data streaming (WebSocket)
  - Order execution API with low latency (<100ms)
  - Position management
- **Leverage:** 3-5x (for position sizing flexibility, not aggressive leverage)

### 3.2 Data Requirements
| Timeframe | Indicators Needed | Candle History |
|-----------|-------------------|----------------|
| H4 | EMA 34, EMA 89, ADX, Volume | 200 candles |
| H1 | EMA 34, EMA 89, ADX, Volume, RSI | 200 candles |
| 1m | EMA 21, RSI 14, ATR 14, Volume | 500 candles |

### 3.3 System Requirements
- **Latency:** <200ms to exchange
- **Uptime:** 99%+ during trading hours
- **Compute:** Ability to monitor 10-20 pairs simultaneously
- **Storage:** Trade logs, historical data for backtesting

### 3.4 Technical Indicators Configuration

```python
# H4 & H1 Timeframe
EMA_FAST = 34
EMA_SLOW = 89
ADX_PERIOD = 14
ADX_THRESHOLD = 25
VOLUME_MA_PERIOD = 20

# 1m Timeframe  
EMA_ENTRY = 21
RSI_PERIOD = 14
RSI_LOWER_BOUND = 50
RSI_UPPER_BOUND = 80
RSI_OVERBOUGHT = 75
RSI_EXTREME_OVERBOUGHT = 80
ATR_PERIOD = 14

# Bollinger Bands (for filter)
BB_PERIOD = 20
BB_STD = 2
BB_SQUEEZE_THRESHOLD = 0.02  # 2% bandwidth
```

---

## 4. MARKET SELECTION RULES

### 4.1 Primary Trend Filter (H4 Timeframe)

**BULLISH TREND Criteria (ALL must be met):**
1. **EMA Alignment:** `Close > EMA34 > EMA89`
2. **EMA Separation:** `(EMA34 - EMA89) / EMA89 > 0.015` (1.5% minimum gap)
3. **ADX Strength:** `ADX > 25`
4. **Price Position:** Current price within 5% of EMA34
5. **Bollinger Band:** NOT in squeeze (bandwidth > 2%)

**BEARISH TREND Criteria (ALL must be met):**
1. **EMA Alignment:** `Close < EMA34 < EMA89`
2. **EMA Separation:** `(EMA89 - EMA34) / EMA89 > 0.015`
3. **ADX Strength:** `ADX > 25`
4. **Price Position:** Current price within 5% of EMA34
5. **Bollinger Band:** NOT in squeeze

### 4.2 Secondary Trend Confirmation (H1 Timeframe)

**Additional H1 Filters:**
1. **EMA Alignment matches H4 direction**
2. **ADX > 20** (slightly relaxed vs H4)
3. **Volume confirmation:** Current volume > 80% of 20-period volume MA
4. **RSI not extreme:** 
   - For LONG: RSI H1 < 75
   - For SHORT: RSI H1 > 25

### 4.3 Pair Selection Priority

**Tier 1 Pairs (Highest Priority):**
- BTC/USDT, ETH/USDT, BNB/USDT, SOL/USDT, XRP/USDT

**Tier 2 Pairs (Medium Priority):**
- ADA/USDT, AVAX/USDT, MATIC/USDT, DOT/USDT, LINK/USDT

**Tier 3 Pairs (Low Priority):**
- Top 30 by market cap, excluding stablecoins and meme coins

**Blacklist Conditions:**
- 24h volume < $100M
- Spread > 0.1%
- Recent exchange issues or delistings
- Excessive funding rate (> 0.1% or < -0.1%)

### 4.4 Market Condition Avoidance

**DO NOT TRADE during:**
- ±30 minutes of major economic news (FOMC, CPI, NFP, etc.)
- BTC price movement > 2% in last 5 minutes (wait for stabilization)
- Exchange maintenance windows
- First 15 minutes after market open/close (if applicable)
- Network congestion (gas fees spike, slow confirmations)

---

## 5. ENTRY RULES

### 5.1 Pre-Entry Conditions (1m Timeframe)

**For LONG Entry (ALL must be TRUE):**
1. ✅ H4 & H1 in BULLISH trend (from Section 4)
2. ✅ Current 1m candle is GREEN (close > open)
3. ✅ Price > EMA21 (1m)
4. ✅ ATR is rising (current ATR > ATR 5 candles ago) BUT not spiking (current ATR < 1.5x ATR MA20)
5. ✅ `50 < RSI < 80` on 1m
6. ✅ No RSI divergence detected (higher price but lower RSI in last 10 candles)
7. ✅ Volume > 50% of volume MA20

**For SHORT Entry (ALL must be TRUE):**
1. ✅ H4 & H1 in BEARISH trend
2. ✅ Current 1m candle is RED
3. ✅ Price < EMA21 (1m)
4. ✅ ATR rising but not spiking
5. ✅ `20 < RSI < 50` on 1m
6. ✅ No RSI divergence (lower price but higher RSI)
7. ✅ Volume > 50% of volume MA20

### 5.2 Entry Trigger

**LONG Entry Trigger:**
- After pre-entry conditions are met AND currently in a GREEN candle
- Wait for FIRST RED candle to CLOSE

**Confirmation:** Wait for x more red candles if unsure

**SHORT Entry Trigger:**
- After pre-entry conditions are met AND currently in RED candle
- Wait for FIRST GREEN candle to CLOSE
- Same exception rules as LONG (inverted)

### 5.3 Scaled Entry System

**Position Sizing Strategy:**

```
Entry #1 (Initial Position):
  └─→ Entry Price: Market order on trigger candle close
  └─→ Condition: First entry as per trigger rules
  
Entry #2 (DCA Level 1):
  └─→ Entry Price: 0.3-0.5% below Entry #1 (LONG) or above (SHORT)
  └─→ Condition: Price retraces BUT still above EMA21 AND RSI still > 45
  
Entry #3 (DCA Level 2):
  └─→ Entry Price: 0.3-0.5% below Entry #2 (LONG) or above (SHORT)  
  └─→ Condition: Price retraces BUT still above EMA21 AND RSI > 40
  └─→ Max Risk: Only if total position still within 1.5% account risk
  
Entry #n (DCA Level n):
  └─→ Entry Price: 0.3-0.5% below Entry #2 (LONG) or above (SHORT)  
  └─→ Condition: Price retraces BUT still above EMA21 AND RSI > 40
  └─→ Max Risk: Only if total position still within 1.5% account risk

n can be configurabled, also the position size
```

**Important Notes:**
- **Maximum n entries per setup**
- **Minimum x% spacing between entries**
- **If price breaks below EMA21 before Entry #2, cancel remaining entries**

### 5.4 Position Size Calculation

**Formula:**
```
Risk Amount = Account Balance × Risk Per Trade (1-1.5%)
Position Size (USDT) = Risk Amount / (Entry Price × Stop Loss %)

Example:
- Account: $10,000
- Risk per trade: 1.5% = $150
- Entry price: $50,000
- Stop loss: 1.5% = $750 (distance from entry to SL)
- Position size: $150 / 0.015 = $10,000 worth (0.2 BTC)
- With 5x leverage: Can use $2,000 margin
```

**Position Limits:**
- Single trade max notional: $500 (small account) to $5,000 (large account)
- Total exposure across all trades: Max 4% of account
- Adjust position size based on ATR volatility (higher ATR = smaller size)

---

## 6. EXIT RULES

### 6.1 Take Profit Strategy (Multi-Tier)

**TP Level 1 (50% of position):**
- **Trigger:** FIRST of these conditions:
  1. First RED candle closes (for LONG) when RSI > 75, OR
  2. Price reaches y% profit from average entry
  3. 15 minutes elapsed with 0.5%+ profit

- **Execution:** Market sell 50% of total position

**TP Level 2 (30% of position):**
- **Trigger:** FIRST of these conditions:
  1. RSI enters overbought zone (>80 for LONG) AND shows rejection wick
  2. Price reaches 1.5-2% profit
  3. Volume suddenly spikes with long wick rejection

- **Execution:** Market sell 30% of remaining position

**TP Level 3 (20% of position):**
- **Trailing Stop:** 0.3% from highest price reached after TP2
- **Max Time:** 30 minutes from initial entry
- **Exit:** Let trailing stop hit OR force close at 30 minutes

### 6.2 Stop Loss Strategy (Dynamic)

**Initial Stop Loss (Worst Case):**
- **LONG:** `Entry Price - (1.5% × Average Entry Price)`
- **SHORT:** `Entry Price + (1.5% × Average Entry Price)`
- **Alternative:** Below recent swing low (H1) - 0.5% buffer, whichever is closer

### 6.3 Emergency Exit Rules

**Force Exit Immediately (Market Order) when:**
1. ⚠️ BTC dumps/pumps >2% in 1 minute (altcoin will follow)
2. ⚠️ EMA21 breaks in opposite direction with strong volume
3. ⚠️ RSI extreme divergence appears (new high/low but RSI weakening)
4. ⚠️ Sudden volume spike >3x average with large rejection wick
6. ⚠️ Loss reaches -2% (should never happen with proper SL, but failsafe)

### 6.4 Partial Exit Strategy Table

| Scenario | Action | Remaining Position | New SL |
|----------|--------|-------------------|---------|
| +0.8% profit OR RSI>75 | Close 50% | 50% | Breakeven |
| +1.5% profit OR RSI>80 | Close 30% more | 20% | TP1 level |
| Trailing stop hit | Close remaining 20% | 0% | N/A |
| 10min no breakeven | Close 50% | 50% | Tighten to -0.8% |
| 20min no breakeven | Close 100% | 0% | N/A |
| 30min max hold | Close all remaining | 0% | N/A |

---

## 7. RISK MANAGEMENT

### 7.1 Per-Trade Risk Parameters

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Risk per trade | 1-1.5% | Balance between growth and preservation |
| Max position value | $500-$5,000 | Based on account size |
| Stop loss distance | 1-1.5% | Tight for scalping, avoid noise |
| Max leverage used | 5x | For size flexibility, not aggression |


### 7.5 Psychological Risk Management

**Pre-Trade Checklist (Mental State):**
- [ ] Not feeling rushed or pressured
- [ ] Not trading to "make back" previous losses
- [ ] Setup meets ALL criteria (not forcing)
- [ ] Clear plan for entry, SL, and TP
- [ ] Ready to accept the 1.5% loss if wrong

**Post-Loss Protocol:**
1. Close trading platform for 10 minutes
2. Document what went wrong (system failure vs market noise)
3. Do NOT open another trade immediately
4. Review if all entry rules were followed

**Post-Win Protocol:**
1. Document what went right
2. Do NOT increase risk due to overconfidence  
3. Maintain same position sizing discipline

---

## 8. TRADE MANAGEMENT

### 8.1 Active Trading Session Management

**Real-Time Monitoring Requirements:**

```python
# Pseudo-code for active monitoring
while trading_session_active:
    for pair in watchlist:
        # Check if pre-entry conditions met
        if check_h4_trend(pair) and check_h1_trend(pair):
            # Monitor 1m for entry trigger
            if check_1m_entry_conditions(pair):
                if entry_trigger_fired(pair):
                    execute_entry(pair, position_size)
                    
    # Monitor open positions every 5 seconds
    for position in open_positions:
        check_tp_levels(position)
        update_dynamic_sl(position)
        check_time_based_exit(position)
        check_emergency_exit_conditions(position)
        
    # Check daily limits
    if daily_loss >= MAX_DAILY_LOSS:
        close_all_positions()
        stop_trading()
```

**Position Monitoring Frequency:**
- **Open positions:** Check every 5 seconds
- **Watchlist scanning:** Every 30 seconds
- **Trend confirmation:** Every 5 minutes

### 8.2 Trade Logging Requirements

**For EVERY Trade, Log:**

```json
{
  "trade_id": "unique_id",
  "timestamp_entry": "2025-11-14T10:30:00Z",
  "timestamp_exit": "2025-11-14T10:45:00Z",
  "pair": "BTC/USDT",
  "direction": "LONG",
  "entry_prices": [50000, 49850, 49700],
  "avg_entry_price": 49850,
  "exit_prices": [50400, 50600, 50550],
  "avg_exit_price": 50517,
  "position_size_usdt": 1000,
  "leverage": 5,
  "profit_loss_usdt": 13.4,
  "profit_loss_percent": 1.34,
  "holding_time_minutes": 15,
  "stop_loss": 49100,
  "take_profit_levels": [50250, 50600, 50800],
  "exit_reason": "TP2_hit",
  "indicators_at_entry": {
    "h4_ema34": 48500,
    "h4_ema89": 47800,
    "h4_adx": 32,
    "h1_rsi": 62,
    "1m_rsi": 58,
    "1m_atr": 45
  },
  "market_conditions": {
    "btc_1h_change": 0.5,
    "volume_ratio": 1.2,
    "spread": 0.05
  },
  "notes": "Perfect setup, followed all rules"
}
```
---

---

## 10. IMPLEMENTATION CHECKLIST

### 10.1 Phase 1: Development (Week 1-2)

**Code Development:**
- [ ] Market data fetching (H4, H1, 1m)
- [ ] Indicator calculations (EMA, RSI, ATR, ADX)
- [ ] Trend filter logic (H4 & H1)
- [ ] Entry signal detection (1m)
- [ ] Position sizing calculator
- [ ] Order execution module
- [ ] Multi-tier TP/SL logic
- [ ] Emergency exit conditions
- [ ] Trade logging system
- [ ] Daily limit enforcement

**Data Infrastructure:**
- [ ] Historical data download (3 months minimum)
- [ ] Real-time WebSocket connection
- [ ] Database for trade logs
- [ ] Backup & redundancy systems

### 10.2 Phase 2: Backtesting (Week 3-4)

**Backtest Requirements:**
- [ ] Minimum 3 months of data
- [ ] Test on at least 5 different pairs
- [ ] Include different market conditions (bull, bear, sideways)
- [ ] Model realistic slippage (0.05-0.1%)
- [ ] Model realistic fees (0.05% maker/taker)
- [ ] Generate performance report

**Backtest Success Criteria:**
- ✅ Win rate >55%
- ✅ Profit factor >1.5
- ✅ Max drawdown <15%
- ✅ Positive returns in 2 out of 3 months tested

### 10.3 Phase 3: Paper Trading (Week 5-6)

**Paper Trading Setup:**
- [ ] Connect to testnet or paper trading account
- [ ] Run strategy in real-time with live data
- [ ] Monitor for 2 weeks minimum
- [ ] Log ALL trades as if live
- [ ] Test system under different market conditions

**Paper Trading Success Criteria:**
- ✅ System runs without errors for 2 weeks
- ✅ Performance metrics match backtest (±10%)
- ✅ No missed signals or execution issues
- ✅ All safety mechanisms work correctly
- ✅ Confident in understanding all system behaviors

### 10.4 Phase 4: Live Trading (Week 7+)

**Go-Live Checklist:**
- [ ] Start with MINIMUM position sizes (10-20% of planned)
- [ ] Trade only 1-2 pairs initially
- [ ] Monitor every trade manually for first week
- [ ] Set strict daily loss limit (-2% for first month)
- [ ] Keep detailed notes on emotions and decisions
- [ ] Compare live vs paper performance daily

**Gradual Scale-Up Plan:**
- Week 1-2: 20% position size, max 2 pairs
- Week 3-4: 40% position size, max 4 pairs (if performance good)
- Week 5-6: 60% position size, max 6 pairs
- Week 7-8: 80% position size, max 8 pairs
- Week 9+: 100% position size, max 10 pairs (if all metrics met)

**Live Trading Success Gates:**
- ✅ Win rate >55% over 50+ live trades
- ✅ No losing weeks in first month
- ✅ All risk limits respected 100% of time
- ✅ Emotional control maintained (no revenge trading)
- ✅ System stability (no critical bugs)

---

## 11. APPENDIX: TECHNICAL INDICATORS

### 11.1 Exponential Moving Average (EMA)

**Formula:**
```
EMA(t) = Price(t) × α + EMA(t-1) × (1 - α)
where α = 2 / (Period + 1)
```

**Usage in Strategy:**
- **EMA 34 & 89 (H4/H1):** Trend direction and strength
- **EMA 21 (1m):** Dynamic support/resistance for entries

**Interpretation:**
- Price > EMA = Bullish bias
- EMA 34 > EMA 89 = Uptrend confirmed
- Distance between EMAs = Trend strength

### 11.2 Relative Strength Index (RSI)

**Formula:**
```
RS = Average Gain / Average Loss (over Period)
RSI = 100 - (100 / (1 + RS))
```

**Usage in Strategy:**
- **1m RSI:** Entry timing and exit signals
- **H1 RSI:** Trend confirmation filter

**Levels:**
- 0-30: Oversold
- 30-50: Bearish zone
- 50-70: Bullish zone
- 70-80: Overbought (TP zone)
- 80-100: Extreme overbought (exit)

**Divergence Detection:**
```python
# Bullish divergence: Price makes lower low, RSI makes higher low
# Bearish divergence: Price makes higher high, RSI makes lower high

def detect_divergence(prices, rsi_values, lookback=10):
    price_trend = prices[-1] > prices[-lookback]
    rsi_trend = rsi_values[-1] > rsi_values[-lookback]
    return price_trend != rsi_trend  # Divergence detected
```

### 11.3 Average True Range (ATR)

**Formula:**
```
TR = max[(High - Low), abs(High - Previous Close), abs(Low - Previous Close)]
ATR = EMA of TR over Period
```

**Usage in Strategy:**
- **Volatility filter:** Avoid entry when ATR spiking
- **Stop loss placement:** SL = EMA21 ± (1 × ATR)
- **Position sizing:** Smaller size when ATR high

**Interpretation:**
- Rising ATR = Increasing volatility
- ATR > 1.5x MA = Avoid new entries
- Use ATR as dynamic buffer for stops

### 11.4 Average Directional Index (ADX)

**Formula:**
```
+DI = (Smoothed +DM / ATR) × 100
-DI = (Smoothed -DM / ATR) × 100  
DX = (|+DI - -DI| / |+DI + -DI|) × 100
ADX = EMA of DX over Period
```

**Usage in Strategy:**
- **Trend strength filter:** ADX > 25 = Strong trend
- **Avoid range-bound markets:** ADX < 20 = No trend

**Interpretation:**
- ADX < 20: Weak/no trend (avoid)
- ADX 20-25: Trend developing
- ADX > 25: Strong trend (trade it)
- ADX > 50: Very strong trend (careful of exhaustion)

### 11.5 Volume Analysis

**Volume Moving Average:**
```
Volume_MA = SMA of Volume over 20 periods
```

**Usage in Strategy:**
- **Entry filter:** Current volume > 50% of Volume_MA
- **Exit signal:** Volume spike > 2x average (potential reversal)
- **Trend confirmation:** Rising price + rising volume = healthy trend

**Red Flags:**
- Price up + volume down = Weak rally
- Price down + volume down = Potential reversal
- Sudden volume spike = Climactic move (exit)

### 11.6 Bollinger Bands (Filter Only)

**Formula:**
```
Middle Band = SMA(20)
Upper Band = SMA(20) + (2 × Standard Deviation)
Lower Band = SMA(20) - (2 × Standard Deviation)
Bandwidth = (Upper - Lower) / Middle
```

**Usage in Strategy:**
- **Squeeze detection:** Bandwidth < 2% = Avoid (low volatility)
- **Expansion:** Bandwidth increasing = Good for scalping
- **Not used for entry/exit signals**

---

### Key Components to Implement:

1. **MarketScanner:** H4/H1 trend filtering
2. **SignalDetector:** 1m entry signal detection
3. **RiskManager:** Position sizing, limit enforcement
4. **OrderExecutor:** Entry/exit order management
5. **PositionMonitor:** Real-time position tracking
6. **TradeLogger:** Database logging and reporting
7. **AlertSystem:** Notifications for important events

### Configuration File Structure:
```yaml
# config.yaml
exchange:
  name: binance
  api_key: YOUR_API_KEY
  api_secret: YOUR_SECRET
  testnet: true

strategy:
  timeframes: [4h, 1h, 1m]
  indicators:
    ema_fast: 34
    ema_slow: 89
    rsi_period: 14
    atr_period: 14
    adx_period: 14
  
risk:
  max_daily_loss_pct: 3.0
  max_concurrent_trades: 3
  risk_per_trade_pct: 1.5
  max_leverage: 5
  
```

---

## FINAL CHECKLIST BEFORE LIVE TRADING

- [ ] Backtest shows positive results (>55% WR, >1.5 PF)
- [ ] Paper trading successful for 2+ weeks
- [ ] All risk limits coded and tested
- [ ] Emergency exit conditions work correctly
- [ ] Trade logging captures all required data
- [ ] Notifications set up (Telegram/Discord/Email)
- [ ] Starting with minimal position sizes
- [ ] Daily review process established
- [ ] Mental preparation and discipline plan ready
- [ ] Understand this is a marathon, not a sprint

---

**Remember:** 
- *The strategy is only as good as your discipline in following it*
- *No strategy wins 100% of the time - manage losses well*
- *Protect your capital first, profits second*
- *Quality setups > quantity of trades*

Good luck! 🚀
