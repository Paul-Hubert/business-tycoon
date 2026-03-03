<%@ page import="data.*"%>
<%@ page import="simulation.*"%>

<!DOCTYPE html>
<html lang="en">
	<head>
		<title>Trade Empire</title>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<link rel="preconnect" href="https://fonts.googleapis.com">
		<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
		<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
		<link href="/css/variables.css" rel="stylesheet">
		<link href="/css/layout.css" rel="stylesheet">
		<link href="/css/components.css" rel="stylesheet">
		<link href="/css/animations.css" rel="stylesheet">
		<link href="/css/game.css" rel="stylesheet">
		<link href="/css/common.css" rel="stylesheet">
		<script src="https://code.jquery.com/jquery-3.6.0.js"></script>
		<script src="/js/common.js"></script>
		<script src="/js/game.js"></script>
	</head>

	<%
	User user = (User) request.getAttribute("user");
	%>

	<body>
		<div class="page fade-in">

			<!-- ========== TOP BAR ========== -->
			<header class="topbar">
				<a class="topbar__brand" href="#">
					<span class="topbar__brand-icon">&#127981;</span>
					<span>Trade Empire</span>
				</a>

				<div class="topbar__stats">
					<div class="topbar__stat">
						<span class="topbar__stat-label">Balance</span>
						<span class="topbar__stat-value topbar__stat-value--money font-mono">$<span id="money">0</span></span>
					</div>
					<div class="topbar__stat">
						<span class="topbar__stat-label">&#x1f451;</span>
						<span id="firstUser" class="topbar__stat-value">-</span>
						<span class="text-muted font-mono">$<span id="firstMoney">0</span></span>
					</div>
				</div>

				<div class="topbar__actions">
					<span id="username" class="text-secondary" style="font-size: var(--text-sm);"><%= user.name %></span>
					<form method="post" action="/" style="margin:0;">
						<button type="submit" name="action" value="logout" class="btn btn--ghost btn--sm">Log out</button>
					</form>
				</div>
			</header>

			<!-- ========== MAIN LAYOUT ========== -->
			<div class="main">

				<!-- ========== SIDEBAR ========== -->
				<aside class="sidebar">

					<!-- Nav tabs -->
					<div class="sidebar__nav">
						<div class="sidebar__nav-item sidebar__nav-item--active" data-view="production" onclick="switchView('production')">
							&#9881; Production
						</div>
						<div class="sidebar__nav-item" data-view="market" onclick="switchView('market')">
							&#128200; Market
						</div>
					</div>

					<div class="sidebar__divider"></div>

					<!-- Raw Resources -->
					<div class="sidebar__section">
						<div class="sidebar__heading">Raw Materials</div>
						<div class="sidebar__item sidebar__item--active" data-resource="wheat" onclick="selectResource('wheat')">
							<span class="sidebar__item-icon">&#127806;</span>
							<span class="sidebar__item-name">Wheat</span>
							<span class="sidebar__item-count" id="sidebar-wheat-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="iron" onclick="selectResource('iron')">
							<span class="sidebar__item-icon">&#9935;</span>
							<span class="sidebar__item-name">Iron</span>
							<span class="sidebar__item-count" id="sidebar-iron-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="copper" onclick="selectResource('copper')">
							<span class="sidebar__item-icon">&#128310;</span>
							<span class="sidebar__item-name">Copper</span>
							<span class="sidebar__item-count" id="sidebar-copper-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="gold" onclick="selectResource('gold')">
							<span class="sidebar__item-icon">&#129351;</span>
							<span class="sidebar__item-name">Gold</span>
							<span class="sidebar__item-count" id="sidebar-gold-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="petrol" onclick="selectResource('petrol')">
							<span class="sidebar__item-icon">&#128738;</span>
							<span class="sidebar__item-name">Petrol</span>
							<span class="sidebar__item-count" id="sidebar-petrol-count">0</span>
						</div>
					</div>

					<div class="sidebar__divider"></div>

					<!-- Crafted Resources -->
					<div class="sidebar__section">
						<div class="sidebar__heading">Crafted</div>
						<div class="sidebar__item" data-resource="bread" onclick="selectResource('bread')">
							<span class="sidebar__item-icon">&#127838;</span>
							<span class="sidebar__item-name">Bread</span>
							<span class="sidebar__item-count" id="sidebar-bread-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="steel" onclick="selectResource('steel')">
							<span class="sidebar__item-icon">&#128297;</span>
							<span class="sidebar__item-name">Steel</span>
							<span class="sidebar__item-count" id="sidebar-steel-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="plastic" onclick="selectResource('plastic')">
							<span class="sidebar__item-icon">&#128230;</span>
							<span class="sidebar__item-name">Plastic</span>
							<span class="sidebar__item-count" id="sidebar-plastic-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="circuit" onclick="selectResource('circuit')">
							<span class="sidebar__item-icon">&#128268;</span>
							<span class="sidebar__item-name">Circuit</span>
							<span class="sidebar__item-count" id="sidebar-circuit-count">0</span>
						</div>
					</div>

					<div class="sidebar__divider"></div>

					<!-- Consumer Goods -->
					<div class="sidebar__section">
						<div class="sidebar__heading">Consumer Goods</div>
						<div class="sidebar__item" data-resource="car" onclick="selectResource('car')">
							<span class="sidebar__item-icon">&#128663;</span>
							<span class="sidebar__item-name">Car</span>
							<span class="sidebar__item-count" id="sidebar-car-count">0</span>
						</div>
						<div class="sidebar__item" data-resource="phone" onclick="selectResource('phone')">
							<span class="sidebar__item-icon">&#128241;</span>
							<span class="sidebar__item-name">Phone</span>
							<span class="sidebar__item-count" id="sidebar-phone-count">0</span>
						</div>
					</div>

				</aside>

				<!-- ========== CONTENT AREA ========== -->
				<main class="content">

					<%
					if (request.getAttribute("error") != null) {
					%>
					<div class="alert alert--error" role="alert">
						${requestScope.error}
					</div>
					<%
					}
					%>

					<!-- ===== PRODUCTION VIEW ===== -->
					<div id="view-production">
						<div class="content__header">
							<div>
								<h1 class="content__title">Production Dashboard</h1>
								<p class="content__subtitle">Manage your resource production and research investments</p>
							</div>
						</div>

						<div class="resource-grid">
							<%
							for (Resource res : Resource.values()) {
								ResourceProduction rp = user.getProduction().get(res);
								Recipe recipe = Crafting.recipes.get(res);

								String icon = "";
								String category = "";
								switch(res.name()) {
									case "wheat":   icon = "&#127806;"; category = "raw"; break;
									case "iron":    icon = "&#9935;";   category = "raw"; break;
									case "copper":  icon = "&#128310;"; category = "raw"; break;
									case "gold":    icon = "&#129351;"; category = "raw"; break;
									case "petrol":  icon = "&#128738;"; category = "raw"; break;
									case "bread":   icon = "&#127838;"; category = "crafted"; break;
									case "steel":   icon = "&#128297;"; category = "crafted"; break;
									case "plastic": icon = "&#128230;"; category = "crafted"; break;
									case "circuit": icon = "&#128268;"; category = "crafted"; break;
									case "car":     icon = "&#128663;"; category = "consumer"; break;
									case "phone":   icon = "&#128241;"; category = "consumer"; break;
								}
							%>

							<div id="<%= res %>" class="card card--interactive" data-resource="<%= res %>">
								<div class="card__header">
									<span class="card__icon"><%= icon %></span>
									<span class="card__title"><%= res %></span>
									<span class="badge badge--<%= category %>"><%= category %></span>
								</div>

								<% if(recipe != null) { %>
								<div class="card__recipe">
									<span><%= recipe.getInfo() %></span>
									<span class="card__recipe-arrow">&rarr;</span>
									<span>1 <%= res %></span>
								</div>
								<% } %>

								<div class="card__body">
									<div class="card__stat">
										<span class="card__stat-label">Stock</span>
										<span class="card__stat-value"><span class="count">0</span></span>
									</div>
									<div class="card__stat">
										<span class="card__stat-label">Price</span>
										<span class="card__stat-value card__stat-value--money">$<span class="price">0</span></span>
									</div>
									<div class="card__stat">
										<span class="card__stat-label">Production</span>
										<span class="card__stat-value card__stat-value--positive">+<span class="production">0</span>/tick</span>
									</div>
									<div class="card__stat">
										<span class="card__stat-label">Efficiency</span>
										<span class="card__stat-value"><span class="research">10000</span>%</span>
									</div>
								</div>

								<div class="card__footer">
									<div class="form-group" style="margin-bottom: var(--space-2);">
										<label class="form-label">Research $/tick</label>
										<input type="number" step="0.01" class="form-input form-input--money research-cost"
											name="invest" onchange="changeResearch(<%= res.getID() %>, this)" value="">
									</div>
									<button onclick="addProduction(<%= res.getID() %>)" type="button" class="btn btn--primary btn--sm btn--block">
										Add Production &mdash; $<span class="production-cost">0</span>
									</button>
								</div>
							</div>

							<%
							}
							%>
						</div>
					</div>

					<!-- ===== MARKET VIEW ===== -->
					<div id="view-market" style="display:none;">
						<div class="content__header">
							<div>
								<h1 class="content__title">Marketplace</h1>
								<p class="content__subtitle">Buy and sell resources with other players</p>
							</div>
						</div>

						<div class="split">
							<!-- Market Form -->
							<div class="panel">
								<div class="panel__header">
									<h2 class="panel__title">Create Order</h2>
								</div>

								<form id="search" method="post" action="/action">
									<div class="form-group">
										<label class="form-label">Order Type</label>
										<div class="radio-group">
											<div class="radio-group__option">
												<input type="radio" id="radio-buy" name="achat_vente" value="true" onchange="search()" required checked>
												<label class="radio-group__label" for="radio-buy">Buy</label>
											</div>
											<div class="radio-group__option">
												<input type="radio" id="radio-sell" name="achat_vente" value="false" onchange="search()">
												<label class="radio-group__label" for="radio-sell">Sell</label>
											</div>
										</div>
									</div>

									<div class="form-group">
										<label class="form-label">Resource</label>
										<select class="form-select" name="resource" onchange="search()" required id="resource-select">
											<option value="">Loading resources...</option>
										</select>
									</div>

									<div class="form-row">
										<div class="form-group">
											<label class="form-label">Price ($)</label>
											<input type="text" class="form-input form-input--money" value="1" name="price" required>
										</div>
										<div class="form-group">
											<label class="form-label">Quantity</label>
											<input type="number" step="1" class="form-input" value="10" name="quantity" required>
										</div>
									</div>

									<div class="form-row gap-3 mt-3">
										<button type="button" onclick="search()" class="btn btn--secondary flex-1">Search</button>
										<button type="button" onclick="publish()" class="btn btn--primary flex-1">Publish Offer</button>
									</div>
								</form>
							</div>

							<!-- Offers List -->
							<div class="panel">
								<div class="panel__header">
									<h2 class="panel__title">Open Offers</h2>
								</div>

								<!-- Hidden template for offer items -->
								<ul hidden>
									<li id="template" class="offer-item">
										<span class="offer-item__user offerer"></span>
										<span class="offer-item__price">$<span class="price">0</span></span>
										<span class="offer-item__quantity"><span class="quantity">0</span> units</span>
										<span class="offer-item__actions">
											<button type="button" onclick="deleteOffer(this)" class="delete btn btn--danger btn--sm">Remove</button>
										</span>
									</li>
								</ul>

								<ul id="offer-list" class="offer-list">
								</ul>
							</div>
						</div>
					</div>

				</main>
			</div>

			<!-- Toast container -->
			<div class="toast-container" id="toast-container"></div>

		</div>
	</body>
</html>
