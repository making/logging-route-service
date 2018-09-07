package am.ik.routeservice;

import java.net.URI;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.server.*;

import static java.util.Collections.singletonList;
import static org.springframework.web.reactive.function.server.ServerResponse.status;

public class LoggingHandler {
	private static final Logger log = LoggerFactory.getLogger(LoggingHandler.class);
	private static final String FORWARDED_URL = "X-CF-Forwarded-Url";
	private static final String PROXY_METADATA = "X-CF-Proxy-Metadata";
	private static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";
	private final WebClient webClient = WebClient.create();

	public RouterFunction<ServerResponse> route() {
		return RouterFunctions.route(incoming(), this::forward);
	}

	private RequestPredicate incoming() {
		return req -> {
			final HttpHeaders h = req.headers().asHttpHeaders();
			return h.containsKey(FORWARDED_URL) && h.containsKey(PROXY_METADATA)
					&& h.containsKey(PROXY_SIGNATURE);
		};
	}

	Mono<ServerResponse> forward(ServerRequest req) {
		final HttpHeaders headers = headers(req.headers().asHttpHeaders());
		final URI uri = headers.remove(FORWARDED_URL).stream().findFirst()
				.map(URI::create).orElseThrow(() -> new IllegalStateException(
						String.format("No %s header present", FORWARDED_URL)));
		final RequestBodySpec spec = webClient.method(req.method()) //
				.uri(uri) //
				.headers(h -> h.putAll(headers));
		log.info("Outgoing Request: <{} {},{}>", req.method(), uri, headers);
		return req.bodyToMono(String.class).doOnNext(b -> log.info("Request Body: {}", b))
				.<RequestHeadersSpec<?>>map(spec::syncBody).switchIfEmpty(Mono.just(spec)) //
				.flatMap(s -> s.exchange().flatMap(r -> {
					log.info("Response : <{}, {}>", r.statusCode(),
							r.headers().asHttpHeaders());
					return status(r.statusCode())
							.headers(h -> h.putAll(r.headers().asHttpHeaders())).body(
									r.bodyToMono(String.class).doOnNext(
											b -> log.info("Response Body: {}", b)),
									String.class);
				}));
	}

	private HttpHeaders headers(HttpHeaders incomingHeaders) {
		final HttpHeaders headers = new HttpHeaders();
		headers.putAll(incomingHeaders);
		final String host = URI
				.create(Objects.requireNonNull(incomingHeaders.getFirst(FORWARDED_URL)))
				.getHost();
		headers.put(HttpHeaders.HOST, singletonList(host));
		return headers;
	}
}
