package com.its.reactivedemoclient;


import io.rsocket.transport.netty.client.TcpClientTransport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreaker;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@Log4j2
public class ReactiveDemoClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReactiveDemoClientApplication.class, args);
	}

	private final ReactiveCircuitBreaker circuitBreaker;

	/*@Bean
	RSocketRequester requester(RSocketRequester.Builder builder) {
		log.info("Entering and leaving requester after initializing RSocketRequester.Builder");
		return builder
				.connect(TcpClientTransport.create(7070))
				.block();
	}*/

	public ReactiveDemoClientApplication(ReactiveCircuitBreakerFactory circuitBreakerFactory) {
		log.info("Entering constructor ReactiveDemoClientApplication ");
		this.circuitBreaker = circuitBreakerFactory.create("names");
		log.info("Leaving constructor ReactiveDemoClientApplication ");
	}

	@Bean
	WebClient client(WebClient.Builder builder) {
		log.info("Entering and leaving client after initializing webclient builder");
		return builder.build();
	}

	@Bean
	RouteLocator routeLocator(RouteLocatorBuilder rlb) {
		log.info("Entering and leaving routeLocator after setting routes");
		return rlb
				.routes()
				.route(rSpec ->
					rSpec
						.host("*.foo.bar")
						.and()
						.path("/proxy")
						.filters( fSpec ->
							fSpec
								.addResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
								.setPath("/reservations")
								//.retry(10)
								//.redirect()
								//.requestRateLimiter()

						)
						.uri("http://localhost:8080")
				)
				.build();
	}

	@Bean
	RouterFunction<ServerResponse> routes(ReservationClient rc, GreetingsClient gc) {
		log.info("Entering and leaving routes after implementing corresponding handlers");
		return route()
				.GET("/greetings/{name}", request -> {
					log.info("Entering handle method of routes for fetching greeting message");
					Flux<GreetingResponse> greetingResponseFlux = gc
																	.greet(new GreetingRequest(request.pathVariable("name")));
					log.info("Returning flux of GreetingResponse");
					return ServerResponse.ok()
							.contentType(MediaType.TEXT_EVENT_STREAM)
							.body(greetingResponseFlux, GreetingResponse.class);

				})
				.GET("/reservations/names", serverRequest -> {
					log.info("Entering handle method of routes for fetching all the reservations");

					/**
					Flux<String> names =  rc
											.getAllReservations()
											.map(r -> r.getName())
											// --- retry how many times
											//.retry(10)
											// --- retry with back off i.e. take 1 sec for 1st time, 2 sec for 2nd time . . .
											// --- 10 sec for 10th time
											//.retryBackoff(10, Duration.ofSeconds(1))
											// --- something goes wrong, than i want to send default value i.e. handling failure mode
											.onErrorResume(throwable -> {
												log.info("Entering and returning from apply method of onErrorResume i.e. failure mode");
												return Flux.just("EEK");
											});
					 * */

					/**
					 * Above impl is operator based defaulting approach
					 *
					 * Below impl is same as above but by using rx circuit breaker appraoch
					 * */

					Flux<String> names = circuitBreaker
											.run(rc
													.getAllReservations()
													.map(r -> r.getName()),
												throwable -> {
													log.error("Error occurred whilst invoking downstream service", throwable);
													return Flux.just("EEK!");
												}
											);

					log.info("Returning flux of names");

					//Flux.first()

					return ok().body(names, String.class);
				})
				.build();
	}

}

@Component
@RequiredArgsConstructor
@Log4j2
class ReservationClient {

	private final WebClient webClient;

	Flux<Reservation> getAllReservations() {
		log.info("Entering and returning getAllReservations after invoking downstream service");
		return webClient
				.get()
				.uri("http://localhost:8080/reservations")
				.retrieve()
				.bodyToFlux(Reservation.class);
	}
}

@AllArgsConstructor
@NoArgsConstructor
@Data
//@Document
class Reservation {
	@Id
	private Integer id;
	private String name;
}

@Component
@RequiredArgsConstructor
@Log4j2
class GreetingsClient {

	private final RSocketRequester requester;

	public Flux<GreetingResponse> greet(GreetingRequest request) {
		log.info("Entering and leaving greet after routing to greetings with rsocketrequester");
		return this.requester
				.route("greetings")
				.data(request)
				.retrieveFlux(GreetingResponse.class);
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingRequest {
	private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingResponse {
	private String message;
}

@Configuration
class RSocketClientConfig {

	@Bean
	RSocketRequester requester(RSocketRequester.Builder builder) {
		return builder.connect(TcpClientTransport.create(7070)).block();
	}
	/*
	@Bean
	RSocket rSocket()	{
		return RSocketFactory
			.connect()
			.dataMimeType(MimeTypeUtils.APPLICATION_JSON_VALUE)
			.frameDecoder(PayloadDecoder.ZERO_COPY)
			.transport(TcpClientTransport.create(7070))
			.start()
			.block();
	}*/
}
