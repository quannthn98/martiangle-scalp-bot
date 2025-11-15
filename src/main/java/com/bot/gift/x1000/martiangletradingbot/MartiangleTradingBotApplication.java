package com.bot.gift.x1000.martiangletradingbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
public class MartiangleTradingBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MartiangleTradingBotApplication.class, args);
    }

}
