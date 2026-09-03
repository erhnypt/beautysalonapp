package com.beautysalonapp.outbound;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Uygulamanın dışarıya HTTP çağrısı yapmak için kullanabileceği TEK istemci.
 * Her çağrı önce {@link OutboundHttpGuard} allowlist kontrolünden geçer.
 *
 * <p>Doğrudan {@link RestClient}, {@code RestTemplate}, {@code HttpClient} veya
 * {@code WebClient} kullanımı ArchUnit testiyle yasaklanır.
 */
@Component
public class GuardedRestClient {

    private final OutboundHttpGuard guard;
    private final RestClient restClient;

    public GuardedRestClient(OutboundHttpGuard guard) {
        this.guard = guard;
        this.restClient = RestClient.builder()
                .requestFactory(clientFactory())
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientFactory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return f;
    }

    public <T> T post(String url, Object body, Class<T> responseType) {
        guard.check(url);
        return restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    public <T> T get(String url, Class<T> responseType) {
        guard.check(url);
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(responseType);
    }
}
