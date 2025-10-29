    let globalWeatherData = [];
    let availableCountries = new Set();
    let currentSearchQuery = '';
    let showingFavorites = false;
    let autoRefreshInterval = null;

    // Configuration
    const API_ENDPOINT = '/api/v1/spots';
    const AUTO_REFRESH_INTERVAL = 60 * 1000; // 1 minute in milliseconds

    // Favorites management
    function getFavorites() {
        const favorites = localStorage.getItem('favoriteSpots');
        return favorites ? JSON.parse(favorites) : [];
    }

    function saveFavorites(favorites) {
        localStorage.setItem('favoriteSpots', JSON.stringify(favorites));
    }

    function isFavorite(spotName) {
        const favorites = getFavorites();
        return favorites.includes(spotName);
    }

    function toggleFavorite(spotName) {
        let favorites = getFavorites();
        const index = favorites.indexOf(spotName);

        if (index > -1) {
            // Remove from favorites
            favorites.splice(index, 1);
        } else {
            // Add to favorites
            favorites.push(spotName);
        }

        saveFavorites(favorites);

        // Update all instances of this spot's favorite icon
        const allFavoriteIcons = document.querySelectorAll('.favorite-icon');
        allFavoriteIcons.forEach(icon => {
            const card = icon.closest('.spot-card');
            if (card) {
                const spotNameElement = card.querySelector('.spot-name');
                if (spotNameElement && spotNameElement.textContent === spotName) {
                    if (index > -1) {
                        icon.classList.remove('favorited');
                        icon.title = 'Add to favorites';
                    } else {
                        icon.classList.add('favorited');
                        icon.title = 'Remove from favorites';
                    }

                    // Add animation
                    icon.classList.add('animate');
                    setTimeout(() => icon.classList.remove('animate'), 400);
                }
            }
        });

        // If showing favorites, re-render to reflect changes
        if (showingFavorites) {
            renderFavorites();
        }
    }

    // Theme functionality
    function initTheme() {
        const savedTheme = localStorage.getItem('theme') || 'dark';
        const themeToggle = document.getElementById('themeToggle');
        const themeIcon = document.getElementById('themeIcon');

        function updateTheme(theme) {
            document.documentElement.setAttribute('data-theme', theme);
            if (theme === 'light') {
                themeIcon.innerHTML = '<path d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5,5-2.24,5-5-2.24-5-5-5Zm0,7c-1.1,0-2-.9-2-2s.9-2,2-2,2,.9,2,2-.9,2-2,2Zm4.95-6.95c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0,.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44ZM7.05,16.95c.59,.59,.59,1.54,0,2.12l-1.41,1.41c-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44c-.59-.59-.59-1.54,0-2.12l1.41-1.41c.59-.59,1.54-.59,2.12,0ZM3.51,5.64c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41Zm16.97,12.73c.59,.59,.59,1.54,0,2.12-.29,.29-.68,.44-1.06,.44s-.77-.15-1.06-.44l-1.41-1.41c-.59-.59-.59-1.54,0-2.12,.59-.59,1.54-.59,2.12,0l1.41,1.41Zm3.51-6.36c0,.83-.67,1.5-1.5,1.5h-2c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5h2c.83,0,1.5,.67,1.5,1.5ZM3.5,13.5H1.5c-.83,0-1.5-.67-1.5-1.5s.67-1.5,1.5-1.5H3.5c.83,0,1.5,.67,1.5,1.5s-.67,1.5-1.5,1.5ZM10.5,3.5V1.5c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5V3.5c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5Zm3,17v2c0,.83-.67,1.5-1.5,1.5s-1.5-.67-1.5-1.5v-2c0-.83,.67-1.5,1.5-1.5s1.5,.67,1.5,1.5Z"/>';
            } else {
                themeIcon.innerHTML = '<path d="M15,24a12.021,12.021,0,0,1-8.914-3.966,11.9,11.9,0,0,1-3.02-9.309A12.122,12.122,0,0,1,13.085.152a13.061,13.061,0,0,1,5.031.205,2.5,2.5,0,0,1,1.108,4.226c-4.56,4.166-4.164,10.644.807,14.41a2.5,2.5,0,0,1-.7,4.32A13.894,13.894,0,0,1,15,24Z"/>';
            }
            localStorage.setItem('theme', theme);
            // Update sponsor logos when theme changes
            updateSponsorLogosForTheme(theme);
        }

        // Set initial theme
        updateTheme(savedTheme);

        // Theme toggle event
        themeToggle.addEventListener('click', () => {
            const currentTheme = document.documentElement.getAttribute('data-theme');
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            updateTheme(newTheme);
        });

        // Make logo clickable to reload page
        const headerLogo = document.getElementById('headerLogo');
        if (headerLogo) {
            headerLogo.addEventListener('click', () => {
                window.location.reload();
            });
        }
    }

    // Language functionality
    function initLanguage() {
        const savedLanguage = localStorage.getItem('language') || 'en';
        const languageToggle = document.getElementById('languageToggle');

        function updateLanguage(lang) {
            localStorage.setItem('language', lang);

            // Update all UI elements with translations
            updateUITranslations();
        }

        function updateUITranslations() {
            // Update page title
            document.title = t('pageTitle');

            // Update search placeholder
            const searchInput = document.getElementById('searchInput');
            if (searchInput) {
                searchInput.placeholder = t('searchPlaceholder');
            }

            // Update tooltips
            const themeToggle = document.getElementById('themeToggle');
            if (themeToggle) {
                themeToggle.title = t('themeToggleTooltip');
            }

            const favoritesToggle = document.getElementById('favoritesToggle');
            if (favoritesToggle) {
                favoritesToggle.title = t('favoritesToggleTooltip');
            }

            const infoToggle = document.getElementById('infoToggle');
            if (infoToggle) {
                infoToggle.title = t('infoToggleTooltip');
            }

            const infoToggleLabel = document.getElementById('infoToggleLabel');
            if (infoToggleLabel) {
                infoToggleLabel.textContent = t('infoButtonLabel');
            }

            const kiteSizeToggle = document.getElementById('kiteSizeToggle');
            if (kiteSizeToggle) {
                kiteSizeToggle.title = t('kiteSizeToggleTooltip');
            }

            const columnToggle = document.getElementById('columnToggle');
            if (columnToggle) {
                columnToggle.title = t('columnToggleTooltip');
            }

            if (languageToggle) {
                languageToggle.title = t('languageToggleTooltip');
            }

            // Update language code text
            const langCode = document.getElementById('langCode');
            if (langCode) {
                langCode.textContent = t('langCode');
            }

            // Update spot counter text
            const spotCounter = document.getElementById('spotCounter');
            if (spotCounter) {
                const spotCounterNumber = document.getElementById('spotCounterNumber');
                const currentCount = spotCounterNumber ? spotCounterNumber.textContent : '0';
                spotCounter.innerHTML = `<span class="spot-counter-number" id="spotCounterNumber">${currentCount}</span><span>${t('spotsCount')}</span>`;
            }

            // Update footer
            const footerDisclaimer = document.querySelector('.footer-disclaimer');
            if (footerDisclaimer) {
                footerDisclaimer.textContent = t('footerDisclaimer');
            }

            // Update "All" in dropdown if selected
            if (globalWeatherData.length > 0) {
                populateCountryDropdown(globalWeatherData);
            } else {
                const savedCountry = localStorage.getItem('selectedCountry') || 'all';
                updateSelectedCountryLabel(savedCountry);
            }

            // Update dropdown "All" option
            const dropdownMenu = document.getElementById('dropdownMenu');
            if (dropdownMenu) {
                const allOption = dropdownMenu.querySelector('[data-country="all"]');
                if (allOption) {
                    allOption.textContent = `🌎 ${t('allCountries')}`;
                }
            }

            // Update loading message if visible
            const loadingText = document.querySelector('.loading-text');
            if (loadingText) {
                loadingText.textContent = t('loadingText');
            }

            // Update modal titles
            const aiModalTitle = document.querySelector('#aiModal .modal-title span:first-child');
            if (aiModalTitle && aiModalTitle.textContent === '⟡') {
                // AI modal title is dynamic, leave as is
            }

            const infoModalTitle = document.querySelector('#infoModal .modal-title span:first-child');
            if (infoModalTitle && infoModalTitle.textContent === '🏄') {
                // Info modal title is dynamic, leave as is
            }

            const icmModalTitle = document.querySelector('#icmModal .modal-title span:first-child');
            if (icmModalTitle && icmModalTitle.textContent === '📊') {
                // ICM modal title is dynamic, leave as is
            }

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

            // Update kite size calculator modal content
            const kiteSizeModalTitle = document.querySelector('#kiteSizeModal .modal-title span');
            if (kiteSizeModalTitle) {
                kiteSizeModalTitle.textContent = t('kiteSizeCalculatorTitle');
            }

            const windSpeedLabel = document.querySelector('label[for="windSpeed"]');
            if (windSpeedLabel) {
                windSpeedLabel.textContent = t('windSpeedLabel');
            }

            const windSpeedInput = document.getElementById('windSpeed');
            if (windSpeedInput) {
                windSpeedInput.placeholder = t('windSpeedPlaceholder');
            }

            const riderWeightLabel = document.querySelector('label[for="riderWeight"]');
            if (riderWeightLabel) {
                riderWeightLabel.textContent = t('riderWeightLabel');
            }

            const riderWeightInput = document.getElementById('riderWeight');
            if (riderWeightInput) {
                riderWeightInput.placeholder = t('riderWeightPlaceholder');
            }

            const skillLevelLabel = document.querySelector('label[for="skillLevel"]');
            if (skillLevelLabel) {
                skillLevelLabel.textContent = t('skillLevelLabel');
            }

            const skillLevelSelect = document.getElementById('skillLevel');
            if (skillLevelSelect) {
                const options = skillLevelSelect.querySelectorAll('option');
                options[0].textContent = t('skillLevelPlaceholder');
                options[1].textContent = t('skillBeginnerFlat');
                options[2].textContent = t('skillBeginnerSmall');
                options[3].textContent = t('skillIntermediateFlat');
                options[4].textContent = t('skillIntermediateMedium');
                options[5].textContent = t('skillAdvancedFlat');
                options[6].textContent = t('skillAdvancedMedium');
                options[7].textContent = t('skillAdvancedLarge');
            }

            const calculateBtn = document.getElementById('calculateBtn');
            if (calculateBtn) {
                calculateBtn.textContent = t('calculateButton');
            }

            const calcResultTitle = document.querySelector('#calcResult .calc-result-title');
            if (calcResultTitle) {
                calcResultTitle.textContent = t('recommendedEquipment');
            }

            const kiteSizeLabel = document.querySelector('#calcResult .calc-result-item:nth-child(2) .calc-result-label');
            if (kiteSizeLabel) {
                kiteSizeLabel.textContent = t('kiteSizeLabel');
            }

            const boardSizeLabel = document.querySelector('#calcResult .calc-result-item:nth-child(3) .calc-result-label');
            if (boardSizeLabel) {
                boardSizeLabel.textContent = t('boardSizeLabel');
            }

            const calcDisclaimer = document.querySelector('#calcResult .modal-disclaimer');
            if (calcDisclaimer) {
                calcDisclaimer.textContent = t('calcDisclaimer');
            }

            // Update AI modal disclaimer
            const aiDisclaimer = document.querySelector('#aiModal .modal-disclaimer');
            if (aiDisclaimer) {
                aiDisclaimer.textContent = t('aiDisclaimer');
            }

            // Re-render spots to update table headers and content
            if (globalWeatherData.length > 0) {
                if (showingFavorites) {
                    renderFavorites();
                } else {
                    renderSpots(currentFilter, currentSearchQuery, true);
                }
            }
        }

        // Set initial language and update UI
        updateLanguage(savedLanguage);

        // Language toggle event
        languageToggle.addEventListener('click', () => {
            const currentLang = localStorage.getItem('language') || 'en';
            const newLang = currentLang === 'en' ? 'pl' : 'en';
            updateLanguage(newLang);
        });
    }

    async function fetchWeatherData() {
        try {
            const response = await fetch(API_ENDPOINT, {cache: 'no-store'});

            if (!response.ok) {
                throw new Error(`HTTP Error: ${response.status}`);
            }

            const data = await response.json();

            if (!Array.isArray(data)) {
                throw new Error('Invalid data format: Expected array of spots');
            }

            return data;
        } catch (error) {
            console.error('Error fetching weather data:', error);
            throw error;
        }
    }

    function showLoadingMessage() {
        const spotsGrid = document.getElementById('spotsGrid');
        spotsGrid.innerHTML = `
                <div class="loading-message">
                    <div class="loading-spinner"></div>
                    <span class="loading-text">${t('loadingText')}</span>
                </div>
            `;
    }

    function showErrorMessage(error) {
        const spotsGrid = document.getElementById('spotsGrid');

        let errorTitle = t('errorLoadDataTitle');
        let errorMessage = t('errorLoadDataDescription');

        if (error.message.includes('HTTP Error: 404')) {
            errorTitle = t('errorDataNotFoundTitle');
            errorMessage = t('errorDataNotFoundDescription');
        } else if (error.message.includes('HTTP Error: 500')) {
            errorTitle = t('errorServerTitle');
            errorMessage = t('errorServerDescription');
        } else if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
            errorTitle = t('errorConnectionTitle');
            errorMessage = t('errorConnectionDescription');
        } else if (error.message.includes('JSON') || error.message.includes('Invalid data format')) {
            errorTitle = t('errorDataFormatTitle');
            errorMessage = t('errorDataFormatDescription');
        }

        spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⚠️</span>
                    <div class="error-title">${errorTitle}</div>
                    <div class="error-description">
                        ${errorMessage}<br/>
                        ${t('errorRefresh')}
                    </div>
                </div>
            `;
    }

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

    function translateDayName(dayName) {
        const dayMap = {
            'Mon': t('dayMon'),
            'Tue': t('dayTue'),
            'Wed': t('dayWed'),
            'Thu': t('dayThu'),
            'Fri': t('dayFri'),
            'Sat': t('daySat'),
            'Sun': t('daySun'),
            'Today': t('dayToday'),
            'Tomorrow': t('dayTomorrow'),
            'Day 3': t('dayDay3'),
            'Day 4': t('dayDay4'),
            'Day 5': t('dayDay5')
        };
        return dayMap[dayName] || dayName;
    }

    function populateCountryDropdown(data) {
        const dropdownMenu = document.getElementById('dropdownMenu');
        const selectedCountry = document.getElementById('selectedCountry');
        availableCountries.clear();

        // Collect all unique countries from the data
        data.forEach(spot => {
            if (spot.country) {
                availableCountries.add(spot.country);
            }
        });

        // Sort countries alphabetically
        const sortedCountries = Array.from(availableCountries).sort();

        // Get saved country from localStorage
        const savedCountry = localStorage.getItem('selectedCountry') || 'all';

        // Build dropdown HTML
        const allLabel = t('allCountries');
        let dropdownHTML = `<div class="dropdown-option ${savedCountry === 'all' ? 'selected' : ''}" data-country="all">🌎 ${allLabel}</div>`;

        sortedCountries.forEach(country => {
            const countryFlag = getCountryFlag(country);
            const isSelected = savedCountry === country ? 'selected' : '';
            const countryName = t(country.replace(/\s+/g, ''));
            dropdownHTML += `<div class="dropdown-option ${isSelected}" data-country="${country}">${countryFlag} ${countryName.toUpperCase()}</div>`;
        });

        dropdownMenu.innerHTML = dropdownHTML;

        // Update the selected country text in the button
        updateSelectedCountryLabel(savedCountry);

        // Re-attach event listeners for the new dropdown options
        setupDropdownEvents();
    }

    function updateSelectedCountryLabel(countryKey) {
        const selectedCountry = document.getElementById('selectedCountry');
        if (!selectedCountry) {
            return;
        }

        if (!countryKey || countryKey === 'all') {
            selectedCountry.textContent = `🌎 ${t('allCountries')}`;
            return;
        }

        const countryFlag = getCountryFlag(countryKey);
        const countryName = t(countryKey.replace(/\s+/g, ''));
        selectedCountry.textContent = `${countryFlag} ${countryName.toUpperCase()}`;
    }

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

    function openAppInfoModal() {
        const modal = document.getElementById('appInfoModal');
        if (!modal) {
            return;
        }
        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeAppInfoModal() {
        const modal = document.getElementById('appInfoModal');
        if (!modal) {
            return;
        }
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    function openAIModal(spotName) {
        const spot = globalWeatherData.find(spot => spot.name === spotName);
        if (!spot || !spot.aiAnalysis) return;

        const modal = document.getElementById('aiModal');
        const modalSpotName = document.getElementById('modalSpotName');
        const aiAnalysisContent = document.getElementById('aiAnalysisContent');

        modalSpotName.textContent = `AI Analysis - ${spotName}`;
        aiAnalysisContent.innerHTML = spot.aiAnalysis.trim();

        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function openInfoModal(spotName) {
        const spot = globalWeatherData.find(spot => spot.name === spotName);
        if (!spot || !spot.spotInfo) return;

        const modal = document.getElementById('infoModal');
        const modalSpotName = document.getElementById('infoModalSpotName');
        const spotInfoContent = document.getElementById('spotInfoContent');

        modalSpotName.textContent = `${spotName}`;

        const info = spot.spotInfo;
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

    function closeAIModal() {
        const modal = document.getElementById('aiModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    function closeInfoModal() {
        const modal = document.getElementById('infoModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';
    }

    function openIcmModal(spotName, icmUrl) {
        const modal = document.getElementById('icmModal');
        const modalSpotName = document.getElementById('icmModalSpotName');
        const icmImage = document.getElementById('icmImage');

        modalSpotName.textContent = `${spotName} - ICM Forecast`;
        icmImage.src = icmUrl;
        icmImage.alt = `ICM Forecast for ${spotName}`;

        modal.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    function closeIcmModal() {
        const modal = document.getElementById('icmModal');
        modal.classList.remove('active');
        document.body.style.overflow = 'auto';

        // Clear the image source to stop loading
        const icmImage = document.getElementById('icmImage');
        icmImage.src = '';
    }

    function createSpotCard(spot) {
        const card = document.createElement('div');
        card.className = 'spot-card';
        card.dataset.country = t(spot.country.replace(/\s+/g, ''));

        // Check if spot has wave data
        const hasWaveData = spot.forecast && spot.forecast.some(day => day.wave !== undefined) ||
            (spot.currentConditions && spot.currentConditions.wave !== undefined);

        let forecastRows = '';
        if (spot.forecast && Array.isArray(spot.forecast)) {
            spot.forecast.forEach(day => {
                const windClass = getWindClass(day.wind);
                const windTextClass = windClass;
                const gustTextClass = getWindClass(day.gusts);

                // Calculate average of base wind and gusts for row background
                const averageWind = (day.wind + day.gusts) / 2;
                const averageWindClass = getWindClass(averageWind);

                // Use average wind class for row background
                const rowWindClass = averageWindClass === 'wind-weak' ? 'weak-wind' :
                                    averageWindClass === 'wind-moderate' ? 'moderate-wind' :
                                    averageWindClass === 'wind-strong' ? 'strong-wind' : 'extreme-wind';

                const tempClass = day.temp >= 18 ? 'temp-positive' : 'temp-negative';
                const windArrow = getWindArrow(day.direction);
                const precipClass = day.precipitation === 0 ? 'precipitation-none' : 'precipitation';

                // Wave classes
                let waveClass = '';
                let waveText = '-';
                if (day.wave !== undefined) {
                    if (day.wave < 1.0) {
                        waveClass = 'wave-small';
                    } else if (day.wave >= 1.0 && day.wave < 2.0) {
                        waveClass = 'wave-moderate';
                    } else {
                        waveClass = 'wave-large';
                    }
                    waveText = `${day.wave}`;
                }

                forecastRows += `
                        <tr class="${rowWindClass}">
                            <td><strong>${translateDayName(day.date)}</strong></td>
                            <td class="${windTextClass}">${day.wind} kts</td>
                            <td class="${gustTextClass}">${day.gusts} kts</td>
                            <td class="${windTextClass}">
                                <span class="wind-arrow">${windArrow}</span> ${day.direction}
                            </td>
                            <td class="${tempClass}">${day.temp}°C</td>
                            <td class="${precipClass}">${day.precipitation}%</td>
                            ${hasWaveData ? `<td class="${waveClass}">${waveText}</td>` : ''}
                        </tr>
                    `;
            });
        }

        let currentConditionsRow = '';
        if (spot.currentConditions) {
            const windClass = getWindClass(spot.currentConditions.wind);
            const windTextClass = windClass;
            const gustTextClass = getWindClass(spot.currentConditions.gusts);

            // Calculate average of base wind and gusts for row background
            const averageWind = (spot.currentConditions.wind + spot.currentConditions.gusts) / 2;
            const averageWindClass = getWindClass(averageWind);

            // Use average wind class for row background
            const rowWindClass = averageWindClass === 'wind-weak' ? 'weak-wind' :
                                averageWindClass === 'wind-moderate' ? 'moderate-wind' :
                                averageWindClass === 'wind-strong' ? 'strong-wind' : 'extreme-wind';

            const tempClass = spot.currentConditions.temp >= 20 ? 'temp-positive' : 'temp-negative';
            const windArrow = getWindArrow(spot.currentConditions.direction);

            // Current wave conditions
            let currentWaveClass = '';
            let currentWaveText = '-';
            if (spot.currentConditions.wave !== undefined) {
                if (spot.currentConditions.wave < 1.0) {
                    currentWaveClass = 'wave-small';
                } else if (spot.currentConditions.wave >= 1.0 && spot.currentConditions.wave < 2.0) {
                    currentWaveClass = 'wave-moderate';
                } else {
                    currentWaveClass = 'wave-large';
                }
                currentWaveText = `${spot.currentConditions.wave}`;
            }

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

        // Check if spot is favorited
        const isFavorited = isFavorite(spot.name);
        const favoriteClass = isFavorited ? 'favorited' : '';

        card.innerHTML = `
                <div class="drag-handle" draggable="true">𓃌</div>
                <div class="spot-header">
                    <div class="spot-title">
                        <div class="spot-name" onclick="window.location.href='/spot/${spot.wgId}'">${spot.name || 'Unknown Spot'}</div>
                    </div>
                    <div class="spot-meta">
                        <div class="country-tag-wrapper">
                            <div class="favorite-icon ${favoriteClass}" onclick="toggleFavorite('${spot.name}')" title="${isFavorited ? 'Remove from favorites' : 'Add to favorites'}">
                                <svg class="favorite-icon-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                                    <path d="M1.327,12.4,4.887,15,3.535,19.187A3.178,3.178,0,0,0,4.719,22.8a3.177,3.177,0,0,0,3.8-.019L12,20.219l3.482,2.559a3.227,3.227,0,0,0,4.983-3.591L19.113,15l3.56-2.6a3.227,3.227,0,0,0-1.9-5.832H16.4L15.073,2.432a3.227,3.227,0,0,0-6.146,0L7.6,6.568H3.231a3.227,3.227,0,0,0-1.9,5.832Z"/>
                                </svg>
                            </div>
                            <div class="country-tag">${t(spot.country.replace(/\s+/g, '')) || 'Unknown'}</div>
                        </div>
                        <div class="last-updated">${spot.lastUpdated || 'No data'}</div>
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
            `;

        return card;
    }

    let currentFilter = 'all';

    function updateSpotCounter(count) {
        const spotCounterNumber = document.getElementById('spotCounterNumber');
        if (spotCounterNumber) {
            spotCounterNumber.textContent = count;
        }
    }

    function filterSpots(data, countryFilter, searchQuery) {
        let filtered = countryFilter === 'all' ? data : data.filter(spot => spot.country === countryFilter);

        if (searchQuery && searchQuery.trim() !== '') {
            const query = searchQuery.toLowerCase().trim();
            filtered = filtered.filter(spot => {
                return spot.name.toLowerCase().includes(query) ||
                    (spot.country && spot.country.toLowerCase().includes(query));
            });
        }

        return filtered;
    }

    async function renderSpots(filter = 'all', searchQuery = '', skipDelay = false, forceRefresh = false) {
        currentFilter = filter;
        currentSearchQuery = searchQuery;
        const spotsGrid = document.getElementById('spotsGrid');

        // If we already have data and not forcing refresh, use cached data
        if (globalWeatherData.length > 0 && !forceRefresh) {
            const filteredSpots = filterSpots(globalWeatherData, filter, searchQuery);
            displaySpots(filteredSpots, spotsGrid, filter, searchQuery);
            return;
        }

        // Show loading message immediately
        showLoadingMessage();

        // Wait at least 2 seconds before showing any content (unless skipped)
        const startTime = Date.now();
        const minDelay = skipDelay ? 0 : 2000;

        try {
            const data = await fetchWeatherData();
            const elapsed = Date.now() - startTime;
            const remainingDelay = minDelay - elapsed;

            if (remainingDelay > 0) {
                await new Promise(resolve => setTimeout(resolve, remainingDelay));
            }

            globalWeatherData = data;

            // Populate country dropdown with available countries
            populateCountryDropdown(data);

            const filteredSpots = filterSpots(data, filter, searchQuery);
            displaySpots(filteredSpots, spotsGrid, filter, searchQuery);
        } catch (error) {
            console.error('Failed to load weather:', error.message);
            showErrorMessage(error);
        }
    }

    function displaySpots(filteredSpots, spotsGrid, filter, searchQuery) {
        spotsGrid.innerHTML = '';
        if (filteredSpots.length === 0) {
            updateSpotCounter(0);
            const message = searchQuery ?
                `${t('errorNoSpotsSearchDescription')} "${searchQuery}"` :
                `${t('errorNoSpotsDescription')}`;
            spotsGrid.innerHTML = `
                    <div class="error-message">
                        <span class="error-icon">🔍</span>
                        <div class="error-title">${t('errorNoSpotsTitle')}</div>
                        <div class="error-description">
                            ${message}<br/>
                            ${t('errorTryAdjusting')}
                        </div>
                    </div>
                `;
        } else {
            // Check if all spots have empty forecasts
            const allForecastsEmpty = filteredSpots.every(spot =>
                !spot.forecast || spot.forecast.length === 0
            );

            if (allForecastsEmpty) {
                spotsGrid.innerHTML = `
                    <div class="loading-message">
                        <div class="loading-spinner"></div>
                        <span class="loading-text">${t('loadingText')}</span>
                    </div>
                `;
                // Retry loading after 5 seconds
                setTimeout(() => {
                    renderSpots(filter, searchQuery, false, true);
                }, 5000);
            } else {
                updateSpotCounter(filteredSpots.length);
                filteredSpots.forEach(spot => {
                    spotsGrid.appendChild(createSpotCard(spot));
                });
                loadCardOrder();
            }
        }
    }

    function setupDropdownEvents() {
        const dropdownOptions = document.querySelectorAll('.dropdown-option');
        const searchInput = document.getElementById('searchInput');

        dropdownOptions.forEach(option => {
            option.addEventListener('click', (e) => {
                e.stopPropagation();

                dropdownOptions.forEach(opt => opt.classList.remove('selected'));
                option.classList.add('selected');

                const country = option.dataset.country;
                updateSelectedCountryLabel(country);

                // Save selected country to localStorage
                localStorage.setItem('selectedCountry', country);

                // Deselect favorites if changing country
                if (showingFavorites) {
                    showingFavorites = false;
                    localStorage.setItem('showingFavorites', 'false');
                    document.getElementById('favoritesToggle').classList.remove('active');
                }

                renderSpots(country, searchInput.value, true);
                closeDropdown();

                // Scroll to top smoothly after country selection
                window.scrollTo({
                    top: 0,
                    behavior: 'smooth'
                });
            });
        });
    }

    function setupDropdown() {
        const dropdownButton = document.getElementById('dropdownButton');
        const dropdownMenu = document.getElementById('dropdownMenu');

        function closeDropdown() {
            dropdownButton.classList.remove('open', 'active');
            dropdownMenu.classList.remove('open');
        }

        dropdownButton.addEventListener('click', (e) => {
            e.stopPropagation();
            if (dropdownMenu.classList.contains('open')) {
                closeDropdown();
            } else {
                dropdownButton.classList.add('open', 'active');
                dropdownMenu.classList.add('open');
            }
        });

        document.addEventListener('click', (e) => {
            if (!dropdownButton.contains(e.target) && !dropdownMenu.contains(e.target)) {
                closeDropdown();
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && dropdownMenu.classList.contains('open')) {
                closeDropdown();
                dropdownButton.focus();
            }
        });

        // Make closeDropdown available globally
        window.closeDropdown = closeDropdown;
    }

    function setupModals() {
        const aiModal = document.getElementById('aiModal');
        const aiCloseButton = document.getElementById('modalClose');
        const appInfoModal = document.getElementById('appInfoModal');
        const appInfoCloseButton = document.getElementById('appInfoModalClose');
        const infoModal = document.getElementById('infoModal');
        const infoCloseButton = document.getElementById('infoModalClose');
        const icmModal = document.getElementById('icmModal');
        const icmCloseButton = document.getElementById('icmModalClose');

        // AI Modal events
        aiCloseButton.addEventListener('click', closeAIModal);
        aiModal.addEventListener('click', (e) => {
            if (e.target === aiModal) {
                closeAIModal();
            }
        });

        // App Info Modal events
        if (appInfoCloseButton) {
            appInfoCloseButton.addEventListener('click', closeAppInfoModal);
        }
        if (appInfoModal) {
            appInfoModal.addEventListener('click', (e) => {
                if (e.target === appInfoModal) {
                    closeAppInfoModal();
                }
            });
        }

        // Info Modal events
        infoCloseButton.addEventListener('click', closeInfoModal);
        infoModal.addEventListener('click', (e) => {
            if (e.target === infoModal) {
                closeInfoModal();
            }
        });

        // ICM Modal events
        icmCloseButton.addEventListener('click', closeIcmModal);
        icmModal.addEventListener('click', (e) => {
            if (e.target === icmModal) {
                closeIcmModal();
            }
        });

        // Escape key for all modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                if (aiModal.classList.contains('active')) {
                    closeAIModal();
                }
                if (appInfoModal && appInfoModal.classList.contains('active')) {
                    closeAppInfoModal();
                }
                if (infoModal.classList.contains('active')) {
                    closeInfoModal();
                }
                if (icmModal.classList.contains('active')) {
                    closeIcmModal();
                }
            }
        });
    }

    function setupSearch() {
        const searchInput = document.getElementById('searchInput');
        const searchClear = document.getElementById('searchClear');
        let searchTimeout;

        searchInput.addEventListener('input', (e) => {
            const value = e.target.value;

            // Show/hide clear button
            if (value.trim() !== '') {
                searchClear.classList.add('visible');
            } else {
                searchClear.classList.remove('visible');
            }

            // Deselect favorites if searching
            if (showingFavorites && value.trim() !== '') {
                showingFavorites = false;
                document.getElementById('favoritesToggle').classList.remove('active');
            }

            // Clear existing timeout
            if (searchTimeout) {
                clearTimeout(searchTimeout);
            }

            // Add delay before triggering search
            searchTimeout = setTimeout(() => {
                renderSpots(currentFilter, value, true);
                window.scrollTo(0, 0);
            }, 300);
        });

        searchClear.addEventListener('click', () => {
            searchInput.value = '';
            searchClear.classList.remove('visible');
            renderSpots(currentFilter, '');
            window.scrollTo(0, 0);
            searchInput.focus();
        });

        // Clear search on Escape key
        searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && searchInput.value !== '') {
                searchInput.value = '';
                searchClear.classList.remove('visible');
                renderSpots(currentFilter, '');
            }
        });
    }

    // Drag and drop functionality with persistence
    function setupDragAndDrop() {
        const spotsGrid = document.getElementById('spotsGrid');
        let draggedCard = null;
        let dragGhost = null;

        spotsGrid.addEventListener('dragstart', (e) => {
            const handle = e.target.closest('.drag-handle');
            if (!handle) {
                e.preventDefault();
                return;
            }

            draggedCard = e.target.closest('.spot-card');
            if (draggedCard) {
                draggedCard.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';

                // Create a visual clone of the dragged card
                dragGhost = draggedCard.cloneNode(true);
                dragGhost.classList.add('drag-ghost');
                dragGhost.classList.remove('dragging');
                dragGhost.style.width = draggedCard.offsetWidth + 'px';
                dragGhost.style.position = 'fixed';
                dragGhost.style.left = '-9999px';
                dragGhost.style.top = '-9999px';
                document.body.appendChild(dragGhost);

                // Set the drag image to the clone
                e.dataTransfer.setDragImage(dragGhost, e.offsetX, e.offsetY);
            }
        });

        spotsGrid.addEventListener('dragend', (e) => {
            if (draggedCard) {
                draggedCard.classList.remove('dragging');
                draggedCard = null;
                saveCardOrder();
            }

            // Remove the ghost element
            if (dragGhost) {
                dragGhost.remove();
                dragGhost = null;
            }
        });

        spotsGrid.addEventListener('dragover', (e) => {
            e.preventDefault();
            const afterElement = getDragAfterElement(spotsGrid, e.clientX, e.clientY);
            if (draggedCard && afterElement == null) {
                spotsGrid.appendChild(draggedCard);
            } else if (draggedCard && afterElement) {
                spotsGrid.insertBefore(draggedCard, afterElement);
            }
        });

        function getDragAfterElement(container, x, y) {
            const draggableElements = [...container.querySelectorAll('.spot-card:not(.dragging)')];

            let closestElement = null;
            let closestOffset = Number.NEGATIVE_INFINITY;

            draggableElements.forEach(child => {
                const box = child.getBoundingClientRect();
                const centerY = box.top + box.height / 2;

                // Calculate vertical and horizontal offsets
                const offsetY = y - centerY;
                const offsetX = x - (box.left + box.width / 2);

                // We want elements that are AFTER the cursor position
                // This means elements that are either:
                // 1. Below the cursor (offsetY < 0)
                // 2. To the right of cursor in same row (offsetX < 0 and on same row)

                if (offsetY < 0) {
                    // Element is below cursor - prioritize by vertical distance
                    const offset = offsetY;
                    if (offset > closestOffset) {
                        closestOffset = offset;
                        closestElement = child;
                    }
                } else if (offsetX < 0 && offsetY < box.height / 2) {
                    // Element is to the right and roughly in same row
                    // Use a combined offset that prioritizes horizontal over vertical
                    const offset = offsetX / 2 + offsetY;
                    if (offset > closestOffset) {
                        closestOffset = offset;
                        closestElement = child;
                    }
                }
            });

            return closestElement;
        }
    }

    function saveCardOrder() {
        const spotsGrid = document.getElementById('spotsGrid');
        const cards = spotsGrid.querySelectorAll('.spot-card');
        const order = Array.from(cards).map(card => {
            return card.querySelector('.spot-name').textContent;
        });

        const isThreeColumns = spotsGrid.classList.contains('three-columns');
        const columnMode = isThreeColumns ? '3col' : '2col';
        const orderKey = `spotOrder_${columnMode}_${currentFilter}_${currentSearchQuery}`;
        localStorage.setItem(orderKey, JSON.stringify(order));
    }

    function loadCardOrder() {
        const spotsGrid = document.getElementById('spotsGrid');
        const isThreeColumns = spotsGrid.classList.contains('three-columns');
        const columnMode = isThreeColumns ? '3col' : '2col';
        const orderKey = `spotOrder_${columnMode}_${currentFilter}_${currentSearchQuery}`;
        const savedOrder = localStorage.getItem(orderKey);

        if (!savedOrder) return;

        try {
            const order = JSON.parse(savedOrder);
            const cards = Array.from(spotsGrid.querySelectorAll('.spot-card'));

            order.forEach(spotName => {
                const card = cards.find(c => c.querySelector('.spot-name').textContent === spotName);
                if (card) {
                    spotsGrid.appendChild(card);
                }
            });
        } catch (e) {
            console.error('Failed to load card order:', e);
        }
    }

    // Favorites rendering
    async function renderFavorites() {
        showingFavorites = true;
        const favoritesButton = document.getElementById('favoritesToggle');
        favoritesButton.classList.add('active');

        const spotsGrid = document.getElementById('spotsGrid');
        const favorites = getFavorites();

        if (favorites.length === 0) {
            updateSpotCounter(0);
            spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⭐</span>
                    <div class="error-title">${t('noFavoritesTitle')}</div>
                    <div class="error-description">
                        ${t('noFavoritesDescription')}
                    </div>
                </div>
            `;
            return;
        }

        try {
            if (globalWeatherData.length === 0) {
                globalWeatherData = await fetchWeatherData();
            }

            const favoriteSpots = globalWeatherData.filter(spot => favorites.includes(spot.name));

            updateSpotCounter(favoriteSpots.length);
            spotsGrid.innerHTML = '';
            favoriteSpots.forEach(spot => {
                spotsGrid.appendChild(createSpotCard(spot));
            });
            loadCardOrder();
        } catch (error) {
            console.error('Failed to load favorites:', error.message);
            showErrorMessage(error);
        }
    }

    function setupFavorites() {
        const favoritesButton = document.getElementById('favoritesToggle');

        favoritesButton.addEventListener('click', () => {
            if (showingFavorites) {
                // Exit favorites mode
                showingFavorites = false;
                localStorage.setItem('showingFavorites', 'false');
                favoritesButton.classList.remove('active');
                const savedCountry = localStorage.getItem('selectedCountry') || 'all';
                renderSpots(savedCountry, '');
            } else {
                // Enter favorites mode
                localStorage.setItem('showingFavorites', 'true');
                renderFavorites();
            }

            // Scroll to top after toggling favorites
            window.scrollTo({
                top: 0,
                behavior: 'smooth'
            });
        });

        // Restore favorites state on page load
        const savedFavoritesState = localStorage.getItem('showingFavorites');
        if (savedFavoritesState === 'true') {
            renderFavorites();
        }
    }

    // Hamburger menu functionality
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

    // Kite Size Calculator Functionality
    function calculateKiteSize(windSpeed, riderWeight, skillLevel) {
        // Base calculation: kite size (m²) = rider weight (kg) * factor / wind speed (kts)
        let factor = 2.5; // Default factor for intermediate flat water

        // Adjust factor based on skill level and conditions
        const factorAdjustments = {
            'beginner-flat': 3.0,         // Beginners need larger kites
            'beginner-small': 2.8,        // Slightly smaller for small waves
            'intermediate-flat': 2.5,     // Standard factor
            'intermediate-medium': 2.3,   // Medium waves need smaller kites
            'advanced-flat': 2.2,         // Advanced riders can use smaller kites
            'advanced-medium': 2.0,       // Advanced with medium waves
            'advanced-large': 1.8         // Advanced with large waves need smallest kites
        };

        if (skillLevel && factorAdjustments[skillLevel]) {
            factor = factorAdjustments[skillLevel];
        }

        // Calculate base kite size
        let kiteSize = (riderWeight * factor) / windSpeed;

        // Round to nearest common kite size (7, 9, 10, 12, 14, 15, 17 m²)
        const commonSizes = [7, 9, 10, 12, 14, 15, 17];
        kiteSize = commonSizes.reduce((prev, curr) =>
            Math.abs(curr - kiteSize) < Math.abs(prev - kiteSize) ? curr : prev
        );

        return kiteSize;
    }

    function calculateBoardSize(riderWeight, skillLevel, kiteSize) {
        // Determine board type first
        let boardType = 'Twin Tip';
        if (skillLevel.includes('large') || (skillLevel.includes('medium') && skillLevel.includes('advanced'))) {
            boardType = 'Surfboard/Directional 🏄';
        }

        if (boardType === 'Twin Tip') {
            // Twin Tip calculation with realistic sizes (125-160 cm)
            let baseSize = 125; // Minimum realistic size

            // Add size based on weight (up to 115 kg)
            const effectiveWeight = Math.min(riderWeight, 115);
            baseSize += (effectiveWeight - 50) * 0.5; // ~32.5 cm range for 50-115 kg

            // Adjust based on skill level
            const sizeAdjustments = {
                'beginner-flat': 1.15,          // Larger for stability
                'beginner-small': 1.10,
                'intermediate-flat': 1.0,       // Standard size
                'intermediate-medium': 0.98,
                'advanced-flat': 0.92,          // Smaller for maneuverability
                'advanced-medium': 0.90
            };

            const adjustment = sizeAdjustments[skillLevel] || 1.0;
            let boardSize = Math.round(baseSize * adjustment);

            // Round to nearest 2 cm
            boardSize = Math.round(boardSize / 2) * 2;

            // Apply minimum and maximum size constraints
            const minSize = 136; // Minimum board size
            let maxSize = 160; // Default for beginners
            if (skillLevel.includes('intermediate')) {
                maxSize = 142;
            } else if (skillLevel.includes('advanced')) {
                maxSize = 142;
            }
            boardSize = Math.max(minSize, Math.min(boardSize, maxSize));

            // Calculate width (typically 38-46 cm for twin tips)
            let width = 38 + Math.floor((boardSize - 136) / 4);
            width = Math.min(width, 46);

            return `${boardSize} x ${width} cm (${boardType})`;
        } else {
            // Surfboard/Directional sizes (typically 5'4" to 6'2")
            const sizeInFeet = riderWeight < 70 ? "5'6\"" : riderWeight < 85 ? "5'10\"" : "6'0\"";
            return `${sizeInFeet} (${boardType})`;
        }
    }

    function setupKiteSizeCalculator() {
        const kiteSizeButton = document.getElementById('kiteSizeToggle');
        const kiteSizeModal = document.getElementById('kiteSizeModal');
        const kiteSizeCloseButton = document.getElementById('kiteSizeModalClose');
        const calculateBtn = document.getElementById('calculateBtn');

        // Open modal
        kiteSizeButton.addEventListener('click', () => {
            kiteSizeModal.classList.add('active');
            document.body.style.overflow = 'hidden';
            // Reset result visibility
            document.getElementById('calcResult').classList.remove('show');
        });

        // Close modal
        function closeKiteSizeModal() {
            kiteSizeModal.classList.remove('active');
            document.body.style.overflow = 'auto';
        }

        kiteSizeCloseButton.addEventListener('click', closeKiteSizeModal);

        kiteSizeModal.addEventListener('click', (e) => {
            if (e.target === kiteSizeModal) {
                closeKiteSizeModal();
            }
        });

        // Calculate button
        calculateBtn.addEventListener('click', () => {
            const windSpeed = parseFloat(document.getElementById('windSpeed').value);
            const riderWeight = parseFloat(document.getElementById('riderWeight').value);
            const skillLevel = document.getElementById('skillLevel').value;
            const calcWarning = document.getElementById('calcWarning');
            const calcResult = document.getElementById('calcResult');

            // Hide previous warnings and results
            calcWarning.style.display = 'none';
            calcResult.classList.remove('show');

            // Validation
            if (!windSpeed || windSpeed < 5 || windSpeed > 50) {
                calcWarning.innerHTML = '⚠️ <strong>INVALID WIND SPEED</strong><br><br>Please enter a valid wind speed between 5 and 50 knots.';
                calcWarning.style.display = 'block';
                return;
            }

            if (!riderWeight || riderWeight > 150) {
                calcWarning.innerHTML = '⚠️ <strong>INVALID RIDER WEIGHT</strong><br><br>Please enter a valid rider weight between 40 and 150 kg.';
                calcWarning.style.display = 'block';
                return;
            }

            if (riderWeight < 40) {
                calcWarning.innerHTML = '⚠️ <strong>WEIGHT TOO LOW</strong><br><br>Your weight is too low and riding may be dangerous. Kitesurfing requires sufficient body weight for control and safety.';
                calcWarning.style.display = 'block';
                return;
            }

            if (!skillLevel) {
                calcWarning.innerHTML = '⚠️ <strong>SKILL LEVEL REQUIRED</strong><br><br>Please select your skill level and conditions.';
                calcWarning.style.display = 'block';
                return;
            }

            // Check for warning conditions
            if (riderWeight > 120) {
                calcWarning.innerHTML = '😉 Maybe you should lose some weight before going onto the water!';
                calcWarning.style.display = 'block';
                return;
            }

            if (windSpeed > 40) {
                calcWarning.innerHTML = '⚠️ <strong>EXTREME CONDITIONS!</strong><br><br>Wind speeds above 40 knots are extremely dangerous. We strongly recommend NOT going onto the water in these conditions. Stay safe!';
                calcWarning.style.display = 'block';
                return;
            }

            if (windSpeed < 12) {
                calcWarning.innerHTML = '💨 <strong>LOW WIND CONDITIONS</strong><br><br>There is not enough wind for riding with a regular setup. However, you could try going on a <strong>kite foil board</strong>, which works great in light wind conditions!';
                calcWarning.style.display = 'block';
                return;
            }

            // Check for low wind with medium or large waves
            if (windSpeed < 15 && (skillLevel.includes('medium') || skillLevel.includes('large'))) {
                calcWarning.innerHTML = '🌊 <strong>INSUFFICIENT WIND FOR WAVE CONDITIONS</strong><br><br>Wind speed below 15 knots is not enough for riding in medium or large waves. Waves create additional resistance and you need more wind power to maintain speed and control. We recommend NOT going onto the water in these conditions.';
                calcWarning.style.display = 'block';
                return;
            }

            // Calculate
            const kiteSize = calculateKiteSize(windSpeed, riderWeight, skillLevel);
            const boardSize = calculateBoardSize(riderWeight, skillLevel, kiteSize);

            // Display results
            document.getElementById('kiteSize').textContent = `${kiteSize} m²`;
            document.getElementById('boardSize').textContent = boardSize;
            calcResult.classList.add('show');
        });

        // Escape key support
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && kiteSizeModal.classList.contains('active')) {
                closeKiteSizeModal();
            }
        });
    }

    function setupColumnToggle() {
        const columnToggle = document.getElementById('columnToggle');
        const spotsGrid = document.getElementById('spotsGrid');
        const icon3Columns = document.getElementById('icon3Columns');
        const icon2Columns = document.getElementById('icon2Columns');

        // Detect if user is on mobile device
        const isMobile = window.innerWidth <= 768 || /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);

        const storedPreference = localStorage.getItem('threeColumns');
        let isThreeColumns = storedPreference === null ? true : storedPreference === 'true';

        // Force 2-column layout on mobile devices
        if (isMobile && isThreeColumns) {
            isThreeColumns = false;
            localStorage.setItem('threeColumns', 'false');
        }

        function updateIcons(isThreeColumns) {
            if (isThreeColumns) {
                // Show 3-column icon, hide 2-column icon
                icon3Columns.style.display = 'block';
                icon2Columns.style.display = 'none';
            } else {
                // Show 2-column icon, hide 3-column icon
                icon3Columns.style.display = 'none';
                icon2Columns.style.display = 'block';
            }
        }

        if (isThreeColumns) {
            spotsGrid.classList.add('three-columns');
        } else {
            spotsGrid.classList.remove('three-columns');
        }

        updateIcons(isThreeColumns);

        if (storedPreference === null) {
            localStorage.setItem('threeColumns', String(isThreeColumns));
        }

        columnToggle.addEventListener('click', () => {
            spotsGrid.classList.toggle('three-columns');

            // Save preference
            const isThreeColumns = spotsGrid.classList.contains('three-columns');
            localStorage.setItem('threeColumns', String(isThreeColumns));
            updateIcons(isThreeColumns);
        });
    }

    // Auto-refresh functionality
    async function refreshDataInBackground() {
        try {
            // Fetch new data silently
            const freshData = await fetchWeatherData();

            // Update global data
            globalWeatherData = freshData;

            // Update country dropdown without changing selection
            populateCountryDropdown(freshData);

            // Silently update the current view
            if (showingFavorites) {
                // Update favorites without re-rendering (to avoid disruption)
                const spotsGrid = document.getElementById('spotsGrid');
                const favorites = getFavorites();
                const favoriteSpots = globalWeatherData.filter(spot => favorites.includes(spot.name));

                // Update existing cards instead of recreating them
                favoriteSpots.forEach(updatedSpot => {
                    const existingCard = Array.from(spotsGrid.querySelectorAll('.spot-card')).find(card => {
                        const spotName = card.querySelector('.spot-name');
                        return spotName && spotName.textContent === updatedSpot.name;
                    });

                    if (existingCard) {
                        const newCard = createSpotCard(updatedSpot);
                        existingCard.replaceWith(newCard);
                    }
                });
            } else {
                // Update regular filtered view
                const filteredSpots = filterSpots(globalWeatherData, currentFilter, currentSearchQuery);
                const spotsGrid = document.getElementById('spotsGrid');

                // Update existing cards instead of recreating them
                filteredSpots.forEach(updatedSpot => {
                    const existingCard = Array.from(spotsGrid.querySelectorAll('.spot-card')).find(card => {
                        const spotName = card.querySelector('.spot-name');
                        return spotName && spotName.textContent === updatedSpot.name;
                    });

                    if (existingCard) {
                        const newCard = createSpotCard(updatedSpot);
                        existingCard.replaceWith(newCard);
                    }
                });

                // Update counter
                updateSpotCounter(filteredSpots.length);
            }

            console.log('Data refreshed in background at', new Date().toLocaleTimeString());
        } catch (error) {
            console.error('Background refresh failed:', error);
            // Silently fail - don't disturb the user
        }
    }

    function startAutoRefresh() {
        // Clear any existing interval
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
        }

        // Start new interval
        autoRefreshInterval = setInterval(() => {
            refreshDataInBackground();
        }, AUTO_REFRESH_INTERVAL);

        console.log('Auto-refresh started: updating every', AUTO_REFRESH_INTERVAL / 1000, 'seconds');
    }

    function stopAutoRefresh() {
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
            console.log('Auto-refresh stopped');
        }
    }

    // Make functions global for onclick handlers
    window.openAppInfoModal = openAppInfoModal;
    window.closeAppInfoModal = closeAppInfoModal;
    window.openAIModal = openAIModal;
    window.closeAIModal = closeAIModal;
    window.openInfoModal = openInfoModal;
    window.closeInfoModal = closeInfoModal;
    window.openIcmModal = openIcmModal;
    window.closeIcmModal = closeIcmModal;
    window.toggleFavorite = toggleFavorite;

    // Sponsors functionality
    async function fetchMainSponsors() {
        try {
            const response = await fetch('/api/v1/sponsors/main');
            if (!response.ok) {
                return [];
            }
            const sponsors = await response.json();
            return sponsors || [];
        } catch (error) {
            console.error('Error fetching main sponsors:', error);
            return [];
        }
    }

    function checkImageExists(url) {
        return new Promise((resolve) => {
            const img = new Image();
            img.onload = () => resolve(true);
            img.onerror = () => resolve(false);
            img.src = url;
        });
    }

    async function renderMainSponsors() {
        const sponsorsContainer = document.getElementById('sponsorsContainer');
        if (!sponsorsContainer) {
            return;
        }

        const sponsors = await fetchMainSponsors();

        if (!sponsors || sponsors.length === 0) {
            sponsorsContainer.innerHTML = '';
            return;
        }

        const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';

        let sponsorsHTML = '<div class="sponsors-container"><div class="sponsors-list">';

        for (const sponsor of sponsors) {
            // Determine which logo to use based on theme
            const logoToUse = currentTheme === 'light' ? sponsor.logoLight : sponsor.logoDark;
            const logoPath = `/img/sponsors/${logoToUse}`;
            const imageExists = await checkImageExists(logoPath);

            if (imageExists) {
                sponsorsHTML += `
                    <div class="sponsor-item">
                        <a href="${sponsor.link}" target="_blank" rel="noopener noreferrer" class="sponsor-link">
                            <img src="${logoPath}" alt="${sponsor.name}" class="sponsor-logo" data-logo-dark="${sponsor.logoDark}" data-logo-light="${sponsor.logoLight}">
                        </a>
                    </div>
                `;
            } else {
                sponsorsHTML += `
                    <div class="sponsor-item">
                        <a href="${sponsor.link}" target="_blank" rel="noopener noreferrer" class="sponsor-link">
                            <span class="sponsor-name">${sponsor.name}</span>
                        </a>
                    </div>
                `;
            }
        }

        sponsorsHTML += '</div></div>';
        sponsorsContainer.innerHTML = sponsorsHTML;
    }

    function updateSponsorLogosForTheme(theme) {
        const sponsorLogos = document.querySelectorAll('.sponsor-logo');
        sponsorLogos.forEach(logo => {
            const logoDark = logo.getAttribute('data-logo-dark');
            const logoLight = logo.getAttribute('data-logo-light');
            const logoToUse = theme === 'light' ? logoLight : logoDark;
            logo.src = `/img/sponsors/${logoToUse}`;
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        initTheme();
        initLanguage();
        setupDropdown();
        setupModals();
        setupSearch();
        setupDragAndDrop();
        setupFavorites();
        setupHamburgerMenu();
        setupKiteSizeCalculator();
        setupColumnToggle();

        const infoToggle = document.getElementById('infoToggle');
        if (infoToggle) {
            infoToggle.addEventListener('click', () => {
                openAppInfoModal();
            });
        }

        // Load main sponsors
        renderMainSponsors();

        // Check if we should show favorites first, before loading spots
        const savedFavoritesState = localStorage.getItem('showingFavorites');
        if (savedFavoritesState === 'true') {
            // Favorites will be rendered by setupFavorites()
            showingFavorites = true;
        } else {
            // Load saved country filter or default to 'all'
            const savedCountry = localStorage.getItem('selectedCountry') || 'all';
            renderSpots(savedCountry);
        }

        // Start auto-refresh after initial load
        startAutoRefresh();
    });
