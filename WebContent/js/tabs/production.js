/**
 * production.js — Production tab rendering.
 *
 * Two views:
 *   - Per-resource view (resource selected): facilities for that resource + build button
 *   - Global view (no resource): all facilities in one table
 */

function renderProductionTab() {
    if (State.selectedResource) {
        renderProductionResourceView(State.selectedResource);
    } else {
        renderProductionGlobalView();
    }
}

function renderProductionResourceView(resource) {
    var facilities = [];
    $.each(State.facilities, function(i, f) {
        if (f.resource_name === resource && f.state !== 'destroyed') {
            facilities.push(f);
        }
    });
    var stock = State.inventory[resource] || 0;
    var label = RESOURCE_LABELS[resource] || resource;
    var tier = getResourceTier(resource);

    var html = '<div class="flex-between" style="margin-bottom:16px;">' +
        '<h2 style="font-size:18px;">' + escapeHtml(label) + '</h2>' +
        '<span class="text-muted">In stock: <strong style="color:var(--text-primary)">' + formatNumber(stock) + '</strong></span>' +
    '</div>';

    // Recipe info
    var recipeHtml = getRecipeDescription(resource);
    if (recipeHtml) {
        html += '<div class="card" style="padding:12px 16px;margin-bottom:12px;">' +
            '<span class="text-muted" style="font-size:12px;">Recipe: </span>' +
            '<span style="font-size:13px;">' + recipeHtml + '</span>' +
        '</div>';
    }

    // Facilities table
    html += '<div class="card"><div class="card-title">Your Facilities</div>';

    if (facilities.length === 0) {
        html += '<div class="text-muted" style="padding:12px 0;">No facilities for ' + escapeHtml(label) + '. Build one below.</div>';
    } else {
        html += '<table class="data-table"><thead><tr>' +
            '<th>Status</th>' +
            '<th>Capacity / tick</th>' +
            '<th>Op. Cost / tick</th>' +
            '<th>Actions</th>' +
        '</tr></thead><tbody>';

        $.each(facilities, function(i, f) {
            var isActive = f.state === 'active';
            var opCost = getOperatingCost(tier, f.state);

            html += '<tr>' +
                '<td><span class="badge badge-' + f.state + '">' + f.state + '</span></td>' +
                '<td class="num">' + (isActive ? f.production_capacity : '\u2014') + '</td>' +
                '<td class="num text-muted">' + formatCash(opCost) + '</td>' +
                '<td>' +
                    (isActive
                        ? '<button class="btn btn-small" onclick="facilityAction(\'idle\',' + f.id + ')">Idle</button> '
                        : '<button class="btn btn-small btn-primary" onclick="facilityAction(\'activate\',' + f.id + ')">Activate</button> ') +
                    '<button class="btn btn-small btn-danger" onclick="facilityAction(\'downsize\',' + f.id + ')">Sell</button>' +
                '</td></tr>';
        });
        html += '</tbody></table>';
    }
    html += '</div>';

    // Build new facility
    var buildCost = getBuildCost(resource);
    var canAfford = State.player && State.player.cash >= buildCost;
    html += '<div class="card"><div class="card-title">Build New Facility</div>' +
        '<div class="flex-between">' +
            '<div>' +
                '<div>Build cost: <strong class="mono">' + formatCash(buildCost) + '</strong></div>' +
                '<div class="text-muted" style="margin-top:4px;font-size:12px;">' +
                    'Produces ' + getProductionRate(resource) + ' ' + escapeHtml(label) + ' per tick when active' +
                '</div>' +
            '</div>' +
            '<button class="btn btn-primary" onclick="buildFacility(\'' + resource + '\')"' +
                (canAfford ? '' : ' disabled') + '>' +
                'Build' +
            '</button>' +
        '</div></div>';

    $('#tab-production').html(html);
}

