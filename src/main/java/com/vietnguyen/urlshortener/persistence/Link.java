package com.vietnguyen.urlshortener.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Spring Data JDBC mapping for a shortened link. */
@Table("links")
public record Link(
    @Id Long id,
    @Column("short_code") String shortCode,
    String destination,
    LinkStatus status,
    @Column("created_at") Instant createdAt) {}
