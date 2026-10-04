package vn.edu.ptit.web_grading_system.api_gateway.service;

import org.springframework.http.HttpMethod;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the encoded body of a {@link ClientRequest} captured by a stubbed
 * {@code ExchangeFunction}, so tests can assert the exact form fields and JSON payload
 * that would go over the wire — without a network.
 */
final class ClientRequestBodies {

    private static final BodyInserter.Context CONTEXT = new BodyInserter.Context() {
        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return ServerCodecConfigurer.create().getWriters();
        }

        @Override
        public Optional<ServerHttpRequest> serverRequest() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> hints() {
            return Map.of();
        }
    };

    private ClientRequestBodies() {
    }

    /** @return the encoded request body, empty string for body-less requests */
    static String of(ClientRequest request) {
        if (request.method().equals(HttpMethod.GET)) {
            return "";
        }
        MockClientHttpRequest message = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(message, CONTEXT).block();
        String body = message.getBodyAsString().block();
        return body == null ? "" : body;
    }
}
