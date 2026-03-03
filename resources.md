# Trade Empire — Resource Reference

This document lists all resources, recipes, production chains, and market goods.

---

## Raw Resources

Extracted or harvested from land. Require **Farm** or **Factory** land type.

| Resource | Land Type | Description |
|----------|-----------|-------------|
| **Wheat** | Farm | Staple grain crop |
| **Cotton** | Farm | Textile fiber crop |
| **Rubber** | Farm | Rubber tree plantation |
| **Timber** | Farm | Forestry / logging |
| **Iron** | Factory | Iron ore mining |
| **Copper** | Factory | Copper ore mining |
| **Gold** | Factory | Precious metal mining |
| **Bauxite** | Factory | Aluminium ore mining |
| **Coal** | Factory | Coal mining |
| **Lithium** | Factory | Lithium mining (battery material) |
| **Silicon** | Factory | Silicon extraction (electronics) |
| **Petrol** | Factory | Oil extraction / refining |

---

## Intermediate Resources

Processed from raw materials. Require **Factory** land type.

| Resource | Recipe (Inputs) | Description |
|----------|----------------|-------------|
| **Bread** | 2 Wheat | Baked goods |
| **Canned Food** | 1 Wheat + 1 Steel | Preserved food products |
| **Steel** | 1 Iron + 1 Coal | Structural metal |
| **Aluminium** | 1 Bauxite + 1 Coal | Lightweight metal |
| **Plastic** | 1 Petrol | Polymer material |
| **Rubber Compound** | 1 Rubber + 1 Petrol | Processed rubber for tires/parts |
| **Glass** | 2 Silicon | Transparent material |
| **Fabric** | 2 Cotton | Textile material |
| **Lumber** | 1 Timber | Processed wood |
| **Battery** | 2 Lithium + 1 Copper | Energy storage |
| **Circuit** | 3 Plastic + 1 Copper + 1 Gold + 1 Silicon | Electronic component |

---

## Consumer Goods (Market Goods)

Final products sold through **Shops**. Require Shop land type for retail.

| Resource | Recipe (Inputs) | Base Price | Demand Cycle |
|----------|----------------|-----------|--------------|
| **Bread** (retail) | *Direct from intermediate* | $3.50 | 5 min |
| **Canned Food** (retail) | *Direct from intermediate* | $5.00 | 5 min |
| **Clothing** | 3 Fabric + 1 Rubber Compound | $120.00 | 10 min |
| **Bicycle** | 2 Steel + 2 Rubber Compound + 1 Aluminium | $50.00 | 10 min |
| **Furniture** | 5 Lumber + 2 Steel + 1 Fabric | $500.00 | 15 min |
| **Laptop** | 5 Circuit + 2 Aluminium + 1 Glass + 1 Battery | $899.00 | 20 min |
| **Phone** | 3 Circuit + 1 Glass + 1 Battery + 1 Aluminium | $899.00 | 20 min |
| **Jewelry** | 3 Gold + 1 Copper + 1 Glass | $2,500.00 | 30 min |
| **Car** | 10 Steel + 4 Rubber Compound + 2 Circuit + 5 Petrol + 1 Glass + 1 Battery | $35,000.00 | 45 min |

---

## Full Supply Chain Diagram

```
RAW MATERIALS              INTERMEDIATES                  CONSUMER GOODS
=============              =============                  ==============

Wheat ─────────────────── Bread ──────────────────────── [Bread] (Shop)
  │
  └─── (+Steel) ──────── Canned Food ─────────────────── [Canned Food] (Shop)

Iron ──── (+Coal) ─────── Steel ──┬─── (+Rubber Comp) ── Bicycle
                                  ├─── (+Lumber,Fabric)── Furniture
Coal ─────────────────────────┘   ├─── (+Rubber Comp) ── Car
                                  └─── (Canned Food) ─── [Canned Food]

Copper ───────────────────────────┬─── (Circuit) ──────── Phone, Laptop, Car
                                  ├─── (Battery) ──────── Phone, Laptop, Car
                                  └─── (Jewelry) ──────── [Jewelry]

Gold ─────────────────────────────┬─── (Circuit)
                                  └─── (Jewelry) ──────── [Jewelry]

Petrol ────────────────── Plastic ─── (Circuit)
  │
  └─── (+Rubber) ──────── Rubber Compound ─┬──────────── Clothing
                                           ├──────────── Bicycle
                                           └──────────── Car

Rubber ───────────────────────────┘

Cotton ────────────────── Fabric ──┬──────────────────── Clothing
                                   └──────────────────── Furniture

Timber ────────────────── Lumber ──────────────────────── Furniture

Silicon ───────────────── Glass ───┬──────────────────── Phone, Laptop
  │                                └──────────────────── Jewelry, Car
  └─── (Circuit) ─────────────────────────────────────── Phone, Laptop, Car

Lithium ── (+Copper) ──── Battery ─┬──────────────────── Phone
                                   ├──────────────────── Laptop
                                   └──────────────────── Car

Bauxite ── (+Coal) ────── Aluminium ┬─────────────────── Bicycle
                                    ├─────────────────── Phone
                                    └─────────────────── Laptop
```

