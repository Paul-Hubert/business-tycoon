/**
 * sidebar.js — Collapsible left sidebar listing all resources by tier.
 * Clicking a resource sets State.selectedResource and refreshes the active tab.
 */

// Resource grouping — order matches ResourceRegistry.java
const RESOURCE_TIERS = {
    raw: ['wheat','iron','copper','gold','petrol','cotton','timber','lithium','rubber','silicon','bauxite','coal'],
    intermediate: ['bread','steel','plastic','fabric','lumber','glass','aluminium','rubber_compound','canned_food'],
    advanced_intermediate: ['circuit','battery'],
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

const TIER_LABELS = {
    raw: 'Raw Materials',
    intermediate: 'Intermediate',
    advanced_intermediate: 'Advanced',
    consumer: 'Consumer Goods'
};

function initSidebar() {
    var sidebar = $('#sidebar-nav');
    sidebar.html('');

    $.each(RESOURCE_TIERS, function(tier, names) {
        var tierLabel = TIER_LABELS[tier];
        var html = '<div class="sidebar-section">' +
            '<div class="sidebar-tier-header" data-tier="' + tier + '">' +
                '<span class="tier-label">' + tierLabel + '</span>' +
                '<span class="collapse-icon">\u25BE</span>' +
            '</div>' +
            '<ul class="resource-list" id="tier-' + tier + '">';

        $.each(names, function(i, name) {
            html += '<li class="resource-item" data-resource="' + name + '">' +
                '<span class="resource-name">' + RESOURCE_LABELS[name] + '</span>' +
                '<span class="resource-stock" id="stock-' + name + '">\u2014</span>' +
            '</li>';
        });

        html += '</ul></div>';
        sidebar.append(html);
    });

    // Add "All Resources" deselect option at top
    sidebar.prepend('<div class="sidebar-deselect" id="sidebar-show-all">All Resources</div>');

    // Click resource to select
    sidebar.on('click', '.resource-item', function() {
        var resource = $(this).data('resource');
        selectResource(resource);
    });

    // Toggle tier collapse
    sidebar.on('click', '.sidebar-tier-header', function() {
        var tier = $(this).data('tier');
        var list = $('#tier-' + tier);
        list.toggleClass('collapsed');
        $(this).find('.collapse-icon').text(list.hasClass('collapsed') ? '\u25B8' : '\u25BE');
    });

    // Click "All Resources"
    $('#sidebar-show-all').on('click', function() {
        selectResource(null);
    });
}

function selectResource(resourceName) {
    State.selectedResource = resourceName;

    // Update sidebar highlight
    $('.resource-item').removeClass('selected');
    if (resourceName) {
        $('.resource-item[data-resource="' + resourceName + '"]').addClass('selected');
    }

    // Refresh the active tab
    refreshActiveTab();
}

function updateSidebarStocks() {
    // Update all resource stock values
    $.each(RESOURCE_TIERS, function(tier, names) {
        $.each(names, function(i, name) {
            var qty = State.inventory[name] || 0;
            var el = $('#stock-' + name);
            var text = qty > 0 ? formatNumber(qty) : '\u2014';
            el.text(text);

            // Color coding
            el.removeClass('stock-high stock-low stock-critical');
            if (qty > 100) el.addClass('stock-high');
            else if (qty > 0 && qty <= 20) el.addClass('stock-critical');
            else if (qty > 0 && qty <= 50) el.addClass('stock-low');
        });
    });
}

function getResourceTier(resource) {
    if (RESOURCE_TIERS.raw.indexOf(resource) !== -1) return 'raw';
    if (RESOURCE_TIERS.intermediate.indexOf(resource) !== -1) return 'intermediate';
    if (RESOURCE_TIERS.advanced_intermediate.indexOf(resource) !== -1) return 'advanced_intermediate';
    if (RESOURCE_TIERS.consumer.indexOf(resource) !== -1) return 'consumer';
    return 'raw';
}
