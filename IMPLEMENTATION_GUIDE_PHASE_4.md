# Trade Empire — Phase 4 Implementation Guide

> **UI & Social**: Tab-Based UI, Sidebar, Production Tab, Player Chat

---

## Overview

Phase 4 makes the game playable for humans. The prior three phases built an economy that runs correctly — Phase 4 makes it visible, navigable, and social.

**Estimated difficulty:** Medium (UI architecture decisions have big downstream impact)
**Estimated duration:** 2–3 weeks
**Tech stack:** HTML, CSS, jQuery 3.6 (already bundled), custom CSS design system

**Critical order:**

```
21.2 (Sidebar) → 21.1 (Tab Layout) → 21.3 (Production Tab)
                                    ↓
                              20.1 (Chat Tab)
```

The sidebar drives state for every tab (selected resource). Tabs contain tab-specific logic. Implement the skeleton first, then fill each tab.

---

## Core Architecture Decision

### Single-Page App (SPA) Pattern

The UI is a **single HTML page** with one JS file that:
1. Bootstraps on load (fetches player state)
2. Listens for user interactions (clicks, form submits)
3. Makes REST API calls for all data
4. Updates the DOM reactively (no page reloads)

**Do NOT use:**
- Server-side rendering (JSP templates for game state)
- Direct DB queries from the frontend
- Page reloads to refresh data

**Why:** Both AI agents and humans use the same API. If the UI bypasses the API, you've created a human-only shortcut that breaks the parity guarantee from Phase 1.

### State Management

Keep a single in-memory state object in JavaScript:

```javascript
const State = {
    player: null,        // { playerId, username, cash, netWorth }
    inventory: {},       // { wheat: 120, iron: 45, ... }
    facilities: [],      // [{ id, resourceName, state, capacity }, ...]
    marketOrders: [],    // Player's open orders
    selectedResource: null,  // Currently selected in sidebar
    activeTab: 'production', // 'production' | 'market' | 'chat'
    priceCache: {},      // { wheat: { buy: 1.80, sell: 2.00 }, ... }
    messages: []         // Chat messages
};
```

This is the single source of truth. Every DOM update reads from `State`, never from the DOM itself.

---

## 21.2 Sidebar Resource Navigation

### What It Does

A collapsible left sidebar listing all 32 resources organized by tier. Clicking a resource sets `State.selectedResource` and refreshes the active tab to show data for that resource.

### Implementation

#### 1. `sidebar.js`

```javascript
const RESOURCES = {
    raw: ['wheat','iron','copper','gold','petrol','cotton','timber','lithium','rubber','silicon','bauxite','coal'],
    intermediate: ['bread','steel','plastic','circuit','fabric','lumber','glass','aluminium','battery','rubber_compound','canned_food'],
    consumer: ['car','phone','clothing','furniture','laptop','bicycle','jewelry']
};

const RESOURCE_LABELS = {
    wheat: 'Wheat', iron: 'Iron', copper: 'Copper', gold: 'Gold',
    petrol: 'Petrol', cotton: 'Cotton', timber: 'Timber', lithium: 'Lithium',
    rubber: 'Rubber', silicon: 'Silicon', bauxite: 'Bauxite', coal: 'Coal',
    bread: 'Bread', steel: 'Steel', plastic: 'Plastic', circuit: 'Circuit',
    fabric: 'Fabric', lumber: 'Lumber', glass: 'Glass', aluminium: 'Aluminium',
    battery: 'Battery', rubber_compound: 'Rubber Comp.', canned_food: 'Canned Food',
    car: 'Car', phone: 'Phone', clothing: 'Clothing', furniture: 'Furniture',
    laptop: 'Laptop', bicycle: 'Bicycle', jewelry: 'Jewelry'
};

function initSidebar() {
    const sidebar = $('#sidebar-nav');
    sidebar.html('');

    Object.entries(RESOURCES).forEach(([tier, names]) => {
        const tierLabel = { raw: 'Raw Materials', intermediate: 'Intermediates', consumer: 'Consumer Goods' }[tier];

        sidebar.append(`
            <div class="sidebar-section">
                <div class="sidebar-tier-header" data-tier="${tier}">
                    <span class="tier-label">${tierLabel}</span>
                    <span class="collapse-icon">▾</span>
                </div>
                <ul class="resource-list" id="tier-${tier}">
                    ${names.map(name => `
                        <li class="resource-item" data-resource="${name}">
                            <span class="resource-name">${RESOURCE_LABELS[name]}</span>
                            <span class="resource-stock" id="stock-${name}">—</span>
                        </li>
                    `).join('')}
                </ul>
            </div>
        `);
    });

    // Click a resource to select it
    sidebar.on('click', '.resource-item', function() {
        const resource = $(this).data('resource');
        selectResource(resource);
    });

    // Toggle tier collapse
    sidebar.on('click', '.sidebar-tier-header', function() {
        const tier = $(this).data('tier');
        $(`#tier-${tier}`).toggleClass('collapsed');
        $(this).find('.collapse-icon').text(
            $(`#tier-${tier}`).hasClass('collapsed') ? '▸' : '▾'
        );
    });
}

