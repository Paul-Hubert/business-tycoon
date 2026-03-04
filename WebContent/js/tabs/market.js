/**
 * market.js — Market tab rendering.
 *
 * Shows:
 *   - Per-resource view: orderbook, price history, order form
 *   - Global view: market prices overview, player's open orders
 */

var _marketOrderSide = 'buy';

function renderMarketTab() {
    if (State.selectedResource) {
        renderMarketResourceView(State.selectedResource);
    } else {
        renderMarketGlobalView();
    }
}

// ── Per-resource view ────────────────────────────────────────────────────

var _currentMarketResource = null;

async function renderMarketResourceView(resource) {
    var label = RESOURCE_LABELS[resource] || resource;
    var stock = State.inventory[resource] || 0;

    // If already showing this resource's market view, just update data in place
    if (_currentMarketResource === resource && $('#order-price').length) {
        $('#market-stock-count').text(formatNumber(stock));
        loadOrderbook(resource);
        loadPriceHistory(resource);
        loadMyOrders(resource);
        return;
    }

    _currentMarketResource = resource;

    var html = '<div class="flex-between" style="margin-bottom:16px;">' +
        '<h2 style="font-size:18px;">' + escapeHtml(label) + ' Market</h2>' +
        '<span class="text-muted">Your stock: <strong id="market-stock-count" style="color:var(--text-primary)">' + formatNumber(stock) + '</strong></span>' +
    '</div>';

    // Order form
    html += '<div class="card">' +
        '<div class="card-title">Place Order</div>' +
        '<div class="order-form">' +
            '<div class="form-group">' +
                '<label>Side</label>' +
                '<div class="side-toggle">' +
                    '<button id="side-buy" class="' + (_marketOrderSide === 'buy' ? 'active-buy' : '') + '" onclick="setOrderSide(\'buy\')">Buy</button>' +
                    '<button id="side-sell" class="' + (_marketOrderSide === 'sell' ? 'active-sell' : '') + '" onclick="setOrderSide(\'sell\')">Sell</button>' +
                '</div>' +
            '</div>' +
            '<div class="form-group">' +
                '<label>Price</label>' +
                '<input type="number" id="order-price" class="form-input" step="0.01" min="0.01" placeholder="0.00">' +
            '</div>' +
            '<div class="form-group">' +
                '<label>Quantity</label>' +
                '<input type="number" id="order-qty" class="form-input" step="1" min="1" placeholder="0">' +
            '</div>' +
            '<div class="form-group">' +
                '<label>&nbsp;</label>' +
                '<button class="btn btn-primary" onclick="placeOrder(\'' + resource + '\')">Submit</button>' +
            '</div>' +
        '</div>' +
    '</div>';

    // Orderbook placeholder (loaded async)
    html += '<div class="card" id="orderbook-card">' +
        '<div class="card-title">Order Book</div>' +
        '<div id="orderbook-content" class="text-muted">Loading...</div>' +
    '</div>';

    // Price history placeholder
    html += '<div class="card" id="history-card">' +
        '<div class="card-title">Recent Trades</div>' +
        '<div id="history-content" class="text-muted">Loading...</div>' +
    '</div>';

    // My orders for this resource
    html += '<div class="card">' +
        '<div class="card-title">Your Open Orders</div>' +
        '<div id="my-orders-content" class="text-muted">Loading...</div>' +
    '</div>';

    $('#tab-market').html(html);

    // Load async data
    loadOrderbook(resource);
    loadPriceHistory(resource);
    loadMyOrders(resource);
}

async function loadOrderbook(resource) {
    try {
        var data = await API.get('/api/v1/market/orderbook/' + resource);
        var buys = data.buys || [];
        var sells = data.sells || [];

        var html = '<div class="orderbook-container">' +
            '<div class="orderbook-side buys"><h3>Bids (Buy)</h3>';

        if (buys.length === 0) {
            html += '<div class="text-muted" style="font-size:12px;">No bids</div>';
        } else {
            $.each(buys, function(i, o) {
                html += '<div class="orderbook-row">' +
                    '<span class="price">' + Number(o.price).toFixed(2) + '</span>' +
                    '<span class="qty">' + formatNumber(o.quantity) + ' (' + o.num_orders + ')</span>' +
                '</div>';
            });
        }

        html += '</div><div class="orderbook-side sells"><h3>Asks (Sell)</h3>';

        if (sells.length === 0) {
            html += '<div class="text-muted" style="font-size:12px;">No asks</div>';
        } else {
            $.each(sells, function(i, o) {
                html += '<div class="orderbook-row">' +
                    '<span class="price">' + Number(o.price).toFixed(2) + '</span>' +
                    '<span class="qty">' + formatNumber(o.quantity) + ' (' + o.num_orders + ')</span>' +
                '</div>';
            });
        }

        html += '</div></div>';
        $('#orderbook-content').html(html);
    } catch (err) {
        $('#orderbook-content').html('<span class="text-danger">' + escapeHtml(err.message) + '</span>');
    }
}

