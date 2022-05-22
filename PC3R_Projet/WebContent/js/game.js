const refresh_rate = 1000; // milliseconds

function error(e) {
    console.log(e);
}


function refresh() {
    $.getJSON("/update", {}, reload, "json").fail(error);
}

// Production

function addProduction(resource) {
    $.post("/action", {"action":"addProduction", resource}, reload, "json").fail(error);
}



// Offers

function search() {
	let resource = $("#search [name='resource']").val();
	let buy = $("#search [name='achat_vente']:checked").val();
	let price = $("#search [name='price']").val();
	let quantity = $("#search [name='quantity']").val();
	
	console.log(buy);
	
	$.post("/action", {"action":"search", resource, buy, price, quantity}, reload, "json").fail(error);
}

function publish() {
	let resource = $("#search [name='resource']").val();
	let buy = $("#search [name='achat_vente']:checked").val();
	let price = $("#search [name='price']").val();
	let quantity = $("#search [name='quantity']").val();
	
	
	$.post("/action", {"action":"publish", resource, buy, price, quantity}, reload, "json").fail(error);
}


function deleteOffer(e) {
	console.log(e, $(e));
	let id = $(e).attr("for");
	
	$.post("/action", {"action":"delete", id}, reload, "json").fail(error);
	
	$(e.target).remove();
}

function reload(data) {
    
    $("#money").html(money(data.money));

    for(let i in data.resources) {
    	let res = data.resources[i];
        $("#" + res.name + " .count").html(res.count);
        $("#" + res.name + " .price").html(money(res.price));
        $("#" + res.name + " .production").html(res.production);
        $("#" + res.name + " .production-cost").html(money(res.production_cost));
        $("#" + res.name + " .research").html(res.research);
        $("#" + res.name + " .research-cost").attr('value', res.research_cost);
    }
    
    let offers = $("#offer-list");
	offers.empty();
	
	let template = $("#template");
	
	for(let i in data.offers) {
		let offer = data.offers[i];
		
		let offer_id = offer.id;
		
		let offer_card = template.clone();
		offer_card.appendTo(offers);
		
		offer_card.attr("id", "modify");
		
		offer_card.attr("buy", offer.buy);
		
		$("#modify .price").html(money(offer.price));
		
		$("#modify .quantity").html(offer.quantity);
		
		let deleteButton = $("#modify .delete");
		
		if(offer.user_id == data.user_id) {
			$("#modify .offerer").html(data.user);
			deleteButton.attr("for", offer_id);
			//delete.on("click", deleteOffer);
		} else {
			$("#modify .offerer").html("#"+offer.user_id);
			deleteButton.attr("hidden", true);
		}
		
		offer_card.attr("id", "offer" + offer_id);
		//offer_card.removeAttr("hidden");
		
	}
	
	
	updateCurrencySpy();

}




window.addEventListener("load", (e) => {
	updateCurrencySpy();
    $.ajaxSetup({ cache: false });
    setInterval(refresh, refresh_rate);
});

/*
$('.auto-submit').submit(function(e){
    e.preventDefault();
    
    $.ajax({
        type: 'POST',
        cache: false,
        url: $(this).attr('action'),
        data: 'action-type='+$(this).attr('action-type')+'&'+$(this).serialize(), 
        success: update
    }).fail(error);
    
    const form = e.target;
	let data = new FormData(form);
	data.set("action-type", "publish");
    fetch(form.action, {
        method: form.method,
        body: data
    }).then(update).catch(error);
});
*/
    
