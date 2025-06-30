package org.api;

import feign.RequestLine;

public interface CatFactNinja {
    // ref: https://catfact.ninja/#/Facts/getRandomFact
    @RequestLine("GET /fact")
    String getRandomFact();
}