function selectResource(resourceName) {
    State.selectedResource = resourceName;

    // Update sidebar highlight
    $('.resource-item').removeClass('selected');
    $(`.resource-item[data-resource="${resourceName}"]`).addClass('selected');

    // Refresh the active tab with the selected resource
    refreshActiveTab();
}

function updateSidebarStocks() {
    Object.keys(State.inventory).forEach(resource => {
        const qty = State.inventory[resource];
        $(`#stock-${resource}`).text(formatNumber(qty));
    });
}
```

### Pitfalls & Foresight

**Pitfall 1: Sidebar doesn't update in real-time**
- ❌ Bad: Sidebar stock values only update on page load.
- ✅ Good: `updateSidebarStocks()` is called every time `State.inventory` is updated (after each API poll).

**Pitfall 2: "All resources" view vs. selected resource view**
- Some tabs need to show data for the selected resource only (focused view).
- Others need to show all resources (global view).
- ✅ Convention: `State.selectedResource === null` means "show all". Each tab checks this.

**Foresight for Phase 2 (Production):**
- Stock numbers in the sidebar create urgency: player sees "Wheat: 2,340" decaying while Steel: 0.
- Update stock colors: green (plentiful), yellow (low), red (critically low).

---

## 21.1 Tab-Based UI Layout

### Implementation

#### 1. `index.html` skeleton

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Trade Empire</title>
    <link rel="stylesheet" href="css/main.css">
</head>
<body>

<!-- HEADER: always visible -->
<header id="game-header">
    <div class="header-logo">Trade Empire</div>
    <div class="header-cash">
        <span class="label">Cash</span>
        <span id="header-cash">$—</span>
    </div>
    <div class="header-player" id="header-player">—</div>
    <button id="btn-settings" class="btn-icon">⚙</button>
</header>

<!-- MAIN LAYOUT -->
<div id="app-layout">

    <!-- SIDEBAR -->
    <aside id="sidebar">
        <nav id="sidebar-nav"></nav>
    </aside>

    <!-- CONTENT AREA -->
    <main id="content-area">

        <!-- TAB BAR -->
        <div id="tab-bar">
            <button class="tab-btn active" data-tab="production">Production</button>
            <button class="tab-btn" data-tab="market">Market</button>
            <button class="tab-btn" data-tab="chat">Chat</button>
        </div>

        <!-- TAB PANELS -->
        <div id="tab-production" class="tab-panel"></div>
        <div id="tab-market" class="tab-panel hidden"></div>
        <div id="tab-chat" class="tab-panel hidden"></div>

    </main>

</div>

<script src="js/vendor/jquery-3.6.min.js"></script>
<script src="js/api.js"></script>
<script src="js/state.js"></script>
<script src="js/sidebar.js"></script>
<script src="js/tabs/production.js"></script>
<script src="js/tabs/market.js"></script>
<script src="js/tabs/chat.js"></script>
<script src="js/app.js"></script>

</body>
</html>
```

#### 2. Tab switching in `app.js`

