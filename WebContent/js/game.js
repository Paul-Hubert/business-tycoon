/* ============================================================
   TRADE EMPIRE — Game Logic
   API calls, DOM updates, view switching, toasts, animations.
   ============================================================ */

const refresh_rate = 2000; // milliseconds

// Previous values for detecting changes
let prevValues = {};

function error(e) {
    console.log(e);
    showToast("Something went wrong. Check console.", "error");
}


// ============================================================
// API CALLS
// ============================================================

function refresh() {
    $.getJSON("/update", {}, reload, "json").fail(error);
}

// Production
function addProduction(resource) {
    $.post("/action", {"action":"addProduction", resource}, function(data) {
        reload(data);
        showToast("Production added!", "success");
    }, "json").fail(error);
}

function changeResearch(resource, element) {
    $.post("/action", {"action":"changeResearch", resource, "cost":element.value}, reload, "json").fail(error);
}

// Offers
function search() {
    let resource = $("#search [name='resource']").val();
    let buy = $("#search [name='achat_vente']:checked").val();
    let price = $("#search [name='price']").val();
    let quantity = $("#search [name='quantity']").val();

    $.post("/action", {"action":"search", resource, buy, price, quantity}, reload, "json").fail(error);
}

function publish() {
    let resource = $("#search [name='resource']").val();
    let buy = $("#search [name='achat_vente']:checked").val();
    let price = $("#search [name='price']").val();
    let quantity = $("#search [name='quantity']").val();

    $.post("/action", {"action":"publish", resource, buy, price, quantity}, function(data) {
        reload(data);
        showToast("Offer published!", "gold");
    }, "json").fail(error);
}

function deleteOffer(e) {
    let id = $(e).attr("for");
    $.post("/action", {"action":"delete", id}, reload, "json").fail(error);
}


// ============================================================
// DOM UPDATE
// ============================================================

function reload(data) {

    // Top bar stats
    animateValue("money", formatMoney(data.money));
    $("#firstUser").html(data.topPlayer);
    $("#firstMoney").html(formatMoney(data.topMoney));

    // Resource cards + sidebar counts
    for (let i in data.resources) {
        let res = data.resources[i];
        let card = $("#" + res.name);

        // Detect value changes and flash
        let prevCount = prevValues[res.name + "_count"];
        let newCount = res.count;

        updateWithFlash(card.find(".count"), formatNumber(newCount), prevCount, newCount);
        card.find(".price").html(formatMoney(res.price));
        card.find(".production").html(formatNumber(res.production));
        card.find(".production-cost").html(formatMoney(res.production_cost));
        card.find(".research").html(formatMoney(res.research + 10000));
        card.find(".research-cost").attr("value", money(res.research_cost));

        // Update sidebar counts
        let sidebarCount = $("#sidebar-" + res.name + "-count");
        if (sidebarCount.length) {
            sidebarCount.html(abbreviate(newCount));
        }

        // Pulse if actively producing
        if (res.production > 0) {
            card.addClass("pulse-producing");
        } else {
            card.removeClass("pulse-producing");
        }

        prevValues[res.name + "_count"] = newCount;
    }

    // Offers
    let offers = $("#offer-list");
    offers.empty();

    let template = $("#template");

    for (let i in data.offers) {
        let offer = data.offers[i];
        let offer_id = offer.id;

        let offer_card = template.clone();
        offer_card.appendTo(offers);
        offer_card.attr("id", "modify");
        offer_card.attr("buy", offer.buy);
        offer_card.removeAttr("hidden");

        $("#modify .price").html(formatMoney(offer.price));
        $("#modify .quantity").html(formatNumber(offer.quantity));
        $("#modify .offerer").html(offer.user_name);

        let deleteButton = $("#modify .delete");
        if (offer.user_id == data.user_id) {
            deleteButton.attr("for", offer_id);
        } else {
            deleteButton.attr("hidden", true);
        }

        offer_card.attr("id", "offer" + offer_id);
    }

    updateCurrencySpy();
}


// ============================================================
// VALUE CHANGE ANIMATION
// ============================================================

function updateWithFlash(element, formattedValue, oldVal, newVal) {
    element.html(formattedValue);
    if (oldVal === undefined) return;
    if (newVal > oldVal) {
        flashElement(element, "flash-positive");
    } else if (newVal < oldVal) {
        flashElement(element, "flash-negative");
    }
}

function flashElement(el, className) {
    el.removeClass("flash-positive flash-negative");
    // Force reflow to restart animation
    void el[0].offsetWidth;
    el.addClass(className);
}

function animateValue(id, newText) {
    let el = $("#" + id);
    let oldText = el.html();
    el.html(newText);
    if (oldText !== newText && oldText !== "0") {
        el.removeClass("money-tick");
        void el[0].offsetWidth;
        el.addClass("money-tick");
    }
}


// ============================================================
// VIEW SWITCHING
// ============================================================

function switchView(view) {
    // Toggle view panels
    $("#view-production").toggle(view === "production");
    $("#view-market").toggle(view === "market");

    // Update nav active state
    $(".sidebar__nav-item").removeClass("sidebar__nav-item--active");
    $(".sidebar__nav-item[data-view='" + view + "']").addClass("sidebar__nav-item--active");
}

function selectResource(name) {
    $(".sidebar__item").removeClass("sidebar__item--active");
    $(".sidebar__item[data-resource='" + name + "']").addClass("sidebar__item--active");

    // Scroll card into view
    let card = $(".card[data-resource='" + name + "']");
    if (card.length) {
        card[0].scrollIntoView({ behavior: "smooth", block: "center" });

        // Brief highlight
        card.addClass("card--active");
        setTimeout(function() { card.removeClass("card--active"); }, 1200);
    }
}


// ============================================================
// TOAST NOTIFICATIONS
// ============================================================

function showToast(message, type) {
    type = type || "success";
    let icons = {
        "success": "&#10003;",
        "error": "&#10007;",
        "gold": "&#9733;"
    };

    let toast = $("<div>")
        .addClass("toast toast--" + type + " toast-enter")
        .html(
            '<span class="toast__icon">' + (icons[type] || "") + '</span>' +
            '<span class="toast__message">' + message + '</span>' +
            '<button class="toast__close" onclick="dismissToast(this)">&times;</button>'
        );

    $("#toast-container").append(toast);

    // Auto-dismiss after 3.5s
    setTimeout(function() {
        dismissToast(toast.find(".toast__close")[0]);
    }, 3500);
}

function dismissToast(closeBtn) {
    let toast = $(closeBtn).closest(".toast");
    if (toast.hasClass("toast-exit")) return;
    toast.removeClass("toast-enter").addClass("toast-exit");
    setTimeout(function() { toast.remove(); }, 300);
}


// ============================================================
// NUMBER ABBREVIATION
// ============================================================

function abbreviate(n) {
    if (n >= 1000000) return (n / 1000000).toFixed(1) + "M";
    if (n >= 1000) return (n / 1000).toFixed(1) + "K";
    return String(n);
}


// ============================================================
// INIT
// ============================================================

window.addEventListener("load", function() {
    updateCurrencySpy();
    $.ajaxSetup({ cache: false });
    refresh();
    setInterval(refresh, refresh_rate);
});
