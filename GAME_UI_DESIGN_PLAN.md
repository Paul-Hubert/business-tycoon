# Business Tycoon — Publishable Game & UI Design Plan

## Current State Assessment

The app is a **Java/Tomcat/JSP** multiplayer resource-trading game with:
- 11 resources (wheat, bread, iron, steel, copper, gold, petrol, plastic, circuit, car, phone)
- A crafting/supply-chain system (e.g. car = 10 steel + 1 circuit + 5 petrol)
- Production with research investment for efficiency gains
- A player-to-player marketplace with buy/sell offers
- Auto-sell consumer goods (bread, car, phone) at prices tied to real stock tickers
- A simulation loop running every second server-side
- Bootstrap 5 + jQuery dark-themed frontend — functional but visually bare

**What's missing for a publishable game:** branding, visual hierarchy, onboarding, game feel, mobile support, performance, and polish.

---

## Architecture Decision: Keep Java Backend, New SPA Frontend

The Java backend already exposes JSON via `/update` and handles actions via `/action`. The plan is:

1. **Keep the Java/Tomcat backend** — it works, handles simulation, market, and persistence
2. **Expand the REST API** — make `/update` and `/action` a clean JSON API; remove JSP server-side rendering
3. **Build a new standalone frontend** — a single `index.html` + vanilla JS/CSS (no build tools needed) that calls the API
4. **Ship as a WAR** — frontend static files served from `WebContent/`, backend servlets handle `/api/*`

This avoids a full rewrite while delivering a modern, polished experience.

---

## Phase 1: Visual Identity & Design System

### 1.1 Branding
- **Game name:** "Trade Empire" (or keep "Business Tycoon" — either works)
- **Color palette:**
  - Background: `#0f1923` (deep navy-black)
  - Cards/panels: `#1a2634` with `#243447` borders
  - Primary accent: `#f0b429` (gold — money theme)
  - Success/positive: `#27ae60`
  - Danger/negative: `#e74c3c`
  - Text: `#e8e8e8` primary, `#8899aa` secondary
- **Typography:** Inter (clean, modern, free) for UI; monospace for numbers/money
- **Favicon & logo:** Simple factory/building silhouette icon in gold on dark

### 1.2 Resource Icons
Each resource gets a simple emoji or SVG icon for instant recognition:

| Resource | Icon | Category |
|----------|------|----------|
| Wheat    | 🌾   | Raw      |
| Iron     | ⛏️   | Raw      |
| Copper   | 🔶   | Raw      |
| Gold     | 🥇   | Raw      |
| Petrol   | 🛢️   | Raw      |
| Bread    | 🍞   | Crafted  |
| Steel    | 🔩   | Crafted  |
| Plastic  | 📦   | Crafted  |
| Circuit  | 🔌   | Crafted  |
| Car      | 🚗   | Consumer |
| Phone    | 📱   | Consumer |

### 1.3 Design Tokens (CSS Custom Properties)
Define all colors, spacing, radii, shadows, and transitions as CSS variables so the entire theme is adjustable from one place.

---

## Phase 2: UI Layout & Screens

### 2.1 Login / Signup Screen
```
┌─────────────────────────────────────────┐
│                                         │
│          🏭  TRADE EMPIRE               │
│          Build. Produce. Dominate.       │
│                                         │
│     ┌─────────────────────────────┐     │
│     │  Username                   │     │
│     ├─────────────────────────────┤     │
│     │  Password                   │     │
│     ├──────────────┬──────────────┤     │
│     │   [ Login ]  │  [ Sign Up ] │     │
│     └──────────────┴──────────────┘     │
│                                         │
│   "Compete with players worldwide to    │
│    build the ultimate trade empire"     │
│                                         │
└─────────────────────────────────────────┘
```
- Centered card on a subtle animated gradient background
- Clean, minimal — no clutter

