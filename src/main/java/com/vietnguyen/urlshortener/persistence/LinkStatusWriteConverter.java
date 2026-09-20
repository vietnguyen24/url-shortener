package com.vietnguyen.urlshortener.persistence;

import java.sql.JDBCType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

/** Converts link lifecycle states to database status values. */
@WritingConverter
final class LinkStatusWriteConverter implements Converter<LinkStatus, JdbcValue> {

  @Override
  public JdbcValue convert(LinkStatus source) {
    return JdbcValue.of(source.name(), JDBCType.VARCHAR);
  }
}
