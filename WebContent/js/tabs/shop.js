/**
 * shop.js — Shop tab rendering.
 *
 * Two views:
 *   - Per-resource view (resource selected): stock/unstock/price for that resource in your shop
 *   - Global view (no resource): all shops with full inventory + demand forecast
 *
 * NPCs buy consumer goods from shops each tick (TickEngine step 4).
 * Prices above the demand cap receive no NPC purchases.
 */

var _shopData = null;       // cached shop list response
var _demandData = null;     // cached demand forecast response

// Resources that NPCs will buy from shops (matches ResourceRegistry.isConsumerGood())
function isConsumerGood(resource) {
    if (resource === 'bread' || resource === 'canned_food') return true;
    return RESOURCE_TIERS.consumer && RESOURCE_TIERS.consumer.indexOf(resource) !== -1;
}

function renderShopTab() {
    if (State.selectedResource) {
        renderShopResourceView(State.selectedResource);
    } else {
        renderShopGlobalView();
    }
}

// ── Global view ──────────────────────────────────────────────────────────

async function renderShopGlobalView() {
    // Show loading on first render
    if (!$('#shop-global-wrap').length) {
        $('#tab-shop').html('<div id="shop-global-wrap"><div class="text-muted">Loading...</div></div>');
    }

    try {
        var shopPromise = API.get('/api/v1/shop/list');
        var demandPromise = API.get('/api/v1/shop/demand');

        _shopData = await shopPromise;
        _demandData = await demandPromise;
    } catch (err) {
        $('#shop-global-wrap').html('<div class="text-danger">' + escapeHtml(err.message) + '</div>');
        return;
    }

    var shops = _shopData.shops || [];
    var forecasts = _demandData.forecasts || {};

    var html = '';

    // ── Create shop section ──
    if (shops.length === 0) {
        html += '<div class="card">' +
            '<div class="card-title">Your Shop</div>' +
            '<div class="empty-state">' +
                '<div class="message">You don\'t have a shop yet. Create one to start selling to NPCs.</div>' +
                '<div style="margin-top:12px;">' +
                    '<input type="text" id="new-shop-name" class="form-input" style="width:200px;display:inline-block;margin-right:8px;" placeholder="Shop name">' +
                    '<button class="btn btn-primary" onclick="createShop()">Create Shop</button>' +
                '</div>' +
            '</div>' +
        '</div>';
    }

    // ── Shops + inventory ──
    $.each(shops, function(i, shop) {
        var inv = shop.inventory || [];

        html += '<div class="card">' +
            '<div class="flex-between">' +
                '<div class="card-title">' + escapeHtml(shop.name) + '</div>' +
                '<span class="text-muted" style="font-size:11px;">Shop #' + shop.id + '</span>' +
            '</div>';

        if (inv.length === 0) {
            html += '<div class="text-muted" style="font-size:13px;">No items stocked. Select a consumer good from the sidebar to stock your shop.</div>';
        } else {
            html += '<table class="data-table"><thead><tr>' +
                '<th>Resource</th><th>Stock</th><th>Your Price</th><th>Demand Cap</th><th>Status</th><th>Actions</th>' +
            '</tr></thead><tbody>';

            $.each(inv, function(j, item) {
                var label = RESOURCE_LABELS[item.resource] || item.resource;
                var forecast = forecasts[item.resource];
                var priceCap = forecast ? Number(forecast.price_cap || forecast.priceCap || 0) : 0;
                var setPrice = item.set_price != null ? Number(item.set_price) : null;
                var selling = setPrice != null && setPrice > 0 && item.quantity > 0;
                var overpriced = selling && priceCap > 0 && setPrice > priceCap;

                html += '<tr>' +
                    '<td><strong style="cursor:pointer;color:var(--accent)" onclick="selectResource(\'' + item.resource + '\')">' + escapeHtml(label) + '</strong></td>' +
                    '<td class="num">' + formatNumber(item.quantity) + '</td>' +
                    '<td class="num">' + (setPrice != null ? formatCash(setPrice) : '<span class="text-muted">not set</span>') + '</td>' +
                    '<td class="num">' + (priceCap > 0 ? formatCash(priceCap) : '\u2014') + '</td>' +
                    '<td>' + (overpriced
                        ? '<span class="badge badge-idle">overpriced</span>'
                        : (selling
                            ? '<span class="badge badge-active">selling</span>'
                            : '<span class="text-muted" style="font-size:12px;">inactive</span>')) +
                    '</td>' +
                    '<td>' +
                        '<button class="btn btn-small btn-danger" onclick="unstockItem(' + shop.id + ',\'' + item.resource + '\',' + item.quantity + ')">Unstock</button>' +
                    '</td>' +
                '</tr>';
            });

            html += '</tbody></table>';
        }

        html += '</div>';
    });

    // ── Demand forecast card ──
    var forecastKeys = Object.keys(forecasts);
    if (forecastKeys.length > 0) {
        html += '<div class="card">' +
            '<div class="card-title">NPC Demand Forecast</div>' +
            '<div class="text-muted" style="font-size:12px;margin-bottom:8px;">NPCs buy consumer goods from shops each tick. Price your items at or below the demand cap to make sales.</div>' +
            '<table class="data-table"><thead><tr>' +
                '<th>Resource</th><th>Demand Cap</th><th>Trend</th>' +
            '</tr></thead><tbody>';

        $.each(forecastKeys, function(i, key) {
            var f = forecasts[key];
            var label = RESOURCE_LABELS[key] || key;
            var priceCap = Number(f.price_cap || f.priceCap || 0);
            var trend = f.trend || 'stable';
            var trendIcon = trend === 'rising' ? '\u25B2' : (trend === 'falling' ? '\u25BC' : '\u25CF');
            var trendClass = trend === 'rising' ? 'text-success' : (trend === 'falling' ? 'text-danger' : 'text-muted');

            html += '<tr>' +
                '<td><strong style="cursor:pointer;color:var(--accent)" onclick="selectResource(\'' + key + '\')">' + escapeHtml(label) + '</strong></td>' +
                '<td class="num">' + formatCash(priceCap) + '</td>' +
                '<td class="num ' + trendClass + '">' + trendIcon + ' ' + trend + '</td>' +
            '</tr>';
        });

        html += '</tbody></table></div>';
    }

    $('#tab-shop').html('<div id="shop-global-wrap">' + html + '</div>');
}

