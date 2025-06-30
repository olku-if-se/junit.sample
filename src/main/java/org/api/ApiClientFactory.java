package org.api;

import feign.Feign;

public class ApiClientFactory {
    public static final String HOST = "https://catfact.ninja";

    public static CatFactNinja createCatFactNinjaClient() {
        return createCatFactNinjaClient(HOST);
    }

    public static CatFactNinja createCatFactNinjaClient(String host) {
        return Feign.builder()
                .target(CatFactNinja.class, host);
    }
}
