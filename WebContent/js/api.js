/**
 * api.js — Centralized HTTP client for the Trade Empire REST API.
 * All data flows through these methods. No direct fetch() calls elsewhere.
 */
const API = {
    _token: function() { return localStorage.getItem('token'); },

    async get(path) {
        const resp = await fetch(path, {
            headers: { 'Authorization': 'Bearer ' + this._token() }
        });
        if (resp.status === 401) { logout(); return; }
        const json = await resp.json();
        if (json.success === false) throw new Error(json.error || 'request_failed');
        return json.data !== undefined ? json.data : json;
    },

    async post(path, body) {
        const resp = await fetch(path, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + this._token(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body || {})
        });
        if (resp.status === 401) { logout(); return; }
        const json = await resp.json();
        if (json.success === false) throw new Error(json.error || 'request_failed');
        return json.data !== undefined ? json.data : json;
    },

    async del(path) {
        const resp = await fetch(path, {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + this._token() }
        });
        if (resp.status === 401) { logout(); return; }
        const json = await resp.json();
        if (json.success === false) throw new Error(json.error || 'request_failed');
        return json.data !== undefined ? json.data : json;
    }
};

function logout() {
    API.post('/api/v1/auth/logout').catch(function() {});
    localStorage.removeItem('token');
    localStorage.removeItem('playerId');
    window.location.href = '/login.html';
}

function formatCash(amount) {
    return '$' + Number(amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function formatNumber(n) {
    if (n === null || n === undefined) return '—';
    n = Number(n);
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 10000) return (n / 1000).toFixed(1) + 'K';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    if (Number.isInteger(n)) return n.toString();
    return n.toFixed(1);
}

function escapeHtml(text) {
    if (!text) return '';
    return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
