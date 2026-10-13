package com.streamlink.server;

interface StreamRecordStore {

    String findStoredPath(int videoId) throws Exception;

    int insert(int userId, int videoId) throws Exception;

    void finalizeRecord(int streamId, int userId) throws Exception;
}