### 2.2 Main Game Screen — Dashboard Layout
```
┌──────────────────────────────────────────────────────────────────┐
│  🏭 Trade Empire    💰 $12,450.00    👑 TopPlayer: $89,200    ⚙ │
├────────────┬─────────────────────────────────────────────────────┤
│            │                                                     │
│  RESOURCES │   PRODUCTION DASHBOARD                              │
│  ─────────-│                                                     │
│            │   ┌─────────┐ ┌─────────┐ ┌─────────┐              │
│  Raw       │   │🌾 Wheat │ │⛏ Iron  │ │🔶Copper │              │
│   🌾 Wheat │   │ 1,240   │ │  830    │ │  415    │              │
│   ⛏ Iron  │   │ +12/s   │ │ +8/s    │ │ +4/s    │              │
│   🔶Copper │   │ [$50]   │ │ [$120]  │ │ [$200]  │              │
│   🥇 Gold  │   └─────────┘ └─────────┘ └─────────┘              │
│   🛢 Petrol│                                                     │
│            │   ┌─────────┐ ┌─────────┐                          │
│  Crafted   │   │🥇 Gold  │ │🛢Petrol │                          │
│   🍞 Bread │   │   52    │ │  310    │                          │
│   🔩 Steel │   │ +2/s    │ │ +6/s    │                          │
│   📦Plastic│   │ [$800]  │ │ [$90]   │                          │
│   🔌Circuit│   └─────────┘ └─────────┘                          │
│            │                                                     │
│  Consumer  │   ... crafted and consumer cards below ...          │
│   🚗 Car   │                                                     │
│   📱 Phone │                                                     │
│            ├─────────────────────────────────────────────────────┤
│            │   MARKETPLACE           [Buy ◉] [Sell ○]           │
│  ──────────│   Resource: [Wheat ▼]   Price: [$___]   Qty: [__]  │
│  MARKET    │   [ Search ]  [ Publish Offer ]                    │
│  RESEARCH  │                                                     │
│            │   ┌─ Offers ──────────────────────────────────┐    │
│            │   │  Player1  │  $2.40  │  500 units  │ [Buy] │    │
│            │   │  Player2  │  $2.55  │  200 units  │ [Buy] │    │
│            │   └───────────────────────────────────────────-┘    │
└────────────┴─────────────────────────────────────────────────────┘
```

**Key layout decisions:**
- **Left sidebar:** Resource navigation grouped by category (Raw / Crafted / Consumer)
- **Main area:** Switches between Production Dashboard, Marketplace, and Research views
- **Top bar:** Always-visible money, top player, and quick stats
- **No scrolling walls of cards** — use a compact grid of resource tiles instead

### 2.3 Resource Detail Panel (click a resource)
```
┌──────────────────────────────────────┐
│  🌾 WHEAT                    Raw     │
│  ─────────────────────────────────── │
│  Stock:       1,240 units            │
│  Production:  12 / tick              │
│  Efficiency:  112%                   │
│  Market Price: $2.40                 │
│                                      │
│  ┌──────────────────────────────┐    │
│  │  Research Investment          │    │
│  │  $[____] / tick               │    │
│  │  ████████░░░░  67% → next +1%│    │
│  └──────────────────────────────┘    │
│                                      │
│  [ + Add Production  $50.00 ]        │
│  [ Sell on Market → ]                │
│                                      │
└──────────────────────────────────────┘
```

- Research shown as a **progress bar** instead of raw numbers
- Clear call-to-action buttons
- Crafted resources also show their recipe visually (ingredient icons + counts)

---

## Phase 3: Game Feel & Interactivity

### 3.1 Animations & Transitions
- **Money counter:** Animate value changes (count up/down smoothly)
- **Stock changes:** Brief green flash (+) or red flash (-) when quantities update
- **Card hover:** Subtle lift/glow effect on resource tiles
- **Production pulse:** Gentle pulse animation on resources actively producing
- **Button feedback:** Scale + color shift on click

### 3.2 Notifications / Toast Messages
- "Production added! Wheat now produces 13/tick" — green toast, top-right, auto-dismiss
- "Offer published!" — gold toast
- "Not enough money!" — red toast with shake
- "Trade completed! Sold 200 wheat to Player2" — gold toast

### 3.3 Sound (Optional, Off by Default)
- Coin clink on purchases
- Subtle chime on production upgrade
- Cash register on successful trade

### 3.4 Number Formatting
- Always show currency with `$X,XXX.XX` format
- Large numbers abbreviated: `$1.2M`, `45.3K units`
- Relative changes shown inline: `+12/tick`, `-$500.00`

---

## Phase 4: Gameplay Improvements

### 4.1 Onboarding / Tutorial
A simple 5-step guided overlay for new players:
1. "Welcome! You start with $1,000. Let's build your empire."
2. "Click **Wheat** to see your first resource. Hit **Add Production** to start producing."
3. "Resources accumulate every second. Watch your wheat grow!"
4. "When you have enough resources, you can craft advanced goods like **Bread** (2 Wheat → 1 Bread)."
5. "Sell goods on the **Marketplace** to earn money. Compete to become the top player!"

### 4.2 Supply Chain Visualization
Show a simple tree/flow diagram on the Research tab:
```
  Wheat ──→ Bread ($$$)
  Iron  ──→ Steel ──┐
  Petrol ──→ Plastic ─┤──→ Circuit ──┬──→ Car ($$$$$)
  Copper ─────────────┤             │
  Gold   ─────────────┘             └──→ Phone ($$$$)
```
This helps players understand what to invest in and plan strategy.

