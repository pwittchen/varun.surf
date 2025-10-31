    let currentSpot = null;
    let currentLanguage = 'en';
    let currentErrorKey = null;
    let currentErrorText = '';
    let currentLoadingKey = 'loadingSpotData';
    let forecastPollIntervalId = null;
    let forecastTimeoutId = null;
    let backgroundRefreshIntervalId = null;
    let currentSpotId = null;
    let selectedModel = 'gfs';
    let currentSponsors = [];

    const FORECAST_POLL_INTERVAL = 5000;
    const FORECAST_TIMEOUT_MS = 30000;
    const BACKGROUND_REFRESH_INTERVAL = 60000;

    // Configuration
    const API_ENDPOINT = '/api/v1/spots';

    // Extract spot ID from URL
    function getSpotIdFromUrl() {
        const pathParts = window.location.pathname.split('/');
        const spotIndex = pathParts.indexOf('spot');
        if (spotIndex !== -1 && pathParts.length > spotIndex + 1) {
            return pathParts[spotIndex + 1];
        }
        return null;
    }

    // Get selected forecast model from sessionStorage
    function getSelectedModel() {
        const model = sessionStorage.getItem('forecastModel');
        if (model && (model === 'gfs' || model === 'ifs')) {
            return model;
        }
        return 'gfs';
    }

    // Set selected forecast model in sessionStorage
    function setSelectedModel(model) {
        selectedModel = model;
        sessionStorage.setItem('forecastModel', model);
    }

    // Fetch single spot data
    async function fetchSpotData(spotId) {
        try {
            const model = getSelectedModel();
            const url = `${API_ENDPOINT}/${spotId}${model ? '/' + model : ''}`;
            const response = await fetch(url);
            if (!response.ok) {
                const error = new Error(`HTTP error! status: ${response.status}`);
                error.status = response.status;
                throw error;
            }
            const data = await response.json();
            return data;
        } catch (error) {
            console.error('Error fetching spot data:', error);
            throw error;
        }
    }

    function hasForecastData(spot) {
        return spot && Array.isArray(spot.forecast) && spot.forecast.length > 0;
    }

    function clearForecastPolling() {
        if (forecastPollIntervalId) {
            clearInterval(forecastPollIntervalId);
            forecastPollIntervalId = null;
        }
        if (forecastTimeoutId) {
            clearTimeout(forecastTimeoutId);
            forecastTimeoutId = null;
        }
    }

    function clearBackgroundRefresh() {
        if (backgroundRefreshIntervalId) {
            clearInterval(backgroundRefreshIntervalId);
            backgroundRefreshIntervalId = null;
        }
    }

    function setLoadingMessage(key) {
        const loadingMessage = document.getElementById('loadingMessage');
        const loadingText = document.getElementById('loadingText');
        const errorMessage = document.getElementById('errorMessage');

        currentLoadingKey = key;

        if (errorMessage) {
            errorMessage.style.display = 'none';
        }

        if (loadingMessage) {
            loadingMessage.style.display = 'block';
        }

        if (loadingText) {
            loadingText.textContent = t(key);
        }
    }

    function startForecastPolling(spotId) {
        clearForecastPolling();

        let pollingInProgress = false;

        forecastPollIntervalId = setInterval(async () => {
            if (pollingInProgress) {
                return;
            }
            pollingInProgress = true;
            try {
                const latestSpot = await fetchSpotData(spotId);
                if (hasForecastData(latestSpot)) {
                    clearForecastPolling();
                    displaySpot(latestSpot);
                }
            } catch (error) {
                if (error && error.status === 404) {
                    clearForecastPolling();
                    displayError('spotNotFound');
                }
            } finally {
                pollingInProgress = false;
            }
        }, FORECAST_POLL_INTERVAL);

        forecastTimeoutId = setTimeout(() => {
            clearForecastPolling();
            displayError('forecastTimeout');
        }, FORECAST_TIMEOUT_MS);
    }

    function startBackgroundRefresh(spotId) {
        if (backgroundRefreshIntervalId || !spotId) {
            return;
        }

        backgroundRefreshIntervalId = setInterval(async () => {
            try {
                const latestSpot = await fetchSpotData(spotId);
                if (!hasForecastData(latestSpot)) {
                    return;
                }

                if (!currentSpot || JSON.stringify(latestSpot) !== JSON.stringify(currentSpot)) {
                    displaySpot(latestSpot);
                }
            } catch (error) {
                if (error && error.status === 404) {
                    clearBackgroundRefresh();
                    displayError('spotNotFound');
                } else {
                    console.warn('Background refresh failed:', error);
                }
            }
        }, BACKGROUND_REFRESH_INTERVAL);
    }

    // Helper function to get wind arrow
    function getWindArrow(direction) {
        const arrows = {
            'N': '↓',
            'NE': '↙',
            'E': '←',
            'SE': '↖',
            'S': '↑',
            'SW': '↗',
            'W': '→',
            'NW': '↘'
        };
        return arrows[direction] || '•';
    }

    // Helper function to get wind rotation angle for arrow
    function getWindRotation(direction) {
        const rotations = {
            'N': 0,
            'NE': 45,
            'E': 90,
            'SE': 135,
            'S': 180,
            'SW': 225,
            'W': 270,
            'NW': 315
        };
        return rotations[direction] || 0;
    }

    // Helper function to get wind quality class based on wind value
    function getWindClass(windValue) {
        if (windValue < 12) {
            return 'wind-weak';
        } else if (windValue >= 12 && windValue < 18) {
            return 'wind-moderate';
        } else if (windValue >= 18 && windValue <= 25) {
            return 'wind-strong';
        } else {
            return 'wind-extreme';
        }
    }

    // Helper function to get spot info based on current language
    function getSpotInfo(spot) {
        if (!spot) return null;
        const lang = localStorage.getItem('language') || 'en';
        // Direct access - no fallback, all translations are complete
        return lang === 'pl' ? spot.spotInfoPL : spot.spotInfo;
    }

    // Helper function to translate day names
    function translateDayName(dayName) {
        if (!dayName || typeof dayName !== 'string') {
            return '';
        }

        const token = dayName.substring(0, 3);
        const keyMap = {
            Mon: 'dayMon',
            Tue: 'dayTue',
            Wed: 'dayWed',
            Thu: 'dayThu',
            Fri: 'dayFri',
            Sat: 'daySat',
            Sun: 'daySun'
        };

        const translationKey = keyMap[token];
        return translationKey ? t(translationKey) : dayName;
    }

    function formatForecastDateLabel(rawDate) {
        if (!rawDate || typeof rawDate !== 'string') {
            return rawDate || '';
        }

        const tokens = rawDate.split(' ').filter(Boolean);
        if (tokens.length < 5) {
            return rawDate;
        }

        const [dayToken, dayOfMonthToken, monthToken, , timeToken] = tokens;
        const translatedDay = translateDayName(dayToken);
        const formattedDayOfMonth = dayOfMonthToken.padStart(2, '0');

        // Check if mobile view (screen width <= 768px)
        const isMobile = window.innerWidth <= 768;

        if (isMobile) {
            // Mobile: Show only first two letters of day and hour (without minutes)
            const hour = timeToken.split(':')[0];
            const shortDay = translatedDay.substring(0, 2);
            return `${shortDay} ${hour}`.trim();
        } else {
            // Desktop: Show full date with day of month, day of week, and time
            return `${formattedDayOfMonth}. ${translatedDay} ${timeToken}`.trim();
        }
    }

    // Helper function to get a country flag
    function getCountryFlag(country) {
        const flags = {
            'Poland': '🇵🇱',
            'Czechia': '🇨🇿',
            'Austria': '🇦🇹',
            'Belgium': '🇧🇪',
            'Switzerland': '🇨🇭',
            'Latvia': '🇱🇻',
            'Lithuania': '🇱🇹',
            'Estonia': '🇪🇪',
            'Denmark': '🇩🇰',
            'Sweden': '🇸🇪',
            'Norway': '🇳🇴',
            'Iceland': '🇮🇸',
            'Spain': '🇪🇸',
            'Portugal': '🇵🇹',
            'Italy': '🇮🇹',
            'Greece': '🇬🇷',
            'France': '🇫🇷',
            'Germany': '🇩🇪',
            'Netherlands': '🇳🇱',
            'Croatia': '🇭🇷',
            'Ireland': '🇮🇪',
            'UK': '🇬🇧',
            'Turkey': '🇹🇷',
            'Morocco': '🇲🇦',
            'Egypt': '🇪🇬',
            'Cape Verde': '🇨🇻',
            'Mauritius': '🇲🇺',
            'Brazil': '🇧🇷',
            'Peru': '🇵🇪',
            'Chile': '🇨🇱',
            'USA': '🇺🇸'
        };
        return flags[country] || '🏴';
    }

    // Modal functions
    function openInfoModal(spotName) {
        if (!currentSpot || !currentSpot.spotInfo) return;

        const modal = document.getElementById('infoModal');
        const modalSpotName = document.getElementById('infoModalSpotName');
        const spotInfoContent = document.getElementById('spotInfoContent');

        modalSpotName.textContent = `${spotName}`;

        const info = getSpotInfo(currentSpot);
        spotInfoContent.innerHTML = `
                <div class="info-grid">
                    <div class="info-item" style="grid-column: 1 / -1;">
                        <div class="info-label">${t('overviewLabel')}</div>
                        <div class="info-value">${info.description}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('spotTypeLabel')}</div>
                        <div class="info-value">${info.type}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('bestWindLabel')}</div>
                        <div class="info-value">${info.bestWind}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('waterTempLabel')}</div>
                        <div class="info-value">${info.waterTemp}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('experienceLabel')}</div>
                        <div class="info-value">${info.experience}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('launchTypeLabel')}</div>
                        <div class="info-value">${info.launch}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">${t('hazardsLabel')}</div>
                        <div class="info-value">${info.hazards}</div>
                    </div>
                    <div class="info-item" style="grid-column: 1 / -1;">
                        <div class="info-label">${t('seasonLabel')}</div>
                        <div class="info-value">${info.season}</div>
                    </div>
                </div>
            `;

        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeInfoModal() {
        const modal = document.getElementById('infoModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    function openAIModal(spotName) {
        const currentLang = localStorage.getItem('language') || 'en';
        const aiAnalysis = currentLang === 'pl' ? currentSpot.aiAnalysisPl : currentSpot.aiAnalysisEn;

        if (!currentSpot || !aiAnalysis) return;

        const modal = document.getElementById('aiModal');
        const modalTitle = document.getElementById('aiModalTitle');
        const aiAnalysisContent = document.getElementById('aiAnalysisContent');
        const aiModalDisclaimer = document.getElementById('aiModalDisclaimer');

        modalTitle.textContent = `${spotName} - ${t('aiAnalysisTitle')}`;
        aiAnalysisContent.innerHTML = `<p>${aiAnalysis}</p>`;
        aiModalDisclaimer.textContent = t('aiDisclaimer');

        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeAIModal() {
        const modal = document.getElementById('aiModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    function openIcmModal(spotName, icmUrl) {
        const modal = document.getElementById('icmModal');
        const modalTitle = document.getElementById('icmModalTitle');
        const icmImage = document.getElementById('icmImage');

        modalTitle.textContent = `${spotName} - ${t('icmForecastTitle')}`;
        icmImage.src = icmUrl;

        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeIcmModal() {
        const modal = document.getElementById('icmModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    function openAppInfoModal() {
        const modal = document.getElementById('appInfoModal');
        if (!modal) return;

        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeAppInfoModal() {
        const modal = document.getElementById('appInfoModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    // Helper function to parse forecast date string (e.g., "Mon 28 Oct 2025 14:00")
    function parseForecastDate(dateStr) {
        if (!dateStr) return new Date();

        try {
            // Parse format: "Mon 28 Oct 2025 14:00"
            const parts = dateStr.trim().split(/\s+/);
            if (parts.length >= 5) {
                const dayOfMonth = parseInt(parts[1]);
                const monthName = parts[2];
                const year = parseInt(parts[3]);
                const timeParts = parts[4].split(':');
                const hours = parseInt(timeParts[0]);
                const minutes = parseInt(timeParts[1]);

                // Map month names to month numbers (0-11)
                const monthMap = {
                    'Jan': 0, 'Feb': 1, 'Mar': 2, 'Apr': 3,
                    'May': 4, 'Jun': 5, 'Jul': 6, 'Aug': 7,
                    'Sep': 8, 'Oct': 9, 'Nov': 10, 'Dec': 11
                };

                const monthNumber = monthMap[monthName];
                if (monthNumber !== undefined) {
                    const parsed = new Date(year, monthNumber, dayOfMonth, hours, minutes);
                    if (!isNaN(parsed.getTime())) {
                        return parsed;
                    }
                }
            }
        } catch (e) {
            console.warn('Error parsing forecast date:', dateStr, e);
        }

        return new Date();
    }

    // Helper function to find forecast closest to current time
    function findClosestForecast(forecastData) {
        if (!forecastData || forecastData.length === 0) {
            return null;
        }

        const now = new Date();
        let closestForecast = forecastData[0];
        let minDiff = Math.abs(parseForecastDate(forecastData[0].date) - now);

        for (let i = 1; i < forecastData.length; i++) {
            const forecastTime = parseForecastDate(forecastData[i].date);
            const diff = Math.abs(forecastTime - now);
            if (diff < minDiff) {
                minDiff = diff;
                closestForecast = forecastData[i];
            }
        }

        return closestForecast;
    }

    // Create spot card HTML
    function createSpotCard(spot) {
        const countryFlag = getCountryFlag(spot.country);

        // Use forecastHourly if available, otherwise fall back to forecast
        const forecastData = (spot.forecastHourly && spot.forecastHourly.length > 0)
            ? spot.forecastHourly
            : (spot.forecast || []);

        // Check if any forecast has wave data
        const hasWaveData = forecastData && forecastData.some(day => day.wave !== undefined);

        // Determine layout
        const isDesktopView = window.matchMedia('(min-width: 769px)').matches;

        // Current conditions row (to be added at the top of the table)
        let currentConditionsRow = '';
        if (spot.currentConditions && spot.currentConditions.wind !== undefined) {
            const windClass = getWindClass(spot.currentConditions.wind);
            const windTextClass = windClass;
            const gustTextClass = getWindClass(spot.currentConditions.gusts);

            const tempClass = spot.currentConditions.temp >= 20 ? 'temp-positive' : 'temp-negative';
            const windArrow = getWindArrow(spot.currentConditions.direction);

            // Current wave conditions
            let currentWaveClass = '';
            let currentWaveText = '-';
            if (spot.currentConditions.wave !== undefined) {
                if (spot.currentConditions.wave === 0) {
                    currentWaveClass = 'wave-none';
                    currentWaveText = '0m';
                } else {
                    currentWaveClass = spot.currentConditions.wave > 1.5 ? 'wave-high' : 'wave-low';
                    currentWaveText = `${spot.currentConditions.wave}m`;
                }
            }

            // Use wind class for row background
            const rowWindClass = windClass === 'wind-weak' ? 'weak-wind' :
                                windClass === 'wind-moderate' ? 'moderate-wind' :
                                windClass === 'wind-strong' ? 'strong-wind' : 'extreme-wind';

            currentConditionsRow = `
                    <tr class="${rowWindClass}" style="border-bottom: 2px solid #404040;">
                        <td>
                            <div class="live-indicator">
                                <strong class="live-text">${t('nowLabel')}</strong>
                                <div class="live-dot"></div>
                            </div>
                        </td>
                        <td class="${windTextClass}">${spot.currentConditions.wind} kts</td>
                        <td class="${gustTextClass}">${spot.currentConditions.gusts} kts</td>
                        <td class="${windTextClass}">
                            <span class="wind-arrow">${windArrow}</span> ${spot.currentConditions.direction}
                        </td>
                        <td class="${tempClass}">${spot.currentConditions.temp}°C</td>
                        <td>-</td>
                        ${hasWaveData ? `<td class="${currentWaveClass}">${currentWaveText}</td>` : ''}
                    </tr>
                `;
        }

        let forecastRows = '';
        if (forecastData && forecastData.length > 0) {
            let previousDay = null;
            let dayColorIndex = 0;

            forecastData.forEach(day => {
                let windClass = '';
                let windTextClass = '';

                if (day.wind < 12) {
                    windClass = 'weak-wind';
                    windTextClass = 'wind-weak';
                } else if (day.wind >= 12 && day.wind <= 18) {
                    windClass = 'moderate-wind';
                    windTextClass = 'wind-moderate';
                } else if (day.wind >= 19 && day.wind <= 25) {
                    windClass = 'strong-wind';
                    windTextClass = 'wind-strong';
                } else {
                    windClass = 'extreme-wind';
                    windTextClass = 'wind-extreme';
                }

                const tempClass = day.temp >= 18 ? 'temp-positive' : 'temp-negative';
                const windArrow = getWindArrow(day.direction);
                const precipClass = day.precipitation === 0 ? 'precipitation-none' : 'precipitation';

                // Wave classes
                let waveClass = '';
                let waveText = '-';
                if (day.wave !== undefined) {
                    if (day.wave === 0) {
                        waveClass = 'wave-none';
                        waveText = '0m';
                    } else {
                        waveClass = day.wave > 1.5 ? 'wave-high' : 'wave-low';
                        waveText = `${day.wave}m`;
                    }
                }

                // Extract day from date string to detect day changes
                const currentDay = day.date ? day.date.split(' ')[0] : null;
                const isDayChange = previousDay && currentDay && previousDay !== currentDay;

                if (isDayChange) {
                    dayColorIndex = (dayColorIndex + 1) % 2;
                }

                // Alternating border colors for each day
                const borderColor = dayColorIndex === 0 ? 'rgba(74, 158, 255, 0.4)' : 'rgba(255, 158, 74, 0.4)';
                const combinedStyle = `border-left: 3px solid ${borderColor};`;

                previousDay = currentDay;

                forecastRows += `
                        <tr class="${windClass}" style="${combinedStyle}">
                            <td><strong>${formatForecastDateLabel(day.date)}</strong></td>
                            <td class="${windTextClass}">${day.wind} kts</td>
                            <td class="${windTextClass}">${day.gusts} kts</td>
                            <td class="${windTextClass}">
                                <span class="wind-arrow">${windArrow}</span> ${day.direction}
                            </td>
                            <td class="${tempClass}">${day.temp}°C</td>
                            <td class="${precipClass}">${day.precipitation} mm</td>
                            ${hasWaveData ? `<td class="${waveClass}">${waveText}</td>` : ''}
                        </tr>
                    `;
            });
        }

        // Build current conditions card HTML
        let currentConditionsCardHtml = '';
        let conditionsData = null;
        let conditionsLabel = '';

        // Check if we have current conditions
        if (spot.currentConditions && spot.currentConditions.wind !== undefined) {
            conditionsData = {
                wind: spot.currentConditions.wind,
                gusts: spot.currentConditions.gusts,
                direction: spot.currentConditions.direction,
                temp: spot.currentConditions.temp,
                precipitation: 0, // Current conditions don't have precipitation
                isCurrent: true
            };
            conditionsLabel = t('nowLabel');
        } else if (forecastData && forecastData.length > 0) {
            // Use the forecast closest to current time
            const nearestForecast = findClosestForecast(forecastData);
            if (nearestForecast) {
                conditionsData = {
                    wind: nearestForecast.wind,
                    gusts: nearestForecast.gusts,
                    direction: nearestForecast.direction,
                    temp: nearestForecast.temp,
                    precipitation: nearestForecast.precipitation || 0,
                    isCurrent: false
                };
                conditionsLabel = formatForecastDateLabel(nearestForecast.date);
            }
        }

        if (conditionsData) {
            const windArrow = getWindArrow(conditionsData.direction);

            // Use individual values for coloring (not average)
            const windClass = getWindClass(conditionsData.wind);
            const gustClass = getWindClass(conditionsData.gusts);

            currentConditionsCardHtml = `
                <div class="current-conditions-card">
                    <div class="conditions-header">
                        <div class="conditions-label">${conditionsLabel}</div>
                        ${conditionsData.isCurrent ? '<div class="live-dot"></div>' : ''}
                    </div>
                    <div class="conditions-main">
                        <div class="wind-arrow-large ${windClass}" style="transform: rotate(${getWindRotation(conditionsData.direction)}deg);">
                            ↓
                        </div>
                        <div class="wind-details">
                            <div class="wind-speed ${windClass}">${conditionsData.wind} kts</div>
                            <div class="wind-label">${t('windLabel')}</div>
                        </div>
                    </div>
                    <div class="conditions-grid">
                        <div class="condition-item">
                            <div class="condition-label">${t('gustsLabel')}</div>
                            <div class="condition-value ${gustClass}">${conditionsData.gusts} kts</div>
                        </div>
                        <div class="condition-item">
                            <div class="condition-label">${t('directionLabel')}</div>
                            <div class="condition-value">${conditionsData.direction}</div>
                        </div>
                        <div class="condition-item">
                            <div class="condition-label">${t('temperatureLabel')}</div>
                            <div class="condition-value ${conditionsData.temp >= 18 ? 'temp-positive' : 'temp-negative'}">${conditionsData.temp}°C</div>
                        </div>
                        <div class="condition-item">
                            <div class="condition-label">${t('precipitationLabel')}</div>
                            <div class="condition-value ${conditionsData.precipitation === 0 ? 'precipitation-none' : 'precipitation'}">${conditionsData.precipitation} mm</div>
                        </div>
                    </div>
                </div>
            `;
        }

        // Build sponsors card HTML (desktop only)
        let sponsorsCardHtml = '';
        if (isDesktopView && currentSponsors && currentSponsors.length > 0) {
            const sponsorLinks = currentSponsors
                .map(sponsor => `<a href="${sponsor.link}" target="_blank" rel="noopener noreferrer" class="sponsor-link-item">${sponsor.name}</a>`)
                .join('');

            sponsorsCardHtml = `
                <div class="sponsors-card">
                    <div class="sponsors-card-title">${t('sponsorsOfSpotTitle')}</div>
                    <div class="sponsors-links">
                        ${sponsorLinks}
                    </div>
                </div>
            `;
        }

        // Build spot info card HTML
        let spotInfoCardHtml = '';
        if (spot.spotInfo) {
            const info = getSpotInfo(spot);
            spotInfoCardHtml = `
                <div class="spot-info-card">
                    <div class="info-grid">
                        <div class="info-item" style="grid-column: 1 / -1;">
                            <div class="info-label">${t('overviewLabel')}</div>
                            <div class="info-value">${info.description}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">${t('spotTypeLabel')}</div>
                            <div class="info-value">${info.type}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">${t('bestWindLabel')}</div>
                            <div class="info-value">${info.bestWind}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">${t('waterTempLabel')}</div>
                            <div class="info-value">${info.waterTemp}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">${t('experienceLabel')}</div>
                            <div class="info-value">${info.experience}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">${t('launchTypeLabel')}</div>
                            <div class="info-value">${info.launch}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">${t('hazardsLabel')}</div>
                            <div class="info-value">${info.hazards}</div>
                        </div>
                        <div class="info-item" style="grid-column: 1 / -1;">
                            <div class="info-label">${t('seasonLabel')}</div>
                            <div class="info-value">${info.season}</div>
                        </div>
                    </div>
                </div>
            `;
        }

        // Build embedded map HTML (desktop only)
        const embeddedMapHtml = isDesktopView && spot.embeddedMap && spot.embeddedMap.trim().length > 0
            ? `
                <div class="spot-embedded-map">
                    <div class="spot-embedded-map-frame">${spot.embeddedMap}</div>
                </div>
            `
            : '';

        // Build AI analysis card HTML
        let aiAnalysisCardHtml = '';
        const currentLang = localStorage.getItem('language') || 'en';
        const aiAnalysis = currentLang === 'pl' ? spot.aiAnalysisPl : spot.aiAnalysisEn;

        if (aiAnalysis) {
            aiAnalysisCardHtml = `
                <div class="ai-analysis-card">
                    <div class="ai-analysis">
                        <p>${aiAnalysis}</p>
                    </div>
                    <div class="modal-disclaimer">${t('aiDisclaimer')}</div>
                </div>
            `;
        }

        return `
            <div class="spot-card" style="width: 100%;">
                <div class="spot-header" style="margin-left: 0;">
                    <div class="spot-title">
                        <h2 class="spot-name spot-name-single">${spot.name}</h2>
                        <div class="last-updated">${spot.lastUpdated || t('noData')}</div>
                    </div>
                    <div class="spot-meta">
                        <span class="country-tag">${countryFlag} ${t(spot.country.replace(/\s+/g, ''))}</span>
                    </div>
                </div>
                <div class="external-links">
                    ${spot.spotInfo ? `<span class="external-link info-link" onclick="openInfoModal('${spot.name}')"></span>` : ''}
                    ${spot.windguruUrl ? `<a href="${spot.windguruUrl}" target="_blank" class="external-link">WG</a>` : ''}
                    ${spot.windfinderUrl ? `<a href="${spot.windfinderUrl}" target="_blank" class="external-link">WF</a>` : ''}
                    ${spot.icmUrl ? `<span class="external-link" onclick="openIcmModal('${spot.name}', '${spot.icmUrl}')">ICM</span>` : ''}
                    ${spot.webcamUrl ? `<a href="${spot.webcamUrl}" target="_blank" class="external-link webcam-link">CAM</a>` : ''}
                    ${spot.locationUrl ? `<a href="${spot.locationUrl}" target="_blank" class="external-link location-link">MAP</a>` : ''}
                    ${(spot.aiAnalysisEn || spot.aiAnalysisPl) ? `<span class="external-link ai-link" onclick="openAIModal('${spot.name}')">AI</span>` : ''}
                </div>

                <div class="spot-detail-container">
                    <div class="spot-detail-left">
                        ${currentConditionsCardHtml}
                        ${sponsorsCardHtml}
                        ${spotInfoCardHtml}
                        ${aiAnalysisCardHtml}
                    </div>
                    <div class="spot-detail-right">
                        ${embeddedMapHtml}
                        <table class="weather-table">
                            <thead>
                                <tr>
                                    <th>${t('dateHeader')}</th>
                                    <th>${t('windHeader')}</th>
                                    <th>${t('gustsHeader')}</th>
                                    <th>${t('directionHeader')}</th>
                                    <th>${t('tempHeader')}</th>
                                    <th>${t('rainHeader')}</th>
                                    ${hasWaveData ? `<th>${t('waveHeader')}</th>` : ''}
                                </tr>
                            </thead>
                            <tbody>
                                ${currentConditionsRow}
                                ${forecastRows}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;
    }

    // Display spot
    function displaySpot(spot) {
        const spotContainer = document.getElementById('spotContainer');
        const loadingMessage = document.getElementById('loadingMessage');
        const errorMessage = document.getElementById('errorMessage');

        clearForecastPolling();

        if (loadingMessage) {
            loadingMessage.style.display = 'none';
        }

        if (errorMessage) {
            errorMessage.style.display = 'none';
        }

        const previousSpot = currentSpot;
        currentErrorKey = null;
        currentErrorText = '';
        currentLoadingKey = null;

        let preservedMapNode = null;
        const shouldPreserveMap = previousSpot
            && previousSpot.embeddedMap
            && spot.embeddedMap
            && previousSpot.embeddedMap === spot.embeddedMap;

        if (shouldPreserveMap) {
            const currentMapFrame = document.querySelector('.spot-embedded-map-frame');
            if (currentMapFrame && currentMapFrame.firstElementChild) {
                preservedMapNode = currentMapFrame.firstElementChild;
            }
        }

        if (spot) {
            spotContainer.innerHTML = createSpotCard(spot);

            if (shouldPreserveMap && preservedMapNode) {
                const newMapFrame = document.querySelector('.spot-embedded-map-frame');
                if (newMapFrame) {
                    newMapFrame.innerHTML = '';
                    newMapFrame.appendChild(preservedMapNode);
                }
            }

            document.title = `${spot.name} - VARUN.SURF`;
        }

        currentSpot = spot;

        if (currentSpotId) {
            startBackgroundRefresh(currentSpotId);
        }
    }

    // Display error
    function displayError(messageKey) {
        const loadingMessage = document.getElementById('loadingMessage');
        const errorMessage = document.getElementById('errorMessage');
        const errorTitle = document.getElementById('errorTitle');
        const errorDescription = document.getElementById('errorDescription');

        clearForecastPolling();
        clearBackgroundRefresh();

        if (loadingMessage) {
            loadingMessage.style.display = 'none';
        }
        errorMessage.style.display = 'flex';

        const hasTranslation = typeof messageKey === 'string' && (
            (translations.en && Object.prototype.hasOwnProperty.call(translations.en, messageKey)) ||
            (translations.pl && Object.prototype.hasOwnProperty.call(translations.pl, messageKey))
        );

        if (hasTranslation) {
            currentErrorKey = messageKey;
            currentErrorText = t(messageKey);
        } else {
            currentErrorKey = null;
            currentErrorText = typeof messageKey === 'string' ? messageKey : '';
        }

        errorTitle.textContent = t('error');
        errorDescription.textContent = currentErrorText;
        currentLoadingKey = null;
    }

    // Initialize theme
    function initTheme() {
        const savedTheme = localStorage.getItem('theme') || 'dark';
        const themeToggle = document.getElementById('themeToggle');
        const themeIcon = document.getElementById('themeIcon');

        function updateTheme(theme) {
            document.documentElement.setAttribute('data-theme', theme);
            if (theme === 'light') {
                themeIcon.innerHTML = '<path d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5,5-2.24,5-5-2.24-5-5-5Zm0,7c-1.1,0-2-.9-2-2s.9-2,2-2,2,.9,2,2-.9,2-2,2Zm4.95-6.95c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0,.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44ZM7.05,16.95c.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0ZM3.51,5.64c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41Zm16.97,12.73c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41Zm3.51-6.36c0,.83-.67,1.5-1.5,1.5h-2c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5h2c.83,0,1.5,.67,1.5,1.5ZM3.5,13.5H1.5c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5H3.5c.83,0,1.5,.67,1.5,1.5s-.67,1.5-1.5,1.5ZM10.5,3.5V1.5c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5V3.5c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5Zm3,17v2c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5v-2c0-.83,.67-1.5-1.5-1.5s1.5,.67,1.5,1.5Z"/>';
            } else {
                themeIcon.innerHTML = '<path d="M15,24a12.021,12.021,0,0,1-8.914-3.966,11.9,11.9,0,0,1-3.02-9.309A12.122,12.122,0,0,1,13.085.152a13.061,13.061,0,0,1,5.031.205,2.5,2.5,0,0,1,1.108,4.226c-4.56,4.166-4.164,10.644.807,14.41a2.5,2.5,0,0,1-.7,4.32A13.894,13.894,0,0,1,15,24Z"/>';
            }
            localStorage.setItem('theme', theme);
        }

        // Set initial theme
        updateTheme(savedTheme);

        // Theme toggle event
        if (themeToggle) {
            themeToggle.addEventListener('click', () => {
                const currentTheme = document.documentElement.getAttribute('data-theme');
                const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
                updateTheme(newTheme);
            });
        }
    }

    // Initialize language
    function updateUITranslations() {
        // Update app info modal
        const appInfoModalTitle = document.getElementById('appInfoModalTitle');
        if (appInfoModalTitle) {
            appInfoModalTitle.textContent = t('appInfoModalTitle');
        }

        const appInfoDescription = document.getElementById('appInfoDescription');
        if (appInfoDescription) {
            appInfoDescription.textContent = t('appInfoDescription');
        }

        const appInfoContactTitle = document.getElementById('appInfoContactTitle');
        if (appInfoContactTitle) {
            appInfoContactTitle.textContent = t('appInfoContactTitle');
        }

        const appInfoContactText = document.getElementById('appInfoContactText');
        if (appInfoContactText) {
            appInfoContactText.innerHTML = t('appInfoContactText');
        }

        const appInfoNewSpotTitle = document.getElementById('appInfoNewSpotTitle');
        if (appInfoNewSpotTitle) {
            appInfoNewSpotTitle.textContent = t('appInfoNewSpotTitle');
        }

        const appInfoNewSpotText = document.getElementById('appInfoNewSpotText');
        if (appInfoNewSpotText) {
            appInfoNewSpotText.innerHTML = t('appInfoNewSpotText');
        }

        const appInfoCollaborationTitle = document.getElementById('appInfoCollaborationTitle');
        if (appInfoCollaborationTitle) {
            appInfoCollaborationTitle.textContent = t('appInfoCollaborationTitle');
        }

        const appInfoCollaborationText = document.getElementById('appInfoCollaborationText');
        if (appInfoCollaborationText) {
            appInfoCollaborationText.innerHTML = t('appInfoCollaborationText');
        }

        // Update info toggle button label
        const infoToggleLabel = document.getElementById('infoToggleLabel');
        if (infoToggleLabel) {
            infoToggleLabel.textContent = t('infoButtonLabel');
        }

        // Update loading text
        const loadingMessage = document.getElementById('loadingMessage');
        if (loadingMessage && loadingMessage.style.display !== 'none') {
            const loadingTextEl = document.getElementById('loadingText');
            if (loadingTextEl) {
                const key = currentLoadingKey || 'loadingSpotData';
                loadingTextEl.textContent = t(key);
            }
        }

        const errorMessage = document.getElementById('errorMessage');
        if (errorMessage && errorMessage.style.display !== 'none') {
            const errorTitle = document.getElementById('errorTitle');
            const errorDescription = document.getElementById('errorDescription');

            if (errorTitle) {
                errorTitle.textContent = t('error');
            }

            if (errorDescription) {
                if (currentErrorKey) {
                    currentErrorText = t(currentErrorKey);
                }
                errorDescription.textContent = currentErrorText;
            }
        }
    }

    function initLanguage() {
        const savedLang = localStorage.getItem('language') || 'en';
        currentLanguage = savedLang;

        const languageToggle = document.getElementById('languageToggle');
        const langCode = document.getElementById('langCode');

        // Update UI translations on init
        updateUITranslations();

        if (languageToggle && langCode) {
            langCode.textContent = savedLang.toUpperCase();

            languageToggle.addEventListener('click', () => {
                // Toggle between EN and PL only
                const newLang = currentLanguage === 'en' ? 'pl' : 'en';

                currentLanguage = newLang;
                langCode.textContent = newLang.toUpperCase();
                localStorage.setItem('language', newLang);

                // Update UI translations
                updateUITranslations();

                // Reload the spot to apply translations
                if (currentSpot) {
                    displaySpot(currentSpot);
                }

            });
        }
    }

    // Setup modal close handlers
    function setupModals() {
        const infoModalClose = document.getElementById('infoModalClose');
        const aiModalClose = document.getElementById('aiModalClose');
        const icmModalClose = document.getElementById('icmModalClose');
        const appInfoModalClose = document.getElementById('appInfoModalClose');

        if (infoModalClose) {
            infoModalClose.addEventListener('click', closeInfoModal);
        }

        if (aiModalClose) {
            aiModalClose.addEventListener('click', closeAIModal);
        }

        if (icmModalClose) {
            icmModalClose.addEventListener('click', closeIcmModal);
        }

        if (appInfoModalClose) {
            appInfoModalClose.addEventListener('click', closeAppInfoModal);
        }

        // Close modals on overlay click
        document.querySelectorAll('.modal-overlay').forEach(overlay => {
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) {
                    closeInfoModal();
                    closeAIModal();
                    closeIcmModal();
                    closeAppInfoModal();
                }
            });
        });
    }

    // Setup info toggle
    function setupInfoToggle() {
        const infoToggle = document.getElementById('infoToggle');
        if (infoToggle) {
            infoToggle.addEventListener('click', () => {
                openAppInfoModal();
            });
        }
    }

    // Setup hamburger menu
    function setupHamburgerMenu() {
        const hamburgerMenu = document.getElementById('hamburgerMenu');
        const headerControls = document.getElementById('headerControls');
        const mainContent = document.querySelector('.main-content');

        if (!hamburgerMenu) return;

        hamburgerMenu.addEventListener('click', () => {
            const isOpen = headerControls.classList.contains('show');

            if (isOpen) {
                headerControls.classList.remove('show');
                hamburgerMenu.classList.remove('active');
                hamburgerMenu.textContent = '☰';
                if (mainContent) {
                    mainContent.classList.remove('menu-open');
                }
            } else {
                headerControls.classList.add('show');
                hamburgerMenu.classList.add('active');
                hamburgerMenu.textContent = '✕';
                if (mainContent) {
                    mainContent.classList.add('menu-open');
                }
            }
        });
    }

    // Setup header navigation
    function setupHeaderNavigation() {
        const headerLogo = document.getElementById('headerLogo');
        const headerTitle = document.getElementById('headerTitle');

        if (headerLogo) {
            headerLogo.addEventListener('click', () => {
                window.location.href = '/';
            });
        }

        if (headerTitle) {
            headerTitle.addEventListener('click', () => {
                window.location.href = '/';
            });
        }
    }

    // Setup window resize handler to re-render dates when crossing mobile/desktop threshold
    function setupResizeHandler() {
        let resizeTimeout;
        let wasMobile = window.innerWidth <= 768;

        window.addEventListener('resize', () => {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(() => {
                const isMobile = window.innerWidth <= 768;

                // Only re-render if we crossed the mobile/desktop threshold
                if (isMobile !== wasMobile) {
                    wasMobile = isMobile;
                    if (currentSpot) {
                        displaySpot(currentSpot);
                    }
                }
            }, 150); // Debounce resize events
        });
    }

    // Setup model dropdown
    function setupModelDropdown() {
        const modelDropdown = document.getElementById('modelDropdown');
        const modelDropdownMenu = document.getElementById('modelDropdownMenu');
        const modelDropdownText = document.getElementById('modelDropdownText');

        if (!modelDropdown || !modelDropdownMenu || !modelDropdownText) {
            return;
        }

        // Initialize with current model
        const currentModel = getSelectedModel();
        modelDropdownText.textContent = currentModel.toUpperCase();

        // Set up dropdown options
        const options = document.querySelectorAll('#modelDropdownMenu .dropdown-option');
        options.forEach(option => {
            // Mark current option as selected
            if (option.dataset.value === currentModel) {
                option.classList.add('selected');
            }

            // Add click handler
            option.addEventListener('click', () => {
                const newModel = option.dataset.value;
                setSelectedModel(newModel);
                modelDropdownText.textContent = newModel.toUpperCase();

                // Update selected state
                options.forEach(opt => opt.classList.remove('selected'));
                option.classList.add('selected');

                // Close dropdown
                modelDropdownMenu.classList.remove('open');
                modelDropdown.classList.remove('open');

                // Reload data with new model
                if (currentSpotId) {
                    setLoadingMessage('loadingSpotData');
                    fetchSpotData(currentSpotId)
                        .then(spot => {
                            if (hasForecastData(spot)) {
                                displaySpot(spot);
                            } else {
                                setLoadingMessage('loadingForecast');
                                startForecastPolling(currentSpotId);
                            }
                        })
                        .catch(error => {
                            if (error && error.status === 404) {
                                displayError('spotNotFound');
                            } else {
                                displayError('errorLoadingSpot');
                            }
                        });
                }
            });
        });

        // Toggle dropdown on button click
        modelDropdown.addEventListener('click', () => {
            modelDropdownMenu.classList.toggle('open');
            modelDropdown.classList.toggle('open');
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!modelDropdown.contains(e.target) && !modelDropdownMenu.contains(e.target)) {
                modelDropdownMenu.classList.remove('open');
                modelDropdown.classList.remove('open');
            }
        });
    }

    // Main initialization
    async function init() {
        initTheme();
        initLanguage();
        setupModals();
        setupInfoToggle();
        setupHamburgerMenu();
        setupHeaderNavigation();
        setupResizeHandler();
        setupModelDropdown();

        const spotId = getSpotIdFromUrl();
        currentSpotId = spotId;

        if (!spotId) {
            displayError('invalidSpotId');
            return;
        }

        setLoadingMessage('loadingSpotData');

        // Wait at least 2 seconds before loading spot data
        const startTime = Date.now();
        const minDelay = 2000;

        try {
            const spot = await fetchSpotData(spotId);
            const elapsed = Date.now() - startTime;
            const remainingDelay = minDelay - elapsed;

            if (remainingDelay > 0) {
                await new Promise(resolve => setTimeout(resolve, remainingDelay));
            }

            // Load sponsors for this spot
            await renderSpotSponsor(spotId);

            if (hasForecastData(spot)) {
                displaySpot(spot);
            } else {
                setLoadingMessage('loadingForecast');
                startForecastPolling(spotId);
            }
        } catch (error) {
            if (error && error.status === 404) {
                displayError('spotNotFound');
            } else {
                displayError('errorLoadingSpot');
            }
        }
    }

    // Sponsors functionality
    async function fetchAllSponsors() {
        try {
            const response = await fetch('/api/v1/sponsors');
            if (!response.ok) {
                return [];
            }
            const sponsors = await response.json();
            return sponsors || [];
        } catch (error) {
            console.error('Error fetching sponsors:', error);
            return [];
        }
    }

    async function renderSpotSponsor(spotId) {
        const allSponsors = await fetchAllSponsors();
        currentSponsors = allSponsors.filter(sponsor => sponsor.id === parseInt(spotId));
    }

    // Make functions global for onclick handlers
    window.openAIModal = openAIModal;
    window.closeAIModal = closeAIModal;
    window.openInfoModal = openInfoModal;
    window.closeInfoModal = closeInfoModal;
    window.openIcmModal = openIcmModal;
    window.closeIcmModal = closeIcmModal;

    // Start the application
    document.addEventListener('DOMContentLoaded', init);
