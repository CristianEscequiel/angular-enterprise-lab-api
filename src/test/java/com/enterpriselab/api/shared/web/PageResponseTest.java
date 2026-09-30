package com.enterpriselab.api.shared.web;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.enterpriselab.api.shared.domain.PageResult;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void mapsTheItemsAndCopiesTheTotals() {
        PageResponse<String> response = PageResponse.from(new PageResult<>(List.of(1, 2), 2, 2, 5), n -> "#" + n);

        assertThat(response).isEqualTo(new PageResponse<>(List.of("#1", "#2"), 2, 2, 5, 3));
    }

    @Test
    void anEmptyResultHasZeroPages() {
        PageResponse<String> response = PageResponse.from(new PageResult<Integer>(List.of(), 1, 10, 0), String::valueOf);

        assertThat(response).isEqualTo(new PageResponse<>(List.of(), 1, 10, 0, 0));
    }
}
