package com.enterpriselab.api.shared.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigIT extends AbstractPostgresIT {

    @Autowired
    private Clock clock;

    @Test
    void theApplicationHasAnUtcClock() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
