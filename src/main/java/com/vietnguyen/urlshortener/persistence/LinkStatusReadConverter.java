package com.vietnguyen.urlshortener.persistence;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/** Converts database status values to link lifecycle states. */
@ReadingConverter
final class LinkStatusReadConverter implements Converter<String, LinkStatus> {

  @Override
  public LinkStatus convert(String source) {
    return LinkStatus.valueOf(source);
  }
}