---

## Resource Properties

### Perishable Resources (Decay)

| Resource | Decay Rate (per 60 ticks) |
|----------|--------------------------|
| Wheat | 1.0% |
| Cotton | 0.5% |
| Rubber | 0.3% |
| Petrol | 0.5% |
| Bread | 2.0% |
| Canned Food | 0.2% |

All other resources are **non-perishable** (metals, circuits, glass, lumber, etc.).

### Storage Limits (Default)

| Tier | Default Capacity per Location |
|------|-------------------------------|
| Raw | 5,000 units |
| Intermediate | 2,000 units |
| Consumer | 500 units |

### Transport Weight Modifiers

| Tier | Weight Modifier | Rationale |
|------|----------------|-----------|
| Raw | 1.0x | Heavy, bulky commodities |
| Intermediate | 0.7x | Processed, more compact |
| Consumer | 0.5x | High-value, compact products |

---

## Potential Future Resources

These resources could be added in future updates to deepen the economy:

### Additional Raw Resources
| Resource | Land Type | Enables |
|----------|-----------|---------|
| **Cattle** | Farm | Leather, Beef |
| **Sugarcane** | Farm | Sugar, Biofuel |
| **Rare Earth** | Factory | Advanced electronics |
| **Natural Gas** | Factory | Chemicals, energy |
| **Sand** | Factory | Concrete, glass variants |
| **Cocoa** | Farm | Chocolate, luxury food |

### Additional Intermediates
| Resource | Recipe | Enables |
|----------|--------|---------|
| **Leather** | 1 Cattle | Clothing variant, Luxury goods |
| **Concrete** | 2 Sand + 1 Coal | Buildings, infrastructure |
| **Chemicals** | 1 Natural Gas + 1 Petrol | Pharmaceuticals, fertilizer |
| **Fertilizer** | 1 Chemicals | Farm efficiency boost |
| **Beef** | 1 Cattle + 1 Wheat | Premium food |
| **Chocolate** | 1 Cocoa + 1 Sugarcane | Luxury food |
| **Wire** | 1 Copper | Electronics, infrastructure |
| **Paint** | 1 Chemicals + 1 Petrol | Vehicles, furniture finishing |

### Additional Consumer Goods
| Resource | Recipe | Base Price |
|----------|--------|-----------|
| **Luxury Watch** | 2 Gold + 1 Circuit + 1 Glass | $5,000 |
| **Television** | 4 Circuit + 2 Glass + 1 Plastic + 1 Aluminium | $1,200 |
| **Refrigerator** | 3 Steel + 2 Circuit + 1 Plastic | $800 |
| **Sneakers** | 2 Rubber Compound + 1 Fabric + 1 Leather | $180 |
| **Chocolate Box** | 2 Chocolate + 1 Plastic | $25 |
| **Steak Dinner** | 1 Beef + 1 Wheat | $45 |
| **Electric Car** | 8 Steel + 4 Rubber Compound + 5 Circuit + 3 Battery + 1 Glass | $55,000 |
| **Pharmaceutical** | 3 Chemicals + 1 Glass + 1 Plastic | $350 |
| **Solar Panel** | 4 Silicon + 2 Glass + 1 Aluminium + 1 Wire | $2,000 |
| **Drone** | 2 Circuit + 1 Battery + 1 Plastic + 1 Aluminium | $1,500 |
