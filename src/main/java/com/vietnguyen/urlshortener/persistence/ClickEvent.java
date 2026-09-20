package com.vietnguyen.urlshortener.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Spring Data JDBC mapping for a redirect click event. */
@Table("click_events")
public record ClickEvent(
    @Id Long id,
    @Column("link_id") Long linkId,
    @Column("occurred_at") Instant occurredAt,
    String referrer,
    @Column("user_agent") String userAgent,
    @Column("client_ip") ClientIp clientIp) {}