// ── Per-resource view ────────────────────────────────────────────────────

async function renderShopResourceView(resource) {
    var label = RESOURCE_LABELS[resource] || resource;
    var playerStock = State.inventory[resource] || 0;

    // Load shop + demand data
    try {
        var shopPromise = API.get('/api/v1/shop/list');
        var demandPromise = API.get('/api/v1/shop/demand');

        _shopData = await shopPromise;
        _demandData = await demandPromise;
    } catch (err) {
        $('#tab-shop').html('<div class="card text-danger">' + escapeHtml(err.message) + '</div>');
        return;
    }

    var shops = _shopData.shops || [];
    var forecasts = _demandData.forecasts || {};
    var forecast = forecasts[resource];
    var isConsumer = isConsumerGood(resource);

    var html = '<div class="flex-between" style="margin-bottom:16px;">' +
        '<h2 style="font-size:18px;">' + escapeHtml(label) + ' &mdash; Shop</h2>' +
        '<span class="text-muted">In inventory: <strong style="color:var(--text-primary)">' + formatNumber(playerStock) + '</strong></span>' +
    '</div>';

    if (!isConsumer) {
        html += '<div class="card"><div class="empty-state">' +
            '<div class="message">This resource is not a consumer good. NPCs only buy consumer goods from shops.</div>' +
            '<div class="text-muted" style="margin-top:8px;font-size:12px;">Consumer goods: Bread, Canned Food, Car, Phone, Clothing, Furniture, Laptop, Bicycle, Jewelry</div>' +
        '</div></div>';
        $('#tab-shop').html(html);
        return;
    }

    // Demand info card
    if (forecast) {
        var priceCap = Number(forecast.price_cap || forecast.priceCap || 0);
        var trend = forecast.trend || 'stable';
        var trendIcon = trend === 'rising' ? '\u25B2' : (trend === 'falling' ? '\u25BC' : '\u25CF');
        var trendClass = trend === 'rising' ? 'text-success' : (trend === 'falling' ? 'text-danger' : 'text-muted');

        html += '<div class="card" style="padding:12px 16px;">' +
            '<div class="flex-between">' +
                '<span class="text-muted" style="font-size:12px;">NPC demand cap: <strong class="mono" style="color:var(--text-primary)">' + formatCash(priceCap) + '</strong></span>' +
                '<span class="' + trendClass + '" style="font-size:12px;">' + trendIcon + ' ' + trend + '</span>' +
            '</div>' +
            '<div class="text-muted" style="font-size:11px;margin-top:4px;">Price at or below the demand cap for NPCs to buy. NPCs purchase 1\u20135 units per tick.</div>' +
        '</div>';
    }

    // No shop yet?
    if (shops.length === 0) {
        html += '<div class="card">' +
            '<div class="card-title">Create a Shop First</div>' +
            '<div style="display:flex;gap:8px;align-items:center;">' +
                '<input type="text" id="new-shop-name" class="form-input" style="width:200px;" placeholder="Shop name">' +
                '<button class="btn btn-primary" onclick="createShop()">Create Shop</button>' +
            '</div>' +
        '</div>';
        $('#tab-shop').html(html);
        return;
    }

    // Show per-shop stocking controls
    $.each(shops, function(i, shop) {
        var inv = shop.inventory || [];
        var item = null;
        $.each(inv, function(j, it) {
            if (it.resource === resource) item = it;
        });

        var shopQty = item ? item.quantity : 0;
        var setPrice = item && item.set_price != null ? Number(item.set_price) : null;

        html += '<div class="card">' +
            '<div class="flex-between">' +
                '<div class="card-title">' + escapeHtml(shop.name) + '</div>' +
                '<span class="text-muted" style="font-size:12px;">Shop stock: <strong style="color:var(--text-primary)">' + formatNumber(shopQty) + '</strong></span>' +
            '</div>';

        // Current price
        if (setPrice != null) {
            var priceCap = forecast ? Number(forecast.price_cap || forecast.priceCap || 0) : 0;
            var overpriced = priceCap > 0 && setPrice > priceCap;
            html += '<div style="margin-bottom:12px;">' +
                '<span class="text-muted" style="font-size:12px;">Current price: </span>' +
                '<strong class="mono' + (overpriced ? ' text-danger' : '') + '">' + formatCash(setPrice) + '</strong>' +
                (overpriced ? ' <span class="text-danger" style="font-size:11px;">(above demand cap \u2014 NPCs won\'t buy)</span>' : '') +
            '</div>';
        }

        // Stock form
        html += '<div class="shop-actions">' +
            '<div class="shop-action-row">' +
                '<label class="text-muted" style="font-size:12px;min-width:50px;">Stock</label>' +
                '<input type="number" id="stock-qty-' + shop.id + '" class="form-input" style="width:100px;" min="1" step="1" placeholder="Qty">' +
                '<button class="btn btn-primary btn-small" onclick="stockResource(' + shop.id + ',\'' + resource + '\')"' +
                    (playerStock <= 0 ? ' disabled' : '') + '>Stock</button>' +
            '</div>' +
            '<div class="shop-action-row">' +
                '<label class="text-muted" style="font-size:12px;min-width:50px;">Price</label>' +
                '<input type="number" id="price-val-' + shop.id + '" class="form-input" style="width:100px;" min="0.01" step="0.01" placeholder="0.00"' +
                    (setPrice != null ? ' value="' + setPrice.toFixed(2) + '"' : '') + '>' +
                '<button class="btn btn-primary btn-small" onclick="setShopPrice(' + shop.id + ',\'' + resource + '\')">Set Price</button>' +
            '</div>' +
            '<div class="shop-action-row">' +
                '<label class="text-muted" style="font-size:12px;min-width:50px;">Unstock</label>' +
                '<input type="number" id="unstock-qty-' + shop.id + '" class="form-input" style="width:100px;" min="1" step="1" placeholder="Qty">' +
                '<button class="btn btn-small btn-danger" onclick="unstockResource(' + shop.id + ',\'' + resource + '\')"' +
                    (shopQty <= 0 ? ' disabled' : '') + '>Unstock</button>' +
            '</div>' +
        '</div>';

        html += '</div>';
    });

    $('#tab-shop').html(html);
}

