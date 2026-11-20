package dev.uxlens.usage;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiUsageRepository extends JpaRepository<ApiUsage, Long> {

    /** Total spend since a point in time; used by the daily budget guard. */
    @Query("select coalesce(sum(u.costUsd), 0) from ApiUsage u where u.createdAt >= :since")
    BigDecimal totalCostSince(Instant since);
}
