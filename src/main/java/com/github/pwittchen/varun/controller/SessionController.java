package com.github.pwittchen.varun.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Hands out the SESSION cookie that the rest of {@code /api/v1/**} requires.
 *
 * <p>A browser never needs this: it loads a page first, and
 * {@code SessionAuthenticationFilter} puts a cookie on that response. A native client
 * has no page to load, so without this endpoint its only way in is to request the
 * index HTML and read the Set-Cookie off it - a few tens of kilobytes fetched for a
 * header, and an undocumented coupling to the frontend that nothing would stop us
 * breaking.
 *
 * <p>The cookie itself is minted by the filter, which is where the decision of whether
 * to mark it Secure lives. This method only supplies the status code, and the filter
 * treats the path as a page visit: a caller already holding a fresh token gets 204 with
 * no Set-Cookie and should keep using the one it has.
 *
 * <p>This is not authentication and gates nothing - see the note in
 * {@code SessionAuthenticationFilter}. Anyone can call it, which is the point: it is a
 * speed bump that exists so casual scraping costs two requests instead of one.
 */
@RestController
@RequestMapping("/api/v1/")
public class SessionController {

    @GetMapping("session")
    public Mono<ResponseEntity<Void>> session() {
        return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }
}