// ── Actions ──────────────────────────────────────────────────────────────

async function createShop() {
    var name = ($('#new-shop-name').val() || '').trim() || 'My Shop';
    try {
        await API.post('/api/v1/shop/create', { shop_name: name });
        showToast('Shop created!', 'success');
        renderShopTab();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function stockResource(shopId, resource) {
    var qty = parseInt($('#stock-qty-' + shopId).val());
    if (!qty || qty <= 0) { showToast('Enter a valid quantity', 'error'); return; }

    try {
        await API.post('/api/v1/shop/stock', { shop_id: shopId, resource: resource, quantity: qty });
        showToast('Stocked ' + qty + ' ' + (RESOURCE_LABELS[resource] || resource), 'success');
        $('#stock-qty-' + shopId).val('');
        await loadPlayerState();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function setShopPrice(shopId, resource) {
    var price = parseFloat($('#price-val-' + shopId).val());
    if (!price || price < 0) { showToast('Enter a valid price', 'error'); return; }

    try {
        await API.post('/api/v1/shop/price', { shop_id: shopId, resource: resource, price: price });
        showToast('Price set to ' + formatCash(price), 'success');
        renderShopTab();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function unstockResource(shopId, resource) {
    var qty = parseInt($('#unstock-qty-' + shopId).val());
    if (!qty || qty <= 0) { showToast('Enter a valid quantity', 'error'); return; }

    try {
        await API.post('/api/v1/shop/unstock', { shop_id: shopId, resource: resource, quantity: qty });
        showToast('Unstocked ' + qty + ' ' + (RESOURCE_LABELS[resource] || resource), 'info');
        $('#unstock-qty-' + shopId).val('');
        await loadPlayerState();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function unstockItem(shopId, resource, maxQty) {
    var qty = prompt('Unstock how many ' + (RESOURCE_LABELS[resource] || resource) + '? (max ' + maxQty + ')', maxQty);
    if (qty === null) return;
    qty = parseInt(qty);
    if (!qty || qty <= 0) return;

    try {
        await API.post('/api/v1/shop/unstock', { shop_id: shopId, resource: resource, quantity: qty });
        showToast('Unstocked ' + qty + ' ' + (RESOURCE_LABELS[resource] || resource), 'info');
        await loadPlayerState();
    } catch (err) {
        showToast(err.message, 'error');
    }
}