async function loadPriceHistory(resource) {
    try {
        var data = await API.get('/api/v1/market/history/' + resource);
        var history = data.history || [];

        if (history.length === 0) {
            $('#history-content').html('<div class="text-muted">No trade history yet</div>');
            return;
        }

        // Show last 20 trades in a table
        var recent = history.slice(0, 20);
        var html = '<table class="data-table"><thead><tr>' +
            '<th>Tick</th><th>Buy Price</th><th>Sell Price</th><th>Volume</th>' +
        '</tr></thead><tbody>';

        $.each(recent, function(i, h) {
            html += '<tr>' +
                '<td class="num">' + h.tick + '</td>' +
                '<td class="num text-success">' + (h.buy_price ? Number(h.buy_price).toFixed(2) : '\u2014') + '</td>' +
                '<td class="num text-danger">' + (h.sell_price ? Number(h.sell_price).toFixed(2) : '\u2014') + '</td>' +
                '<td class="num">' + (h.volume || 0) + '</td>' +
            '</tr>';
        });

        html += '</tbody></table>';
        $('#history-content').html(html);
    } catch (err) {
        $('#history-content').html('<span class="text-danger">' + escapeHtml(err.message) + '</span>');
    }
}

async function loadMyOrders(resource) {
    try {
        var data = await API.get('/api/v1/market/myorders');
        var orders = data.orders || [];

        // Filter to current resource if specified
        if (resource) {
            orders = orders.filter(function(o) { return o.resource === resource; });
        }

        // Filter to only open orders (not fully filled)
        orders = orders.filter(function(o) { return o.quantity > o.quantity_filled; });

        if (orders.length === 0) {
            $('#my-orders-content').html('<div class="text-muted">No open orders' + (resource ? ' for this resource' : '') + '</div>');
            return;
        }

        var html = '<table class="data-table"><thead><tr>' +
            (resource ? '' : '<th>Resource</th>') +
            '<th>Side</th><th>Price</th><th>Qty (filled)</th><th>Actions</th>' +
        '</tr></thead><tbody>';

        $.each(orders, function(i, o) {
            var remaining = o.quantity - o.quantity_filled;
            html += '<tr>' +
                (resource ? '' : '<td>' + escapeHtml(RESOURCE_LABELS[o.resource] || o.resource) + '</td>') +
                '<td><span class="badge badge-' + o.side + '">' + o.side + '</span></td>' +
                '<td class="num">' + Number(o.price).toFixed(2) + '</td>' +
                '<td class="num">' + remaining + ' / ' + o.quantity + '</td>' +
                '<td><button class="btn btn-small btn-danger" onclick="cancelOrder(' + o.id + ')">Cancel</button></td>' +
            '</tr>';
        });

        html += '</tbody></table>';
        $('#my-orders-content').html(html);
    } catch (err) {
        $('#my-orders-content').html('<span class="text-danger">' + escapeHtml(err.message) + '</span>');
    }
}

// ── Global view ──────────────────────────────────────────────────────────

async function renderMarketGlobalView() {
    // If already showing the global view, just refresh data in place
    if (_currentMarketResource === null && $('#market-prices-content').length) {
        loadMarketPrices();
        loadMyOrders(null);
        return;
    }

    _currentMarketResource = null;

    var html = '<div class="card">' +
        '<div class="card-title">Market Prices</div>' +
        '<div id="market-prices-content" class="text-muted">Loading...</div>' +
    '</div>';

    html += '<div class="card">' +
        '<div class="card-title">Your Open Orders</div>' +
        '<div id="my-orders-content" class="text-muted">Loading...</div>' +
    '</div>';

    $('#tab-market').html(html);

    // Load prices and orders
    loadMarketPrices();
    loadMyOrders(null);
}

async function loadMarketPrices() {
    try {
        var prices = await API.get('/api/v1/market/prices');

        var html = '<table class="data-table"><thead><tr>' +
            '<th>Resource</th><th>Tier</th><th>Price</th>' +
        '</tr></thead><tbody>';

        // Group by tier
        $.each(RESOURCE_TIERS, function(tier, names) {
            $.each(names, function(i, name) {
                var price = prices[name];
                var label = RESOURCE_LABELS[name] || name;
                html += '<tr>' +
                    '<td><strong style="cursor:pointer;color:var(--accent)" onclick="selectResource(\'' + name + '\')">' + escapeHtml(label) + '</strong></td>' +
                    '<td class="text-muted">' + (TIER_LABELS[tier] || tier) + '</td>' +
                    '<td class="num">' + (price ? formatCash(price) : '\u2014') + '</td>' +
                '</tr>';
            });
        });

        html += '</tbody></table>';
        $('#market-prices-content').html(html);
    } catch (err) {
        $('#market-prices-content').html('<span class="text-danger">' + escapeHtml(err.message) + '</span>');
    }
}

// ── Actions ──────────────────────────────────────────────────────────────

function setOrderSide(side) {
    _marketOrderSide = side;
    $('#side-buy').removeClass('active-buy');
    $('#side-sell').removeClass('active-sell');
    if (side === 'buy') {
        $('#side-buy').addClass('active-buy');
    } else {
        $('#side-sell').addClass('active-sell');
    }
}

async function placeOrder(resource) {
    var price = parseFloat($('#order-price').val());
    var qty = parseInt($('#order-qty').val());

    if (!price || price <= 0) { showToast('Enter a valid price', 'error'); return; }
    if (!qty || qty <= 0) { showToast('Enter a valid quantity', 'error'); return; }

    try {
        await API.post('/api/v1/market/order', {
            resource: resource,
            side: _marketOrderSide,
            price: price,
            quantity: qty
        });
        showToast(_marketOrderSide.charAt(0).toUpperCase() + _marketOrderSide.slice(1) + ' order placed!', 'success');
        $('#order-price').val('');
        $('#order-qty').val('');
        // Refresh
        loadOrderbook(resource);
        loadMyOrders(resource);
        await loadPlayerState();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function cancelOrder(orderId) {
    try {
        await API.del('/api/v1/market/order/' + orderId);
        showToast('Order cancelled', 'info');
        refreshActiveTab();
    } catch (err) {
        showToast(err.message, 'error');
    }
}
