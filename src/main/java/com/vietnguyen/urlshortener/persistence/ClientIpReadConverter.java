package com.vietnguyen.urlshortener.persistence;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/** Converts PostgreSQL INET values to their string representation. */
@ReadingConverter
final class ClientIpReadConverter implements Converter<PGobject, ClientIp> {

  @Override
  public ClientIp convert(PGobject source) {
    return new ClientIp(source.getValue());
  }
}
