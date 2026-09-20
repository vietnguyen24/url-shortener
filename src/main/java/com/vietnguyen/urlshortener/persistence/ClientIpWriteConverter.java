package com.vietnguyen.urlshortener.persistence;

import java.sql.JDBCType;
import java.sql.SQLException;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/** Converts client IP strings to PostgreSQL INET values. */
@WritingConverter
final class ClientIpWriteConverter implements Converter<ClientIp, JdbcValue> {

  @Override
  public JdbcValue convert(ClientIp source) {
    PGobject value = new PGobject();
    try {
      value.setType("inet");
      value.setValue(source.value());
    } catch (SQLException exception) {
      throw new IllegalArgumentException("Unable to map client IP", exception);
    }
    return JdbcValue.of(value, JDBCType.OTHER);
  }
}
