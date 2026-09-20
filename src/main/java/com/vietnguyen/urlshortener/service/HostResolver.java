package com.vietnguyen.urlshortener.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
interface HostResolver {

  InetAddress[] resolve(String host) throws UnknownHostException;
}
