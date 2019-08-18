package com.its.reactivedemo;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;

import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;

import org.springframework.web.reactive.function.server.RouterFunction;

import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;


import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EnableTransactionManagement
@Log4j2
public class ReactiveDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReactiveDemoApplication.class, args);
	}

	@Bean
	ConnectionFactory connectionFactory(@Value("${spring.r2dbc.url}") String url) {
		log.info("Entering and leaving connectionFactory after instantiating r2dbc conn. factory");
	    return ConnectionFactories.get(url);
	}

	@Bean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager rtm) {
        log.info("Entering and leaving transactionalOperator after instantiating r2dbc txnal operator");
	    return TransactionalOperator.create(rtm);
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory cf) {
        log.info("Entering and leaving transactionManager after instantiating r2dbc txnManager");
	    return new R2dbcTransactionManager(cf);
    }
}

@Configuration
@Log4j2
class ReservationHttpConfig {
    @Bean
    RouterFunction<ServerResponse> routes(ReservationRepository rr) {
        log.info("Entering and leaving routes after returning all records from reservation table");
        return route()
                .GET("/reservations", serverRequest -> ok()
                        .body(rr.findAll(), Reservation.class))
                .build();
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

@Service
class GreetingService {
    Flux<GreetingResponse> greet(GreetingRequest greetingRequest) {
        return Flux
                .fromStream(Stream
                                .generate(() -> new GreetingResponse("Hello " + greetingRequest.getName() + " @ " + Instant.now()))
                )
                .delayElements(Duration.ofSeconds(1));
    }
}


class WebSocketConfiguration {
    @Bean
    SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler wsh) {
        return new SimpleUrlHandlerMapping() {

            @Override
            public void setOrder(int order) {
                super.setOrder(10);
            }

            @Override
            public void setUrlMap(Map<String, ?> urlMap) {
                Map.of("/ws/greetings", wsh);
            }
        };
    }

    @Bean
    WebSocketHandler webSocketHandler(GreetingService service) {
        return new WebSocketHandler() {
            @Override
            public Mono<Void> handle(WebSocketSession webSocketSession) {
                /*Flux<WebSocketMessage> websocketChat = webSocketSession.websocketChat();
                Flux <String> names = websocketChat.map(webSocketMessage -> webSocketMessage.getPayloadAsText());
                Flux<GreetingResponse> greetings = names.flatMap(name -> service.greet(new GreetingRequest(name)));
                Flux<String> map = greetings.map(gr -> gr.getMessage());
                Flux<WebSocketMessage> webSocketMessageFlux = map.map(txt -> webSocketSession.textMessage(txt));*/

                // instead of above intermediate returns u can do below for better readability

                Flux<WebSocketMessage> websocketChat = webSocketSession
                                                        .receive()
                                                        .map(webSocketMessage -> webSocketMessage.getPayloadAsText())
                                                        .flatMap(name -> service.greet(new GreetingRequest(name)))
                                                        .map(gr -> gr.getMessage())
                                                        .map(txt -> webSocketSession.textMessage(txt));

                return webSocketSession.send(websocketChat);
            }
        };
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

// below approach is still conventional way of exposing REST endpoints

/*@RestController
@RequiredArgsConstructor
@Log4j2
class ReservationRestController {
    private final ReservationRepository reservationRepository;

    @GetMapping("/reservations")
    Flux<Reservation> fetchAll() {
        log.info(" Entering and leaving fetchAll after fetching from db");
        return this.reservationRepository.findAll();
    }
}*/

@RequiredArgsConstructor
@Service
@Transactional
@Log4j2
class ReservationService {
    private final ReservationRepository reservationRepository;
    private final TransactionalOperator transactionalOperator;

    public Flux<Reservation> saveName(String ... names) {
        log.info("Entering saveName with arguments {} ", names);
        Flux<Reservation> reservationFlux =  Flux
                                                .just(names)
                                                .map(name -> new Reservation(null, name))
                                                .flatMap(reservationRepository::save)
                                                .doOnNext(r -> validateName(r.getName()));

        log.info("Leaving saveName ");
        return reservationFlux;

        /**
         * Below call indicates declarative txn mgmt.
         *
         * If you enable class with @EnableTransactionManagement and method with @Transactional,
         * you dont need to do below call explicitly
         * */
        //return this.transactionalOperator.transactional(reservationFlux);
    }

    private void validateName(String name) {
        log.info("Entering validateName ");
        Assert.isTrue(name != null && name.length() > 0, "Name must not be empty");
        log.info("Name is not empty ");
        var firstChar = name.charAt(0);
        Assert.isTrue(Character.isUpperCase(firstChar), "the name must start with an upper case letter");
        log.info("Name starts with upper case letters ");
    }
}

@Component
@RequiredArgsConstructor
@Log4j2
class SampleDataInitializer {
	private final ReservationRepository reservationRepository;
	private final ReservationService reservationService;

	@EventListener(ApplicationReadyEvent.class)
	public void go() {
        log.info("Entering SampleDataInitializer : go ");
		Flux<Reservation> names = Flux
									.just("Dhaval", "Bhavin", "Jigar", "Vishal", "Sharad", "Sid", "Samir")
									.map(s -> new Reservation(null, s))
									.flatMap(this.reservationRepository::save);

		reservationRepository
			.deleteAll()
			.thenMany(names)
			.thenMany(this.reservationRepository.findAll())

            .log()
            .doOnEach(signal -> {
                log.info("Signal type : {} ", signal.getType());
                log.info("Fetch id from signal's context : {} ", signal.getContext().getOrEmpty("id").get());
                log.info("Signal : {} ", signal.get());
            })
            /**
             * how to propagate information across the reactive stream ?
             *
             * this context is visible and propagated throughout the reactive pipeline.
             * it moves from one thread to another. If you want to propagate txn, security credential or MDC. Spring cloud sleuth uses this
             * */
            .subscriberContext(Context.of("id", UUID.randomUUID().toString()))
            // u can influence how things are scheduled with below api call
            //.subscribeOn(Schedulers.fromExecutor(Executors.newFixedThreadPool(10)))
            // belows api call keeps on creating new thread whenever you need it. it may lead to starvation issue
            // hence this is a smell
			.subscribeOn(Schedulers.elastic());
			//.subscribe(log::info);
            this.reservationRepository
                .deleteAll()
                .thenMany(this.reservationService.saveName("Viral", "Ankur", "Hitesh", "Parag", "Rikin"))
                .subscribe();

	}
}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {

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

@Controller
@RequiredArgsConstructor
@Log4j2
class GreetingsRSocketController {

    //private final GreetingProducer producer;
    private final GreetingService greetingService;

    @MessageMapping("greetings")
    Flux<GreetingResponse> greet(GreetingRequest request) {
        log.info("Entering greet");
        return this.greetingService.greet(request);
    }
}

/*@Component
class GreetingProducer {

    public Flux<GreetingResponse> greet(GreetingRequest request) {
        return Flux.fromStream(
                Stream.generate(() -> new GreetingResponse("Hello " + request.getName() + " @ " + Instant.now() + "!")))
                .delayElements(Duration.ofSeconds(1));
    }

}*/


