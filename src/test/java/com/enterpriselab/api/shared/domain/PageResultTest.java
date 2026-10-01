package com.enterpriselab.api.shared.domain;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @ParameterizedTest
    @CsvSource({
            "0, 10, 0", "1, 10, 1", "10, 10, 1", "11, 10, 2", "32, 10, 4",
            "0, 1, 0", "1, 1, 1", "32, 1, 32", "32, 100, 1", "100, 100, 1", "101, 100, 2"})
    void totalPagesRoundsUp(long totalItems, int size, int expectedPages) {
        assertThat(new PageResult<>(List.of(), 1, size, totalItems).totalPages()).isEqualTo(expectedPages);
    }

    @Test
    void mapKeepsThePageAndTheTotals() {
        PageResult<String> mapped = new PageResult<>(List.of(1, 2), 3, 2, 11).map(n -> "#" + n);

        assertThat(mapped).isEqualTo(new PageResult<>(List.of("#1", "#2"), 3, 2, 11));
        assertThat(mapped.totalPages()).isEqualTo(6);
    }

    @Test
    void defaultsAreThePageOneAndTenItems() {
        assertThat(PageQuery.DEFAULT_PAGE).isEqualTo(1);
        assertThat(PageQuery.DEFAULT_SIZE).isEqualTo(10);
        assertThat(PageQuery.MAX_SIZE).isEqualTo(100);
    }
}