```javascript
// app.js — main entry point

$(document).ready(function() {
    // Check auth
    const token = localStorage.getItem('token');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    // Initialize
    initSidebar();
    initTabs();
    loadPlayerState();

    // Poll for updates every 2 seconds
    setInterval(loadPlayerState, 2000);
});

function initTabs() {
    $('#tab-bar').on('click', '.tab-btn', function() {
        const tab = $(this).data('tab');
        switchTab(tab);
    });
}

function switchTab(tabName) {
    State.activeTab = tabName;

    // Update tab buttons
    $('.tab-btn').removeClass('active');
    $(`.tab-btn[data-tab="${tabName}"]`).addClass('active');

    // Show/hide panels
    $('.tab-panel').addClass('hidden');
    $(`#tab-${tabName}`).removeClass('hidden');

    // Refresh tab content
    refreshActiveTab();
}

function refreshActiveTab() {
    switch (State.activeTab) {
        case 'production': renderProductionTab(); break;
        case 'market':     renderMarketTab();     break;
        case 'chat':       renderChatTab();        break;
    }
}

async function loadPlayerState() {
    try {
        const data = await API.get('/api/v1/state');
        State.player = {
            playerId: data.playerId,
            username: data.username,
            cash: data.cash,
            netWorth: data.net_worth
        };
        State.inventory = data.inventory || {};
        State.facilities = data.facilities || [];

        updateHeader();
        updateSidebarStocks();
        refreshActiveTab();
    } catch (err) {
        console.error('Failed to load state:', err);
    }
}

