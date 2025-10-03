package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.CurrentConditions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class CurrentConditionsService {

    public static final Map<Integer, String> LIVE_CONDITIONS_PL = Map.of(
            126330, "https://www.wiatrkadyny.pl/wiatrkadyny.txt",
            14473, "https://www.wiatrkadyny.pl/krynica/wiatrkadyny.txt",
            509469, "https://www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt",
            500760, "https://www.wiatrkadyny.pl/draga/wiatrkadyny.txt",
            4165, "https://www.wiatrkadyny.pl/rewa/wiatrkadyny.txt",
            48009, "https://www.wiatrkadyny.pl/puck/wiatrkadyny.txt"

    );
    private final OkHttpClient httpClient = new OkHttpClient();

    /*
     *    Data sources to use:
     *
     *       AT:
     *
     *       https://www.kiteriders.at/wind/weatherstat_kn.html
     *
     *       example (first row after header)
     *
     *       <tr style="background:#3dfa8e;"><td style="background:#3dfa8e;">29/09/2025</td><td style="background:#3dfa8e;">23:14</td><td style="background:#3dfa8e;">NNW</td><td style="background:#3dfa8e;"><b> 3 Bft</b></td><td style="background:#3dfa8e;">&nbsp;<b> 10.5 kn</b>&nbsp;</td><td style="background:#3dfa8e;"> 4 Bft</td><td style="background:#3dfa8e;">&nbsp; 12.2 kn&nbsp;</td><td style="background:#3dfa8e;">11.2 °C</td><td style="background:#3dfa8e;"> 5.1 °C</td><td style="background:#3dfa8e;">1023 mbar</td><td style="background:#3dfa8e;">+0.68</td></tr>
     *
     *       PL:
     *
     *       www.wiatrkadyny.pl/wiatrkadyny.txt - dla stacji Kadyny
     *       www.wiatrkadyny.pl/krynicwha/wiatrkadyny.txt - dla stacji Krynica Morska
     *       www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt - dla stacji Kuźnica
     *       www.wiatrkadyny.pl/draga/wiatrkadyny.txt - dla stacji Draga
     *       www.wiatrkadyny.pl/rewa/wiatrkadyny.txt - dla stacji Rewa
     *       www.wiatrkadyny.pl/puck/wiatrkadyny.txt - dla stacji Puck
     *
     *                    tmp               wd wdir                                                               gust
     *  29/09/25 23:08:31 10.1 74.9 5.9 4.7 7.0 65 0 0 1029.4 ENE 3 kts C hPa mm 346.88 0.4 0 0 0 10.1 74.9 7.1 0 12.9 23:36 9.7 2025-09-29 21:47:26 12.8 23:03 17.1 21:19 1029.5 22:54 1027.8 02:01 0 0 0 0 0 0 0 0 65 0 0 0 0 ENE 1718 m 10.1 12.56 0 0
     *
     */

    Mono<CurrentConditions> fetchWiatrKadynyForecast(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request
                    .Builder()
                    .url(url)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                String body = responseBody.string();
                String[] parts = body.trim().split("\\s+");

                // Parse based on the format:
                // 29/09/25 23:08:31 10.1 74.9 5.9 4.7 7.0 65 0 0 1029.4 ENE ...
                // [0] date, [1] time, [2] temp, [6] wind, [11] direction, [26] gust
                String date = parts[0] + " " + parts[1];
                int temp = (int) Math.round(Double.parseDouble(parts[2]));
                int wind = (int) Math.round(Double.parseDouble(parts[6]));
                String direction = parts[11];
                int gusts = (int) Math.round(Double.parseDouble(parts[26]));

                return new CurrentConditions(date, wind, gusts, direction, temp);
            }
        });
    }

    public Mono<CurrentConditions> fetchKadynyForecast(int wgId) {
        return fetchWiatrKadynyForecast(LIVE_CONDITIONS_PL.get(wgId));
    }

}