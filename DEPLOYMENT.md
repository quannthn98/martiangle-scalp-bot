# Wave Rider Scalping Bot - Deployment Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Database Setup](#database-setup)
3. [Configuration](#configuration)
4. [Binance API Setup](#binance-api-setup)
5. [Building & Running](#building--running)
6. [Production Deployment](#production-deployment)
7. [Monitoring & Maintenance](#monitoring--maintenance)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software
- **Java 17** or higher
- **MySQL 8.0** or higher
- **Redis** (optional, for caching)
- **Maven 3.8+** for building

### Recommended Specs
- **CPU**: 2+ cores
- **RAM**: 4GB minimum, 8GB recommended
- **Storage**: 20GB for logs and database
- **Network**: Low latency connection to Binance (<200ms)

---

## Database Setup

### 1. Install MySQL

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install mysql-server

# macOS (using Homebrew)
brew install mysql

# Start MySQL
sudo systemctl start mysql  # Linux
brew services start mysql   # macOS
```

### 2. Create Database and User

```sql
-- Connect to MySQL as root
mysql -u root -p

-- Create database
CREATE DATABASE trading_bot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user (replace with strong password)
CREATE USER 'trading_bot'@'localhost' IDENTIFIED BY 'your_strong_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON trading_bot.* TO 'trading_bot'@'localhost';
FLUSH PRIVILEGES;

-- Verify
SHOW DATABASES;
exit;
```

### 3. Test Connection

```bash
mysql -u trading_bot -p trading_bot
```

---

## Configuration

### 1. Environment Variables

Create a `.env` file in the project root:

```bash
# Database Configuration
DB_USERNAME=trading_bot
DB_PASSWORD=your_strong_password
DB_URL=jdbc:mysql://localhost:3306/trading_bot

# Binance API (Testnet for testing)
BINANCE_API_KEY=your_binance_testnet_api_key
BINANCE_SECRET_KEY=your_binance_testnet_secret_key
BINANCE_TESTNET=true
BINANCE_BASE_URL=https://testnet.binancefuture.com
BINANCE_WS_URL=wss://stream.binancefuture.com

# Redis (Optional)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Trading Configuration
TRADING_ACCOUNT_BALANCE=10000

# Notifications (Optional)
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
TELEGRAM_CHAT_ID=your_telegram_chat_id
DISCORD_WEBHOOK_URL=your_discord_webhook_url
```

### 2. Application Configuration

The main configuration is in `src/main/resources/application.yml`. Key settings to review:

```yaml
strategy:
  market-selection:
    tier1-pairs:
      - BTCUSDT
      - ETHUSDT
      # Add/remove pairs as needed

risk:
  risk-per-trade-percent: 1.5      # Risk per trade (1-1.5%)
  max-daily-loss-percent: 3.0      # Max daily loss
  max-concurrent-trades: 3          # Max open positions
  max-position-size-usdt: 5000     # Max position size
```

---

## Binance API Setup

### 1. Create Testnet Account

1. Go to [Binance Testnet](https://testnet.binancefuture.com/)
2. Create an account using your email
3. Login and navigate to API Management
4. Create a new API key
5. **IMPORTANT**: Enable "Futures" trading permissions
6. Save your API Key and Secret Key securely
7. **DO NOT** share these keys publicly

### 2. API Permissions Required

- ✅ Enable Reading
- ✅ Enable Spot & Margin Trading
- ✅ Enable Futures
- ❌ Do NOT enable withdrawals

### 3. Test API Connection

```bash
# Test your API keys
curl -H "X-MBX-APIKEY: YOUR_API_KEY" \
  'https://testnet.binancefuture.com/fapi/v1/account'
```

---

## Building & Running

### 1. Build the Project

```bash
# Clone repository
git clone <repository-url>
cd martiangle-scalp-bot

# Build with Maven
mvn clean package -DskipTests

# Or build with tests
mvn clean package
```

### 2. Run Locally (Development)

```bash
# Using Maven
mvn spring-boot:run

# Or using the JAR
java -jar target/Martiangle-Trading-Bot-0.0.1-SNAPSHOT.jar
```

### 3. Run with Custom Configuration

```bash
java -jar target/Martiangle-Trading-Bot-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=production \
  --trading.account.balance=10000
```

---

## Production Deployment

### Option 1: Systemd Service (Linux)

Create `/etc/systemd/system/trading-bot.service`:

```ini
[Unit]
Description=Wave Rider Trading Bot
After=network.target mysql.service

[Service]
Type=simple
User=trading-bot
WorkingDirectory=/opt/trading-bot
ExecStart=/usr/bin/java -jar /opt/trading-bot/Martiangle-Trading-Bot.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment="DB_USERNAME=trading_bot"
Environment="DB_PASSWORD=your_password"
Environment="BINANCE_API_KEY=your_api_key"
Environment="BINANCE_SECRET_KEY=your_secret_key"

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable trading-bot
sudo systemctl start trading-bot
sudo systemctl status trading-bot
```

### Option 2: Docker Deployment

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/Martiangle-Trading-Bot-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: trading_bot
      MYSQL_USER: trading_bot
      MYSQL_PASSWORD: password
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  trading-bot:
    build: .
    depends_on:
      - mysql
      - redis
    environment:
      DB_USERNAME: trading_bot
      DB_PASSWORD: password
      DB_URL: jdbc:mysql://mysql:3306/trading_bot
      REDIS_HOST: redis
      BINANCE_API_KEY: ${BINANCE_API_KEY}
      BINANCE_SECRET_KEY: ${BINANCE_SECRET_KEY}
    ports:
      - "8080:8080"
    restart: unless-stopped

volumes:
  mysql_data:
```

Deploy:

```bash
docker-compose up -d
```

### Option 3: Cloud Deployment (AWS EC2)

```bash
# 1. Launch EC2 instance (t3.medium recommended)
# 2. Install Java 17
sudo yum install java-17-amazon-corretto

# 3. Install MySQL
sudo yum install mysql-server
sudo systemctl start mysqld

# 4. Copy JAR file
scp target/Martiangle-Trading-Bot-0.0.1-SNAPSHOT.jar ec2-user@your-instance:/opt/

# 5. Create systemd service (see Option 1)
# 6. Configure security groups (allow port 3306 for MySQL if remote)
```

---

## Monitoring & Maintenance

### 1. Application Logs

```bash
# View real-time logs (systemd)
sudo journalctl -u trading-bot -f

# View logs (standalone)
tail -f logs/trading-bot.log

# Search logs for errors
grep ERROR logs/trading-bot.log
```

### 2. Health Check Endpoint

```bash
# Check application health
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/metrics

# View Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### 3. Database Monitoring

```sql
-- Check active positions
SELECT * FROM positions WHERE status IN ('OPEN', 'PARTIALLY_CLOSED');

-- Today's performance
SELECT * FROM daily_performance WHERE trading_date = CURDATE();

-- Recent trades
SELECT trade_id, symbol, direction, profit_loss_usdt, exit_reason
FROM trades
WHERE entry_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY entry_time DESC;
```

### 4. Daily Maintenance Tasks

```bash
# Backup database
mysqldump -u trading_bot -p trading_bot > backup_$(date +%Y%m%d).sql

# Rotate logs
logrotate /etc/logrotate.d/trading-bot

# Check disk space
df -h

# Monitor system resources
htop
```

---

## Troubleshooting

### Issue: Cannot connect to database

```bash
# Check MySQL is running
sudo systemctl status mysql

# Test connection
mysql -u trading_bot -p -h localhost trading_bot

# Check configuration
grep "spring.datasource" src/main/resources/application.yml
```

### Issue: Binance API errors

```bash
# Test API keys
curl -H "X-MBX-APIKEY: YOUR_KEY" \
  'https://testnet.binancefuture.com/fapi/v1/ping'

# Check API permissions in Binance dashboard
# Verify API key is not IP-restricted

# Check logs for specific error codes
grep "Binance" logs/trading-bot.log
```

### Issue: Out of Memory

```bash
# Increase Java heap size
java -Xms2g -Xmx4g -jar Martiangle-Trading-Bot.jar

# Monitor memory usage
jcmd <pid> VM.native_memory summary
```

### Issue: Trading not executing

```bash
# Check trading engine status
curl http://localhost:8080/actuator/metrics/trading.engine.status

# Check risk limits
# Look for "daily limit reached" or "max concurrent trades" in logs
grep "Cannot open new trade" logs/trading-bot.log

# Verify pairs are configured
grep "tier1-pairs" src/main/resources/application.yml
```

---

## Security Best Practices

1. **Never commit API keys to Git**
   - Use environment variables
   - Add `.env` to `.gitignore`

2. **Use strong database passwords**
   - Minimum 16 characters
   - Mix of letters, numbers, symbols

3. **Restrict API access**
   - Enable only required permissions
   - Use IP whitelist if possible
   - Never enable withdrawal permissions

4. **Keep software updated**
   ```bash
   # Update dependencies regularly
   mvn versions:display-dependency-updates
   ```

5. **Monitor for unauthorized access**
   ```bash
   # Check active sessions
   SELECT * FROM mysql.user;

   # Review access logs
   tail -f /var/log/mysql/mysql.log
   ```

---

## Support & Resources

- **Documentation**: [README.md](README.md)
- **Strategy Document**: [Crypto_Scalping_Strategy_Business_Document.md](Crypto_Scalping_Strategy_Business_Document.md)
- **Binance API Docs**: https://binance-docs.github.io/apidocs/futures/en/
- **Spring Boot Docs**: https://docs.spring.io/spring-boot/docs/current/reference/html/

---

## Emergency Procedures

### Stop Trading Immediately

```bash
# Method 1: Shutdown application
sudo systemctl stop trading-bot

# Method 2: Pause via API (if implemented)
curl -X POST http://localhost:8080/api/trading/pause

# Method 3: Kill process
pkill -f "Martiangle-Trading-Bot"
```

### Close All Positions

The application automatically closes all positions on shutdown via the `@PreDestroy` hook in `TradingEngineService`.

Alternatively, manually close via Binance web interface.

---

**Remember**: Start with testnet, test thoroughly, and only use production when confident!
