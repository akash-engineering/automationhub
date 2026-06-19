package com.automationhub.document.storage;

public interface StorageService {

    StorageLocation put(String key, byte[] bytes, String contentType);

    byte[] get(String key);
}
