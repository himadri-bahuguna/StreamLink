package com.streamlink.database;


@FunctionalInterface
public interface StoredPathResolver {
    String resolve(String storedPath) throws Exception;
}
