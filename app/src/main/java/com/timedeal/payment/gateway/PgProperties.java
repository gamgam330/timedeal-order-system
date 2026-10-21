package com.timedeal.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg")
public record PgProperties(long latencyMs, double failureRate) {
}