function renderProductionGlobalView() {
    var activeFacilities = [];
    var idleFacilities = [];
    var totalCost = 0;

    $.each(State.facilities, function(i, f) {
        if (f.state === 'destroyed') return;
        var tier = getResourceTier(f.resource_name);
        var cost = getOperatingCost(tier, f.state);
        totalCost += cost;
        if (f.state === 'active') activeFacilities.push(f);
        else if (f.state === 'idle') idleFacilities.push(f);
    });

    var allFacilities = [];
    $.each(State.facilities, function(i, f) {
        if (f.state !== 'destroyed') allFacilities.push(f);
    });

    var html = '<div class="card">' +
        '<div class="flex-between">' +
            '<div class="card-title">All Facilities</div>' +
            '<span class="text-muted">Total cost: <strong class="text-danger mono">' + formatCash(totalCost) + '/tick</strong></span>' +
        '</div>' +
        '<div style="margin-bottom:12px;font-size:13px;color:var(--text-muted);">' +
            activeFacilities.length + ' active &nbsp;&middot;&nbsp; ' + idleFacilities.length + ' idle' +
        '</div>';

    if (allFacilities.length === 0) {
        html += '<div class="empty-state">' +
            '<div class="message">No facilities yet. Select a resource from the sidebar to build one.</div>' +
        '</div>';
    } else {
        html += '<table class="data-table"><thead><tr>' +
            '<th>Resource</th>' +
            '<th>Status</th>' +
            '<th>Capacity / tick</th>' +
            '<th>Op. Cost / tick</th>' +
            '<th>Actions</th>' +
        '</tr></thead><tbody>';

        $.each(allFacilities, function(i, f) {
            var label = RESOURCE_LABELS[f.resource_name] || f.resource_name;
            var isActive = f.state === 'active';
            var tier = getResourceTier(f.resource_name);
            var opCost = getOperatingCost(tier, f.state);

            html += '<tr>' +
                '<td><strong style="cursor:pointer;color:var(--accent)" onclick="selectResource(\'' + f.resource_name + '\')">' + escapeHtml(label) + '</strong></td>' +
                '<td><span class="badge badge-' + f.state + '">' + f.state + '</span></td>' +
                '<td class="num">' + (isActive ? f.production_capacity : '\u2014') + '</td>' +
                '<td class="num text-muted">' + formatCash(opCost) + '</td>' +
                '<td>' +
                    (isActive
                        ? '<button class="btn btn-small" onclick="facilityAction(\'idle\',' + f.id + ')">Idle</button> '
                        : '<button class="btn btn-small btn-primary" onclick="facilityAction(\'activate\',' + f.id + ')">Activate</button> ') +
                    '<button class="btn btn-small btn-danger" onclick="facilityAction(\'downsize\',' + f.id + ')">Sell</button>' +
                '</td></tr>';
        });
        html += '</tbody></table>';
    }
    html += '</div>';

    $('#tab-production').html(html);
}

// ── Actions ──────────────────────────────────────────────────────────────

async function buildFacility(resource) {
    try {
        await API.post('/api/v1/production/build', { resource: resource });
        await loadPlayerState();
        showToast('Built ' + (RESOURCE_LABELS[resource] || resource) + ' facility!', 'success');
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function facilityAction(action, facilityId) {
    if (action === 'downsize') {
        if (!confirm('Sell this facility? You will receive 40% of the build cost back. This cannot be undone.')) return;
    }
    try {
        await API.post('/api/v1/production/' + action, { facility_id: facilityId });
        await loadPlayerState();
        if (action === 'downsize') showToast('Facility sold', 'info');
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

function getBuildCost(resource) {
    var tier = getResourceTier(resource);
    if (State.buildCosts && State.buildCosts[tier] !== undefined) {
        return State.buildCosts[tier];
    }
    var defaults = { raw: 100, intermediate: 300, advanced_intermediate: 800, consumer: 2000 };
    return defaults[tier] || 100;
}

function getOperatingCost(tier, state) {
    var baseCost = 2.0;
    if (State.opCosts && State.opCosts[tier] !== undefined) {
        baseCost = State.opCosts[tier];
    } else {
        var defaults = { raw: 2, intermediate: 5, advanced_intermediate: 12, consumer: 30 };
        baseCost = defaults[tier] || 2;
    }
    if (state === 'idle') {
        var multiplier = (State.config && State.config['facility.idle_cost_multiplier']) || 0.30;
        return baseCost * multiplier;
    }
    return baseCost;
}

function getProductionRate(resource) {
    if (State.resources) {
        for (var i = 0; i < State.resources.length; i++) {
            if (State.resources[i].name === resource) {
                return State.resources[i].default_production_tick || '?';
            }
        }
    }
    return '?';
}

function getRecipeDescription(resource) {
    if (!State.recipes || !State.recipes[resource]) return '';
    var recipe = State.recipes[resource];
    var inputs = recipe.inputs;
    if (!inputs) return 'No inputs required';

    var parts = [];
    $.each(inputs, function(name, qty) {
        parts.push((RESOURCE_LABELS[name] || name) + ' x' + qty);
    });
    return parts.join(', ');
}
