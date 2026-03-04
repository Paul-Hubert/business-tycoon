/**
 * app.js — Main entry point for the Trade Empire SPA.
 * Bootstraps on load, initializes components, starts polling.
 */

var _statePollInterval = null;

$(document).ready(function() {
    // Check auth
    var token = localStorage.getItem('token');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    // Initialize components
    initSidebar();
    initTabs();

    // Load config first, then player state
    loadGameConfig().then(function() {
        loadPlayerState();
        // Poll for updates every 2 seconds
        _statePollInterval = setInterval(loadPlayerState, 2000);
    });
});

// ── Tab Management ───────────────────────────────────────────────────────

function initTabs() {
    $('#tab-bar').on('click', '.tab-btn', function() {
        var tab = $(this).data('tab');
        switchTab(tab);
    });
}

function switchTab(tabName) {
    // Stop chat polling when leaving chat tab
    if (State.activeTab === 'chat' && tabName !== 'chat') {
        stopChatPolling();
    }

    State.activeTab = tabName;

    // Update tab buttons
    $('.tab-btn').removeClass('active');
    $('.tab-btn[data-tab="' + tabName + '"]').addClass('active');

    // Show/hide panels
    $('.tab-panel').addClass('hidden');
    $('#tab-' + tabName).removeClass('hidden');

    // Refresh tab content
    refreshActiveTab();
}

function refreshActiveTab() {
    switch (State.activeTab) {
        case 'production': renderProductionTab(); break;
        case 'market':     renderMarketTab();     break;
        case 'shop':       renderShopTab();       break;
        case 'chat':       renderChatTab();       break;
    }
}

// ── Data Loading ─────────────────────────────────────────────────────────

async function loadGameConfig() {
    try {
        var data = await API.get('/api/v1/config');
        State.config = data;
        State.resources = data.resources || [];
        State.recipes = data.recipes || {};
        State.buildCosts = data.facility_build_costs || {};
        State.opCosts = data.facility_operating_costs || {};
    } catch (err) {
        console.error('Failed to load config:', err);
    }
}

async function loadPlayerState() {
    try {
        var data = await API.get('/api/v1/state');
        if (!data) return; // Auth redirect

        State.player = {
            playerId: data.playerId,
            username: data.username,
            cash: data.cash,
            netWorth: data.net_worth
        };
        State.inventory = data.inventory || {};
        State.facilities = data.facilities || [];
        State.currentTick = data.current_tick || 0;

        updateHeader();
        updateSidebarStocks();
        refreshActiveTab();
    } catch (err) {
        console.error('Failed to load state:', err);
    }
}

// ── Header Updates ───────────────────────────────────────────────────────

function updateHeader() {
    if (!State.player) return;
    $('#header-cash').text(formatCash(State.player.cash));
    $('#header-net-worth').text(formatCash(State.player.netWorth));
    $('#header-player').text(State.player.username);
    $('#header-tick').text('Tick ' + State.currentTick);
}

// ── Toast Notifications ──────────────────────────────────────────────────

function showToast(message, type) {
    type = type || 'info';
    var toast = $('<div class="toast toast-' + type + '">' + escapeHtml(message) + '</div>');
    $('body').append(toast);
    setTimeout(function() {
        toast.fadeOut(300, function() { toast.remove(); });
    }, 3000);
}
