package com.enterpriselab.api.dashboard.web;

import com.enterpriselab.api.dashboard.domain.ClosedInPeriod;

public record ClosedInPeriodResponse(long completed, long cancelled, long total) {

    static ClosedInPeriodResponse from(ClosedInPeriod closed) {
        return new ClosedInPeriodResponse(closed.completed(), closed.cancelled(), closed.total());
    }
}
