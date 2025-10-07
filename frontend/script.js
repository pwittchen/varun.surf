    let globalWeatherData = [];
    let availableCountries = new Set();
    let currentSearchQuery = '';
    let showingFavorites = false;

    // Configuration
    const API_ENDPOINT = '/api/v1/spots';

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
                themeIcon.textContent = '🌞';
            } else {
                themeIcon.textContent = '🌙';
            }
            localStorage.setItem('theme', theme);
        }

        // Set initial theme
        updateTheme(savedTheme);

        // Theme toggle event
        themeToggle.addEventListener('click', () => {
            const currentTheme = document.documentElement.getAttribute('data-theme');
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            updateTheme(newTheme);
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
                    <span class="loading-text">Loading weather data...</span>
                </div>
            `;
    }

    function showErrorMessage(error) {
        const spotsGrid = document.getElementById('spotsGrid');

        let errorTitle = 'Failed to Load Weather Data';
        let errorMessage = 'Unable to fetch the latest weather information from the server.';

        if (error.message.includes('HTTP Error: 404')) {
            errorTitle = 'Data Source Not Found';
            errorMessage = 'The weather data endpoint is not available. Please check if the server is running.';
        } else if (error.message.includes('HTTP Error: 500')) {
            errorTitle = 'Server Error';
            errorMessage = 'The weather service is experiencing technical difficulties. Please try again later.';
        } else if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
            errorTitle = 'Connection Error';
            errorMessage = 'Unable to connect to the weather service. Please check your internet connection and ensure the server is running.';
        } else if (error.message.includes('JSON') || error.message.includes('Invalid data format')) {
            errorTitle = 'Data Format Error';
            errorMessage = 'The weather data received is corrupted or in an unexpected format.';
        }

        spotsGrid.innerHTML = `
                <div class="error-message">
                    <span class="error-icon">⚠️</span>
                    <div class="error-title">${errorTitle}</div>
                    <div class="error-description">
                        ${errorMessage}<br/>
                        Please refresh the page to try again.
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
        let dropdownHTML = `<div class="dropdown-option ${savedCountry === 'all' ? 'selected' : ''}" data-country="all">🌎 All</div>`;

        sortedCountries.forEach(country => {
            const countryFlag = getCountryFlag(country);
            const isSelected = savedCountry === country ? 'selected' : '';
            dropdownHTML += `<div class="dropdown-option ${isSelected}" data-country="${country}">${countryFlag} ${country.toUpperCase()}</div>`;
        });

        dropdownMenu.innerHTML = dropdownHTML;

        // Update the selected country text in the button
        if (savedCountry !== 'all') {
            const countryFlag = getCountryFlag(savedCountry);
            selectedCountry.textContent = `${countryFlag} ${savedCountry.toUpperCase()}`;
        } else {
            selectedCountry.textContent = '🌎 All';
        }

        // Re-attach event listeners for the new dropdown options
        setupDropdownEvents();
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

    function createSpotCard(spot) {
        const card = document.createElement('div');
        card.className = 'spot-card';
        card.dataset.country = spot.country;

        // Check if spot has wave data
        const hasWaveData = spot.forecast && spot.forecast.some(day => day.wave !== undefined) ||
            (spot.currentConditions && spot.currentConditions.wave !== undefined);

        let forecastRows = '';
        if (spot.forecast && Array.isArray(spot.forecast)) {
            spot.forecast.forEach(day => {
                let windClass, windTextClass;
                if (day.gusts < 12) {
                    windClass = 'weak-wind';
                    windTextClass = 'wind-weak';
                } else if (day.gusts >= 12 && day.gusts < 18) {
                    windClass = 'moderate-wind';
                    windTextClass = 'wind-moderate';
                } else if (day.gusts >= 18 && day.gusts <= 25) {
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
                        <tr class="${windClass}">
                            <td><strong>${day.date}</strong></td>
                            <td class="${windTextClass}">${day.wind} kts</td>
                            <td class="${windTextClass}">${day.gusts} kts</td>
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
            let windClass, windTextClass;
            if (spot.currentConditions.gusts < 12) {
                windClass = 'weak-wind';
                windTextClass = 'wind-weak';
            } else if (spot.currentConditions.gusts >= 12 && spot.currentConditions.gusts < 18) {
                windClass = 'moderate-wind';
                windTextClass = 'wind-moderate';
            } else if (spot.currentConditions.gusts >= 18 && spot.currentConditions.gusts <= 22) {
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
                    <tr class="${windClass}" style="border-top: 2px solid #404040;">
                        <td>
                            <div class="live-indicator">
                                <strong class="live-text">NOW</strong>
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

        // Check if spot is favorited
        const isFavorited = isFavorite(spot.name);
        const favoriteClass = isFavorited ? 'favorited' : '';

        card.innerHTML = `
                <div class="drag-handle" draggable="true">𓃌</div>
                <div class="spot-header">
                    <div class="spot-title">
                        <div class="spot-name">${spot.name || 'Unknown Spot'}</div>
                    </div>
                    <div class="spot-meta">
                        <div class="country-tag-wrapper">
                            <div class="favorite-icon ${favoriteClass}" onclick="toggleFavorite('${spot.name}')" title="${isFavorited ? 'Remove from favorites' : 'Add to favorites'}">★</div>
                            <div class="country-tag">${spot.country || 'Unknown'}</div>
                        </div>
                        <div class="last-updated">${spot.lastUpdated || 'No data'}</div>
                    </div>
                </div>
                <div class="external-links">
                    ${spot.spotInfo ? `<span class="external-link info-link" onclick="openInfoModal('${spot.name}')"></span>` : ''}
                    ${spot.windguruUrl ? `<a href="${spot.windguruUrl}" target="_blank" class="external-link">WG</a>` : ''}
                    ${spot.windfinderUrl ? `<a href="${spot.windfinderUrl}" target="_blank" class="external-link">WF</a>` : ''}
                    ${spot.icmUrl ? `<a href="${spot.icmUrl}" target="_blank" class="external-link">ICM</a>` : ''}
                    ${spot.webcamUrl ? `<a href="${spot.webcamUrl}" target="_blank" class="external-link webcam-link">CAM</a>` : ''}
                    ${spot.locationUrl ? `<a href="${spot.locationUrl}" target="_blank" class="external-link location-link">MAP</a>` : ''}
                    ${spot.aiAnalysis ? `<span class="external-link ai-link" onclick="openAIModal('${spot.name}')">AI</span>` : ''}
                </div>
                <table class="weather-table">
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Wind</th>
                            <th>Gusts</th>
                            <th>Direction</th>
                            <th>Temp</th>
                            <th>Rain</th>
                            ${hasWaveData ? '<th>Wave</th>' : ''}
                        </tr>
                    </thead>
                    <tbody>
                        ${forecastRows}
                        ${currentConditionsRow}
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
                `No spots found matching "${searchQuery}"` :
                'No kitesurfing spots found for the selected filter.';
            spotsGrid.innerHTML = `
                    <div class="error-message">
                        <span class="error-icon">🔍</span>
                        <div class="error-title">No Spots Found</div>
                        <div class="error-description">
                            ${message}<br/>
                            Try adjusting your search or selecting "All" countries.
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
                        <span class="loading-text">Loading forecasts...</span>
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
        const selectedCountry = document.getElementById('selectedCountry');
        const searchInput = document.getElementById('searchInput');

        dropdownOptions.forEach(option => {
            option.addEventListener('click', (e) => {
                e.stopPropagation();

                dropdownOptions.forEach(opt => opt.classList.remove('selected'));
                option.classList.add('selected');

                selectedCountry.textContent = option.textContent;
                const country = option.dataset.country;

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
        const infoModal = document.getElementById('infoModal');
        const infoCloseButton = document.getElementById('infoModalClose');

        // AI Modal events
        aiCloseButton.addEventListener('click', closeAIModal);
        aiModal.addEventListener('click', (e) => {
            if (e.target === aiModal) {
                closeAIModal();
            }
        });

        // Info Modal events
        infoCloseButton.addEventListener('click', closeInfoModal);
        infoModal.addEventListener('click', (e) => {
            if (e.target === infoModal) {
                closeInfoModal();
            }
        });

        // Escape key for both modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                if (aiModal.classList.contains('active')) {
                    closeAIModal();
                }
                if (infoModal.classList.contains('active')) {
                    closeInfoModal();
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
                    <div class="error-title">No Favorites Yet</div>
                    <div class="error-description">
                        Click the star icon on any spot card to add it to your favorites.
                    </div>
                </div>
            `;
            return;
        }

        try {
            if (globalWeatherData.length === 0) {
                const data = await fetchWeatherData();
                globalWeatherData = data;
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

        // Load saved column preference (default is 2 columns)
        const isThreeColumns = localStorage.getItem('threeColumns') === 'true';
        if (isThreeColumns) {
            spotsGrid.classList.add('three-columns');
            columnToggle.classList.add('active');
        }

        columnToggle.addEventListener('click', () => {
            spotsGrid.classList.toggle('three-columns');
            columnToggle.classList.toggle('active');

            // Save preference
            const isThreeColumns = spotsGrid.classList.contains('three-columns');
            localStorage.setItem('threeColumns', isThreeColumns);
        });
    }

    // Make functions global for onclick handlers
    window.openAIModal = openAIModal;
    window.closeAIModal = closeAIModal;
    window.openInfoModal = openInfoModal;
    window.closeInfoModal = closeInfoModal;
    window.toggleFavorite = toggleFavorite;

    document.addEventListener('DOMContentLoaded', () => {
        initTheme();
        setupDropdown();
        setupModals();
        setupSearch();
        setupDragAndDrop();
        setupFavorites();
        setupHamburgerMenu();
        setupKiteSizeCalculator();
        setupColumnToggle();

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
    });
