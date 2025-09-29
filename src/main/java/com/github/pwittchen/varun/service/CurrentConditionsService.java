package com.github.pwittchen.varun.service;

import org.springframework.stereotype.Service;

@Service
public class CurrentConditionsService {

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
     *       www.wiatrkadyny.pl/krynica/wiatrkadyny.txt - dla stacji Krynica Morska
     *       www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt - dla stacji Kuźnica
     *       www.wiatrkadyny.pl/draga/wiatrkadyny.txt - dla stacji Draga
     *       www.wiatrkadyny.pl/rewa/wiatrkadyny.txt - dla stacji Rewa
     *       www.wiatrkadyny.pl/puck/wiatrkadyny.txt - dla stacji Puck
     *
     *                    tmp               wd wdir                                                               gust
     *  29/09/25 23:08:31 10.1 74.9 5.9 4.7 7.0 65 0 0 1029.4 ENE 3 kts C hPa mm 346.88 0.4 0 0 0 10.1 74.9 7.1 0 12.9 23:36 9.7 2025-09-29 21:47:26 12.8 23:03 17.1 21:19 1029.5 22:54 1027.8 02:01 0 0 0 0 0 0 0 0 65 0 0 0 0 ENE 1718 m 10.1 12.56 0 0
     *
     */
}