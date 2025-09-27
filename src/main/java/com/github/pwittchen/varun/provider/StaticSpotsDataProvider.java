package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.LiveConditions;
import com.github.pwittchen.varun.model.Spot;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class StaticSpotsDataProvider implements SpotsDataProvider {
    private final List<Spot> spots = new LinkedList<>();

    public StaticSpotsDataProvider() {
        spots.add(new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl/um/metco/mgram_pict.php?ntype=0u&row=339&col=213&lang=pl",
                "https://www.wiatrkadyny.pl/draga/index.php",
                "https://maps.app.goo.gl/yJJPfBtdGqUfFkAr6",
                new LiveConditions(),
                new LinkedList<>()
        ));

        //todo: add more spots
    }

    @Override
    public List<Spot> getSpots() {
        return spots;
    }
}