function updateHeader() {
    $('#header-cash').text(formatCash(State.player.cash));
    $('#header-player').text(State.player.username);
}
```

#### 3. `api.js` — centralized HTTP client

```javascript
const API = {
    _token: () => localStorage.getItem('token'),

    async get(path) {
        const resp = await fetch(path, {
            headers: { 'Authorization': `Bearer ${this._token()}` }
        });
        if (resp.status === 401) { logout(); return; }
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        return data;
    },

    async post(path, body) {
        const resp = await fetch(path, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${this._token()}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        });
        if (resp.status === 401) { logout(); return; }
        const data = await resp.json();
        if (data.error) throw new Error(data.error);
        return data;
    },

    async del(path) {
        const resp = await fetch(path, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${this._token()}` }
        });
        if (resp.status === 401) { logout(); return; }
        return resp.json();
    }
};

function logout() {
    localStorage.removeItem('token');
    window.location.href = '/login.html';
}

function formatCash(amount) {
    return '$' + Number(amount).toLocaleString('en-US', { minimumFractionDigits: 2 });
}

function formatNumber(n) {
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return Math.round(n).toString();
}
```

#### 4. `main.css` — design system

```css
/* === Reset & Variables === */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
    --bg-dark:     #0d1117;
    --bg-card:     #161b22;
    --bg-hover:    #1c2128;
    --border:      #30363d;
    --text-primary: #e6edf3;
    --text-muted:  #8b949e;
    --accent:      #58a6ff;
    --accent-hover:#79c0ff;
    --success:     #3fb950;
    --warning:     #d29922;
    --danger:      #f85149;
    --header-h:    52px;
    --sidebar-w:   200px;
    --tab-h:       44px;
}

body {
    background: var(--bg-dark);
    color: var(--text-primary);
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace;
    font-size: 14px;
    height: 100vh;
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

/* === Header === */
#game-header {
    height: var(--header-h);
    background: var(--bg-card);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    padding: 0 16px;
    gap: 16px;
    flex-shrink: 0;
    z-index: 100;
}

.header-logo { font-weight: 700; font-size: 16px; color: var(--accent); }

.header-cash {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 18px;
    font-weight: 600;
    color: var(--success);
}

.header-cash .label { font-size: 12px; color: var(--text-muted); font-weight: 400; }

#header-player { color: var(--text-muted); }

/* === App Layout === */
#app-layout {
    display: flex;
    flex: 1;
    overflow: hidden;
}

/* === Sidebar === */
#sidebar {
    width: var(--sidebar-w);
    background: var(--bg-card);
    border-right: 1px solid var(--border);
    overflow-y: auto;
    flex-shrink: 0;
}

.sidebar-tier-header {
    padding: 10px 12px 6px;
    font-size: 11px;
    font-weight: 600;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    user-select: none;
}

.sidebar-tier-header:hover { color: var(--text-primary); }

.resource-list { list-style: none; }
.resource-list.collapsed { display: none; }

.resource-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 5px 12px;
    cursor: pointer;
    border-radius: 4px;
    margin: 1px 4px;
}

.resource-item:hover { background: var(--bg-hover); }
.resource-item.selected { background: rgba(88, 166, 255, 0.12); color: var(--accent); }

.resource-stock { font-size: 12px; color: var(--text-muted); font-family: monospace; }

/* === Content Area === */
#content-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

/* === Tab Bar === */
#tab-bar {
    height: var(--tab-h);
    background: var(--bg-card);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: stretch;
    flex-shrink: 0;
}

.tab-btn {
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    color: var(--text-muted);
    padding: 0 20px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 500;
    transition: color 0.15s, border-color 0.15s;
}

.tab-btn:hover { color: var(--text-primary); }
.tab-btn.active { color: var(--accent); border-bottom-color: var(--accent); }

/* === Tab Panels === */
.tab-panel { flex: 1; overflow-y: auto; padding: 16px; }
.tab-panel.hidden { display: none; }

/* === Cards === */
.card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 12px;
}

.card-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 12px;
}

/* === Tables === */
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
    text-align: left;
    padding: 6px 10px;
    font-size: 11px;
    color: var(--text-muted);
    text-transform: uppercase;
    border-bottom: 1px solid var(--border);
}
.data-table td { padding: 8px 10px; border-bottom: 1px solid var(--border); }
.data-table tr:last-child td { border-bottom: none; }
.data-table tr:hover td { background: var(--bg-hover); }

/* === Buttons === */
.btn {
    padding: 6px 14px;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--bg-hover);
    color: var(--text-primary);
    cursor: pointer;
    font-size: 13px;
    transition: background 0.15s;
}
.btn:hover { background: var(--border); }
.btn-primary { background: var(--accent); color: #0d1117; border-color: var(--accent); font-weight: 600; }
.btn-primary:hover { background: var(--accent-hover); }
.btn-danger { border-color: var(--danger); color: var(--danger); }
.btn-danger:hover { background: rgba(248, 81, 73, 0.1); }
.btn-small { padding: 3px 8px; font-size: 12px; }

/* === Forms === */
.form-group { margin-bottom: 12px; }
.form-group label { display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
.form-input {
    width: 100%;
    padding: 6px 10px;
    background: var(--bg-dark);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: var(--text-primary);
    font-size: 13px;
}
.form-input:focus { outline: none; border-color: var(--accent); }

/* === States === */
.badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; }
.badge-active { background: rgba(63, 185, 80, 0.15); color: var(--success); }
.badge-idle { background: rgba(210, 153, 34, 0.15); color: var(--warning); }
.badge-ai { background: rgba(88, 166, 255, 0.15); color: var(--accent); }

/* === Utilities === */
.hidden { display: none !important; }
.text-muted { color: var(--text-muted); }
.text-success { color: var(--success); }
.text-danger { color: var(--danger); }
.text-warning { color: var(--warning); }
.flex { display: flex; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }
```

---

## 21.3 Production Tab

### What It Does

Two views:
- **Per-resource view** (resource selected in sidebar): all facilities for that resource, capacity vs. actual output, build button
- **Global view** (no resource selected): all facilities in one table, quick-action buttons

### Implementation

```javascript
// js/tabs/production.js

function renderProductionTab() {
    if (State.selectedResource) {
        renderProductionResourceView(State.selectedResource);
    } else {
        renderProductionGlobalView();
    }
}

function renderProductionResourceView(resource) {
    const facilities = State.facilities.filter(f => f.resourceName === resource && f.state !== 'destroyed');
    const stock = State.inventory[resource] || 0;
    const res = RESOURCE_LABELS[resource] || resource;

    let html = `
        <div class="flex-between" style="margin-bottom: 16px;">
            <h2 style="font-size: 18px;">${res}</h2>
            <span class="text-muted">In stock: <strong style="color: var(--text-primary)">${formatNumber(stock)}</strong></span>
        </div>
    `;

    // Facilities for this resource
    html += `<div class="card">
        <div class="card-title">Your Facilities</div>`;

    if (facilities.length === 0) {
        html += `<div class="text-muted">No facilities. Build one below.</div>`;
    } else {
        html += `<table class="data-table">
            <thead><tr>
                <th>Status</th>
                <th>Capacity / tick</th>
                <th>Actual / tick</th>
                <th>Op. Cost</th>
                <th>Actions</th>
            </tr></thead>
            <tbody>`;

        facilities.forEach(f => {
            const isActive = f.state === 'active';
            const actual = isActive ? f.actualOutput : 0;
            const capacity = f.capacity;
            const efficiency = capacity > 0 ? Math.round((actual / capacity) * 100) : 0;
            const effColor = efficiency >= 80 ? 'success' : efficiency >= 40 ? 'warning' : 'danger';

            html += `<tr>
                <td><span class="badge badge-${f.state}">${f.state}</span></td>
                <td>${capacity}</td>
                <td><span class="text-${effColor}">${actual}</span> <span class="text-muted">(${efficiency}%)</span></td>
                <td class="text-muted">$${f.operatingCost}/tick</td>
                <td>
                    ${isActive
                        ? `<button class="btn btn-small" onclick="facilityAction('idle', ${f.id})">Idle</button>`
                        : `<button class="btn btn-small btn-primary" onclick="facilityAction('activate', ${f.id})">Activate</button>`
                    }
                    <button class="btn btn-small btn-danger" onclick="facilityAction('downsize', ${f.id})">Sell</button>
                </td>
            </tr>`;
        });

        html += `</tbody></table>`;
    }

    html += `</div>`;

    // Build new facility
    const buildCost = getBuildCost(resource);
    const canAfford = State.player.cash >= buildCost;
    html += `
        <div class="card">
            <div class="card-title">Build Facility</div>
            <div class="flex-between">
                <div>
                    <div>Cost: <strong>${formatCash(buildCost)}</strong></div>
                    <div class="text-muted" style="margin-top: 4px; font-size: 12px;">
                        ${getRecipeDescription(resource)}
                    </div>
                </div>
                <button class="btn btn-primary ${canAfford ? '' : 'disabled'}"
                        onclick="buildFacility('${resource}')"
                        ${canAfford ? '' : 'disabled'}>
                    Build
                </button>
            </div>
        </div>
    `;

    $('#tab-production').html(html);
}

function renderProductionGlobalView() {
    const active = State.facilities.filter(f => f.state === 'active');
    const idle = State.facilities.filter(f => f.state === 'idle');
    const totalCostPerTick = State.facilities.reduce((sum, f) => sum + (f.state !== 'destroyed' ? f.operatingCost : 0), 0);

    let html = `
        <div class="card">
            <div class="flex-between">
                <div class="card-title">All Facilities</div>
                <span class="text-muted">Total cost: <strong class="text-danger">$${totalCostPerTick.toFixed(2)}/tick</strong></span>
            </div>
            <div style="margin-bottom: 12px; font-size: 13px; color: var(--text-muted);">
                ${active.length} active &nbsp;·&nbsp; ${idle.length} idle
            </div>
            <table class="data-table">
                <thead><tr>
                    <th>Resource</th>
                    <th>Status</th>
                    <th>Output / tick</th>
                    <th>Op. Cost</th>
                    <th>Actions</th>
                </tr></thead>
                <tbody>
    `;

    const facilities = State.facilities.filter(f => f.state !== 'destroyed');
    if (facilities.length === 0) {
        html += `<tr><td colspan="5" class="text-muted" style="text-align:center;padding:20px;">No facilities yet. Select a resource from the sidebar to build one.</td></tr>`;
    } else {
        facilities.forEach(f => {
            html += `<tr>
                <td><strong>${RESOURCE_LABELS[f.resourceName] || f.resourceName}</strong></td>
                <td><span class="badge badge-${f.state}">${f.state}</span></td>
                <td>${f.state === 'active' ? f.actualOutput : '—'}</td>
                <td class="text-muted">$${f.operatingCost.toFixed(2)}</td>
                <td>
                    ${f.state === 'active'
                        ? `<button class="btn btn-small" onclick="facilityAction('idle', ${f.id})">Idle</button>`
                        : `<button class="btn btn-small btn-primary" onclick="facilityAction('activate', ${f.id})">Activate</button>`
                    }
                    <button class="btn btn-small btn-danger" onclick="facilityAction('downsize', ${f.id})">Sell</button>
                </td>
            </tr>`;
        });
    }

    html += `</tbody></table></div>`;
    $('#tab-production').html(html);
}

async function buildFacility(resource) {
    try {
        await API.post('/api/v1/production/build', { resource });
        await loadPlayerState();
        showToast(`Built ${RESOURCE_LABELS[resource]} facility!`, 'success');
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function facilityAction(action, facilityId) {
    if (action === 'downsize') {
        if (!confirm('Sell this facility? You will receive 40% of the build cost. This cannot be undone.')) return;
    }
    try {
        await API.post(`/api/v1/production/${action}`, { facilityId });
        await loadPlayerState();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function getBuildCost(resource) {
    const tier = getResourceTier(resource);
    const costs = { raw: 100, intermediate: 300, advanced_intermediate: 800, consumer: 2000 };
    return costs[tier] || 100;
}

function getRecipeDescription(resource) {
    const recipes = {
        bread: 'Inputs: Wheat ×2', steel: 'Inputs: Iron ×2, Coal ×1',
        circuit: 'Inputs: Silicon ×2, Copper ×1', phone: 'Inputs: Circuit, Glass, Battery, Aluminium',
        // ... etc — ideally fetched from /api/v1/config
    };
    return recipes[resource] || 'No inputs required';
}

function getResourceTier(resource) {
    if (RESOURCES.raw.includes(resource)) return 'raw';
    if (['circuit', 'battery'].includes(resource)) return 'advanced_intermediate';
    if (RESOURCES.intermediate.includes(resource)) return 'intermediate';
    return 'consumer';
}
```

### Pitfalls & Foresight

**Pitfall 1: Re-rendering the entire DOM on every poll**
- ❌ Bad: Every 2 seconds, replace all of `#tab-production` innerHTML. Causes UI flicker.
- ✅ Good: Use **reconciliation** — only update values that changed. Or use a virtual DOM approach.
- Simplest: update only the cells that changed by comparing to previous state.

**Pitfall 2: Missing `actualOutput` from API**
- The production tab shows "capacity vs. actual output" — the API must return both.
- ✅ `GET /api/v1/state` must include `facilities[].actualOutput` (requires tracking in tick engine).

**Pitfall 3: Build button not greyed out when broke**
- ❌ Bad: Player clicks build, gets a server error. Confusing.
- ✅ Good: Disable button client-side if `State.player.cash < buildCost`. Show tooltip.

**Foresight for Phase 2 (Economy):**
- Fetch recipes from `/api/v1/config` dynamically — don't hardcode in JS.
- Otherwise every recipe change requires a frontend deploy.

---

## 20.1 Player Chat

### What It Does

Direct messaging between any two players (human ↔ human, human ↔ AI). Messages persist in the database. Both sender and recipient can be an AI agent.

### Implementation

```javascript
// js/tabs/chat.js

let chatPollInterval = null;

function renderChatTab() {
    if (!$('#chat-container').length) {
        $('#tab-chat').html(`
            <div class="card" style="display: flex; gap: 16px; height: 100%;">

                <!-- Conversation list -->
                <div id="conversation-list" style="width: 200px; flex-shrink: 0; border-right: 1px solid var(--border); padding-right: 16px;">
                    <div class="card-title">Conversations</div>
                    <div id="conversation-items"></div>
                </div>

                <!-- Message panel -->
                <div id="chat-panel" style="flex: 1; display: flex; flex-direction: column;">
                    <div id="chat-messages" style="flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding-bottom: 12px;">
                        <div class="text-muted" style="text-align:center;margin-top:40px;">Select a conversation or start a new one</div>
                    </div>

                    <div id="chat-input-area" style="border-top: 1px solid var(--border); padding-top: 12px;">
                        <div class="flex-between" style="gap: 8px;">
                            <select id="chat-recipient" class="form-input" style="width: 160px;">
                                <option value="">Select player...</option>
                            </select>
                            <input type="text" id="chat-message-input" class="form-input" placeholder="Type a message..." style="flex: 1;">
                            <button class="btn btn-primary" onclick="sendChatMessage()">Send</button>
                        </div>
                    </div>
                </div>

            </div>
        `);

        // Press Enter to send
        $('#chat-message-input').on('keydown', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendChatMessage();
            }
        });
    }

    loadChatData();
    startChatPolling();
}

async function loadChatData() {
    try {
        const data = await API.get('/api/v1/chat/messages');
        State.messages = data.messages || [];

        // Get unique players from message history
        const players = await API.get('/api/v1/leaderboard');
        populateRecipientList(players.players || []);

        renderMessages();
    } catch (err) {
        console.error('Failed to load chat:', err);
    }
}

function populateRecipientList(players) {
    const select = $('#chat-recipient');
    const currentVal = select.val();
    select.html('<option value="">Select player...</option>');
    players.forEach(p => {
        if (p.playerId !== State.player.playerId) {
            select.append(`<option value="${p.playerId}">${p.username}${p.isAi ? ' 🤖' : ''}</option>`);
        }
    });
    if (currentVal) select.val(currentVal);
}

function renderMessages() {
    const myId = State.player.playerId;
    const messages = State.messages.slice(-100); // Last 100 messages

    if (messages.length === 0) {
        $('#chat-messages').html('<div class="text-muted" style="text-align:center;margin-top:40px;">No messages yet.</div>');
        return;
    }

    let html = '';
    messages.forEach(msg => {
        const isMine = msg.fromPlayerId === myId;
        const align = isMine ? 'flex-end' : 'flex-start';
        const bgColor = isMine ? 'rgba(88, 166, 255, 0.15)' : 'var(--bg-hover)';
        const nameColor = isMine ? 'var(--accent)' : 'var(--text-muted)';

        html += `
            <div style="display: flex; flex-direction: column; align-items: ${align}; gap: 2px;">
                <span style="font-size: 11px; color: ${nameColor};">${msg.fromUsername}${msg.fromIsAi ? ' 🤖' : ''}</span>
                <div style="background: ${bgColor}; padding: 8px 12px; border-radius: 12px; max-width: 70%; word-break: break-word;">
                    ${escapeHtml(msg.message)}
                </div>
                <span style="font-size: 11px; color: var(--text-muted);">${formatTime(msg.createdAt)}</span>
            </div>
        `;
    });

    const chatMsgs = $('#chat-messages');
    chatMsgs.html(html);
    chatMsgs.scrollTop(chatMsgs[0].scrollHeight);
}

async function sendChatMessage() {
    const toPlayerId = parseInt($('#chat-recipient').val());
    const message = $('#chat-message-input').val().trim();

    if (!toPlayerId) { showToast('Select a recipient', 'error'); return; }
    if (!message) return;

    try {
        await API.post('/api/v1/chat/send', { toPlayerId, message });
        $('#chat-message-input').val('');
        await loadChatData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function startChatPolling() {
    if (chatPollInterval) clearInterval(chatPollInterval);
    chatPollInterval = setInterval(loadChatData, 3000);
}

function escapeHtml(text) {
    return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function formatTime(ts) {
    const d = new Date(ts);
    return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}
```

### Toast Notifications

```javascript
// In app.js — global toast helper

function showToast(message, type = 'info') {
    const colors = { success: 'var(--success)', error: 'var(--danger)', info: 'var(--accent)' };
    const toast = $(`
        <div style="
            position: fixed; bottom: 20px; right: 20px; z-index: 1000;
            background: var(--bg-card); border: 1px solid ${colors[type]};
            color: ${colors[type]}; padding: 10px 16px; border-radius: 8px;
            font-size: 13px; max-width: 300px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.5);
        ">${escapeHtml(message)}</div>
    `);

    $('body').append(toast);
    setTimeout(() => toast.fadeOut(300, () => toast.remove()), 3000);
}
```

### Pitfalls & Foresight

**Pitfall 1: XSS via chat messages**
- ❌ Bad: `$('#chat-messages').html(message)` where message contains `<script>alert(1)</script>`
- ✅ Good: Always `escapeHtml()` before inserting user content into DOM.
- This is a critical security issue — chat is the most obvious XSS vector.

**Pitfall 2: Polling too frequently for chat**
- Polling every 500ms multiplied by 100 users = server overload.
- ✅ Poll chat every 3 seconds (less time-sensitive than market data). Or use WebSockets later.

**Pitfall 3: Not stopping chat poll when tab is inactive**
- Chat poll continues running even when user switches to Production tab, wasting requests.
- ✅ Stop polling when leaving the chat tab: call `clearInterval(chatPollInterval)` in `switchTab()`.

**Pitfall 4: AI messages flooding chat**
- If AI agents can send chat messages freely, they'll spam 4 messages per second.
- ✅ Rate limit chat messages: max 1 message per player per 10 seconds.

**Foresight for Phase 3 (AI Integration):**
- AI agents will message players for trade negotiation. Design the chat API with this in mind.
- Add `GET /api/v1/chat/messages?since={timestamp}` for efficient polling (only new messages).
- Consider: AI agent receives a message → it factors it into its next decision. This creates emergent negotiation.

---

## Phase 4 Checklist

- [ ] Sidebar renders all 32 resources grouped by tier
- [ ] Sidebar stock numbers update every poll cycle
- [ ] Resource selection sets `State.selectedResource` and refreshes tab
- [ ] Tab switching works without page reload
- [ ] Production global view shows all facilities
- [ ] Production resource view shows per-resource facilities + build button
- [ ] Build/idle/activate/downsize all work and refresh UI
- [ ] Market tab renders orderbook (see note below)
- [ ] Chat sends and receives messages
- [ ] Chat escapes HTML (XSS protection)
- [ ] Toasts appear for errors and success
- [ ] UI works on 1280px+ screens
- [ ] No direct DB calls from frontend

---

## Note: Market Tab

The Market tab (showing orderbook, price charts, post/cancel orders) follows the same patterns as Production. Key additions:

- **Price charts:** Use `<canvas>` with a lightweight chart library (Chart.js is MIT licensed and small) or a simple SVG line renderer. Fetch from `GET /api/v1/market/history/{resource}`.
- **Orderbook:** Two columns (bids, asks), color-coded green/red, scrollable.
- **Post offer form:** Inputs for price, quantity, optional reserve/target. Submit to `/api/v1/market/sell` or `/api/v1/market/buy`.
- **Cancel button:** Each of the player's open orders has a cancel button calling `DELETE /api/v1/market/{offerId}`.

The Market tab deserves its own guide if it grows complex (especially chart rendering). Start simple — a plain table of historical prices is more useful than a broken fancy chart.

---

## Summary

Phase 4 gives humans the controls to operate the economy built in Phases 1–3. The implementation principles:

| Principle | Implementation |
|-----------|---------------|
| All data via API | `API.get()` / `API.post()` only — no direct DB |
| Reactive UI | `State` object → DOM updates. Never reverse. |
| Security | `escapeHtml()` on all user content |
| Performance | Poll at 2s for state, 3s for chat. Stop polls on tab change. |
| Parity | UI actions and AI actions use identical endpoints |

The UI layer is intentionally thin — it translates `State` to pixels and user clicks to API calls. The game logic lives entirely server-side.

With Phase 4 complete, Trade Empire is a fully playable multiplayer economic simulation with AI participants and a human-accessible interface.
