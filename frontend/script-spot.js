    let currentSpot = null;
    let currentLanguage = 'en';

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

    // Fetch single spot data
    async function fetchSpotData(spotId) {
        try {
            const response = await fetch(`${API_ENDPOINT}/${spotId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            return data;
        } catch (error) {
            console.error('Error fetching spot data:', error);
            throw error;
        }
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

    // Helper function to translate day names
    function translateDayName(dayName) {
        const dayMap = {
            'Mon': t('dayMon'),
            'Tue': t('dayTue'),
            'Wed': t('dayWed'),
            'Thu': t('dayThu'),
            'Fri': t('dayFri'),
            'Sat': t('daySat'),
            'Sun': t('daySun')
        };
        return dayMap[dayName] || dayName;
    }

    // Helper function to get country flag
    function getCountryFlag(country) {
        const flags = {
            'Poland': '🇵🇱',
            'Czechia': '🇨🇿',
            'Austria': '🇦🇹',
            'Belgium': '🇧🇪',
            'Denmark': '🇩🇰',
            'Germany': '🇩🇪',
            'Netherlands': '🇳🇱',
            'Spain': '🇪🇸',
            'Portugal': '🇵🇹',
            'Italy': '🇮🇹',
            'France': '🇫🇷',
            'Brazil': '🇧🇷',
            'Argentina': '🇦🇷',
            'Greece': '🇬🇷',
            'Croatia': '🇭🇷',
            'Romania': '🇷🇴',
            'Bulgaria': '🇧🇬',
            'Turkey': '🇹🇷',
            'Egypt': '🇪🇬',
            'Morocco': '🇲🇦',
            'South Africa': '🇿🇦',
            'USA': '🇺🇸',
            'Canada': '🇨🇦',
            'Mexico': '🇲🇽',
            'Colombia': '🇨🇴',
            'Venezuela': '🇻🇪',
            'Chile': '🇨🇱',
            'Peru': '🇵🇪',
            'Uruguay': '🇺🇾',
            'Dominican Republic': '🇩🇴',
            'Cuba': '🇨🇺',
            'Jamaica': '🇯🇲',
            'Costa Rica': '🇨🇷',
            'Panama': '🇵🇦',
            'Australia': '🇦🇺',
            'New Zealand': '🇳🇿',
            'Thailand': '🇹🇭',
            'Vietnam': '🇻🇳',
            'Philippines': '🇵🇭',
            'Indonesia': '🇮🇩',
            'Sri Lanka': '🇱🇰',
            'United Arab Emirates': '🇦🇪',
            'Oman': '🇴🇲'
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

        const info = currentSpot.spotInfo;
        spotInfoContent.innerHTML = `
                <div class="info-grid">
                    <div class="info-item" style="grid-column: 1 / -1;">
                        <div class="info-label">Overview</div>
                        <div class="info-value">${info.description}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">Spot Type</div>
                        <div class="info-value">${info.type}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">Best Wind</div>
                        <div class="info-value">${info.bestWind}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">Water Temperature</div>
                        <div class="info-value">${info.waterTemp}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">Experience Level</div>
                        <div class="info-value">${info.experience}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">Launch Type</div>
                        <div class="info-value">${info.launch}</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">Hazards</div>
                        <div class="info-value">${info.hazards}</div>
                    </div>
                    <div class="info-item" style="grid-column: 1 / -1;">
                        <div class="info-label">Season</div>
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
        if (!currentSpot || !currentSpot.aiAnalysis) return;

        const modal = document.getElementById('aiModal');
        const modalTitle = document.getElementById('aiModalTitle');
        const aiAnalysisContent = document.getElementById('aiAnalysisContent');

        modalTitle.textContent = `${spotName} - AI Analysis`;
        aiAnalysisContent.innerHTML = `<p>${currentSpot.aiAnalysis}</p>`;

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

        modalTitle.textContent = `${spotName} - ICM Forecast`;
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

    // Create spot card HTML
    function createSpotCard(spot) {
        const countryFlag = getCountryFlag(spot.country);

        // Use forecastHourly if available, otherwise fall back to forecast
        const forecastData = (spot.forecastHourly && spot.forecastHourly.length > 0)
            ? spot.forecastHourly
            : (spot.forecast || []);

        // Check if any forecast has wave data
        const hasWaveData = forecastData && forecastData.some(day => day.wave !== undefined);

        // Current conditions row (to be added at the top of the table)
        let currentConditionsRow = '';
        if (spot.currentConditions && spot.currentConditions.wind !== undefined) {
            let windClass, windTextClass;
            const avgWind = (spot.currentConditions.wind + spot.currentConditions.gusts) / 2;
            if (avgWind < 12) {
                windClass = 'weak-wind';
                windTextClass = 'wind-weak';
            } else if (avgWind >= 12 && avgWind < 18) {
                windClass = 'moderate-wind';
                windTextClass = 'wind-moderate';
            } else if (avgWind >= 18 && avgWind <= 22) {
                windClass = 'strong-wind';
                windTextClass = 'wind-strong';
            } else {
                windClass = 'extreme-wind';
                windTextClass = 'wind-extreme';
            }

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

            currentConditionsRow = `
                    <tr class="${windClass}" style="border-top: 2px solid #404040;">
                        <td>
                            <div class="live-indicator">
                                <strong class="live-text">${t('nowLabel')}</strong>
                                <div class="live-dot"></div>
                            </div>
                        </td>
                        <td class="${windTextClass}">${spot.currentConditions.wind} kts</td>
                        <td class="${windTextClass}">${spot.currentConditions.gusts} kts</td>
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

                forecastRows += `
                        <tr class="${windClass}">
                            <td><strong>${translateDayName(day.date)}</strong></td>
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
                    ${spot.aiAnalysis ? `<span class="external-link ai-link" onclick="openAIModal('${spot.name}')">AI</span>` : ''}
                </div>
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
        `;
    }

    // Display spot
    function displaySpot(spot) {
        currentSpot = spot;
        const spotContainer = document.getElementById('spotContainer');
        const loadingMessage = document.getElementById('loadingMessage');
        const errorMessage = document.getElementById('errorMessage');

        loadingMessage.style.display = 'none';
        errorMessage.style.display = 'none';

        if (spot) {
            spotContainer.innerHTML = createSpotCard(spot);
            document.title = `${spot.name} - VARUN.SURF`;
        }
    }

    // Display error
    function displayError(message) {
        const loadingMessage = document.getElementById('loadingMessage');
        const errorMessage = document.getElementById('errorMessage');
        const errorTitle = document.getElementById('errorTitle');
        const errorDescription = document.getElementById('errorDescription');

        loadingMessage.style.display = 'none';
        errorMessage.style.display = 'flex';
        errorTitle.textContent = t('error');
        errorDescription.textContent = message;
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
        const loadingText = document.getElementById('loadingText');
        if (loadingText) {
            loadingText.textContent = t('loadingText');
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

    // Main initialization
    async function init() {
        initTheme();
        initLanguage();
        setupModals();
        setupInfoToggle();
        setupHamburgerMenu();
        setupHeaderNavigation();

        const spotId = getSpotIdFromUrl();

        if (!spotId) {
            displayError(t('invalidSpotId'));
            return;
        }

        try {
            const spot = await fetchSpotData(spotId);
            displaySpot(spot);
        } catch (error) {
            displayError(t('errorLoadingSpot'));
        }
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