### 4.3 Statistics & History
- Simple line chart showing money over time (last 50 ticks, stored client-side)
- Production totals per resource
- Total trades completed

### 4.4 Leaderboard
Replace the single "top player" with a top-5 mini-leaderboard in the sidebar, showing rank, name, and net worth.

---

## Phase 5: Technical Improvements

### 5.1 API Cleanup
- `GET /api/state` — returns full player state (replaces `/update`)
- `POST /api/action` — handles all actions (keeps current pattern but returns proper HTTP status codes)
- `GET /api/leaderboard` — returns top N players
- `GET /api/market/{resource}` — returns current offers for a resource
- Proper JSON error responses instead of silent failures

### 5.2 Frontend Architecture
```
WebContent/
  index.html          ← Single entry point
  css/
    variables.css     ← Design tokens
    layout.css        ← Grid/flexbox structure
    components.css    ← Cards, buttons, forms, toasts
    animations.css    ← Keyframes and transitions
  js/
    app.js            ← Main app controller, routing
    api.js            ← Fetch wrapper for all API calls
    state.js          ← Client-side state management
    render.js         ← DOM rendering functions
    format.js         ← Number/currency formatting
    tutorial.js       ← Onboarding flow
  img/
    logo.svg
    favicon.ico
```
- **No framework, no build tools** — vanilla JS with ES modules
- Clean separation of concerns
- Approximately 1,500–2,000 lines of frontend code total

### 5.3 Mobile Responsiveness
- Sidebar collapses to a bottom tab bar on mobile
- Resource grid goes from 3-across → 2-across → 1-across
- Touch-friendly button sizes (min 44px tap targets)
- Market form stacks vertically on small screens

### 5.4 Performance
- Reduce polling interval from 1s to 2s (or use Server-Sent Events for push updates)
- Only update DOM elements whose values actually changed (diffing)
- Debounce research investment input changes

---

## Phase 6: Polish & Publishing Readiness

### 6.1 Quality
- Input validation (prevent negative prices, empty fields)
- Graceful error handling with user-facing messages
- Session timeout handling with redirect to login
- Prevent double-click on action buttons (disable during request)

### 6.2 Security Hardening
- CSRF tokens on all POST actions
- Rate limiting on login/signup
- Sanitize all user inputs displayed in the UI (XSS prevention)
- Remove hardcoded API key from Market.java (move to environment/config)

### 6.3 Deployment
- Dockerfile for easy deployment (Tomcat + MariaDB)
- Environment-based configuration (DB credentials, API keys)
- Health check endpoint

---

## Implementation Priority & Estimated Scope

| Phase | What | Files Changed | Complexity |
|-------|------|---------------|------------|
| **1** | Design system (CSS variables, colors, typography) | 4 new CSS files | Low |
| **2** | New login page | 1 HTML, 1 CSS | Low |
| **3** | Dashboard layout + resource grid | 1 HTML, 2 JS, 2 CSS | Medium |
| **4** | Resource detail panel + research bar | JS + CSS additions | Medium |
| **5** | Marketplace UI redesign | JS + CSS additions | Medium |
| **6** | Animations, toasts, number formatting | JS + CSS additions | Low |
| **7** | Tutorial / onboarding overlay | 1 JS, 1 CSS | Low |
| **8** | Leaderboard + supply chain viz | 1 JS, API endpoint | Medium |
| **9** | Mobile responsiveness | CSS media queries | Low |
| **10** | API cleanup + error handling | 3-4 Java servlets | Medium |
| **11** | Security hardening | Java servlets + config | Medium |

**Recommended implementation order:** Phases 1 → 2 → 3 → 5 → 6 → 4 → 9 → 7 → 10 → 8 → 11

Start with the design system and layout since everything else builds on top of it. The marketplace is core gameplay so it comes before polish. Mobile and tutorial come after the desktop experience is solid.

---

## Summary

The core game mechanics are solid — resource production, crafting chains, player trading, and market dynamics make for engaging gameplay. The main gap is **presentation**. This plan transforms a developer prototype into a visually cohesive, satisfying game by:

1. **Establishing a clear visual identity** (dark theme, gold accents, resource icons)
2. **Reorganizing the layout** from a wall of cards into a navigable dashboard
3. **Adding game feel** (animations, toasts, progress bars, number formatting)
4. **Guiding new players** with a simple tutorial and supply chain visualization
5. **Making it mobile-friendly** and technically robust for real-world use

No game engine or framework needed — vanilla HTML/CSS/JS on top of the existing Java backend.
