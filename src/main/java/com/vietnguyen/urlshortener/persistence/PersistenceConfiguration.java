package com.vietnguyen.urlshortener.persistence;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

/** Registers PostgreSQL-specific persistence conversions. */
@Configuration(proxyBeanMethods = false)
class PersistenceConfiguration extends AbstractJdbcConfiguration {

  @Override
  protected List<?> userConverters() {
    return List.of(
        new ClientIpReadConverter(),
        new ClientIpWriteConverter(),
        new LinkStatusReadConverter(),
        new LinkStatusWriteConverter());
  }
}
