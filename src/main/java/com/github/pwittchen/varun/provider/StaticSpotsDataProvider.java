package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.LiveConditions;
import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.model.SpotInfo;
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
                new LinkedList<>(),
                "",
                new SpotInfo(
                        "Bay, flat water",
                        "W, SW",
                        "10-18°C",
                        "Beginner to Advanced",
                        "Sandy Beach",
                        "Shallow areas, swimmers in summer, fishing nets",
                        "May-October (peak: Jul-Aug). Strong winds common in autumn/winter but cold water requires thick wetsuit. Summer offers warmest water but can be crowded.", "Jastarnia is one of Poland's most popular kitesurfing spots, located on the Hel Peninsula. The spot offers excellent conditions with westerly winds and a wide sandy beach perfect for launching. The shallow lagoon side is ideal for beginners, while the open Baltic Sea side provides more challenging conditions for advanced riders."
                ))
        );

        //todo: add more spots
    }

    @Override
    public List<Spot> getSpots() {
        return spots;
    }
}
