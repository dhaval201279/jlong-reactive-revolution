package com.its.reactivedemo;

import com.github.javafaker.service.FakeValuesService;
import com.github.javafaker.service.RandomService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.annotation.Id;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Log4j2
public class SpringReactorDemoApplication {

    public static void main (String [] args) {
        log.info("Entering main");
        List<CardMetaData> cardMetaDataList = fetchAllCardMetaData();
        //cardMetaDataList.sub

        Flux
            .fromIterable(cardMetaDataList)
            .parallel(10)
            .runOn(Schedulers.parallel())
            .subscribe(new Consumer<CardMetaData>() {
                @Override
                public void accept(CardMetaData cardMetaData) {
                    log.info("Thread name {} - processing TUR {} ", Thread.currentThread().getName(), cardMetaData.getTur());
                }
            });

        /*Flux
            .fromIterable(cardMetaDataList)
            .*/


    }

    private static List<CardMetaData> fetchAllCardMetaData() {
        List<CardMetaData> cardMetaDatas = new ArrayList<CardMetaData>();
        for (int i =0; i < 100; i++) {
            cardMetaDatas.add(CardMetaData.builder().build());
        }
        log.info("Leaving fetchAllCardMetaData with list size as {} ", cardMetaDatas.size());
        return cardMetaDatas;
    }
}



@AllArgsConstructor

@Data
@Builder
class CardMetaData {


    static FakeValuesService fakeValuesService  = new FakeValuesService(new Locale("en-US"), new RandomService());

    /*public CardMetaData() {
        fakeValuesService =
    }*/

    @Id
    @Builder.Default
    private String cardId = fakeValuesService.regexify("[a-z1-9]{32}");
    @Builder.Default
    private String tur = fakeValuesService.regexify("[A-Z1-9]{32}");
}

@AllArgsConstructor
@NoArgsConstructor
class CardToBeDeleted {
    private String cardId;
    private String tur;
}
