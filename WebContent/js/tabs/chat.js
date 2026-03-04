/**
 * chat.js — Chat tab rendering.
 * Direct messaging between players (human and AI).
 */

var _chatPollInterval = null;
var _chatInitialized = false;

function renderChatTab() {
    if (!_chatInitialized) {
        var html = '<div class="chat-layout">' +
            '<div class="conversation-list">' +
                '<div class="card-title">Players</div>' +
                '<div id="player-list-items"></div>' +
            '</div>' +
            '<div class="chat-panel">' +
                '<div class="chat-messages" id="chat-messages">' +
                    '<div class="empty-state"><div class="message">Select a player to view messages, or send a new message below.</div></div>' +
                '</div>' +
                '<div class="chat-input-area">' +
                    '<select id="chat-recipient" class="form-input">' +
                        '<option value="">Select player...</option>' +
                    '</select>' +
                    '<input type="text" id="chat-message-input" class="form-input" placeholder="Type a message..." maxlength="1000">' +
                    '<button class="btn btn-primary" onclick="sendChatMessage()">Send</button>' +
                '</div>' +
            '</div>' +
        '</div>';

        $('#tab-chat').html(html);
        _chatInitialized = true;

        // Enter to send
        $('#chat-message-input').on('keydown', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendChatMessage();
            }
        });
    }

    loadChatData();
    startChatPolling();
}

async function loadChatData() {
    try {
        // Load messages and player list in parallel
        var msgPromise = API.get('/api/v1/chat/messages');
        var playerPromise = API.get('/api/v1/leaderboard');

        var msgData = await msgPromise;
        var playerData = await playerPromise;

        State.messages = msgData.messages || [];
        State.players = (playerData.players || []);

        populatePlayerList();
        renderMessages();
    } catch (err) {
        console.error('Chat load error:', err);
    }
}

function populatePlayerList() {
    var html = '';
    var select = $('#chat-recipient');
    var currentVal = select.val();

    select.html('<option value="">Select player...</option>');

    $.each(State.players, function(i, p) {
        if (State.player && p.playerId === State.player.playerId) return;

        // Player list sidebar
        html += '<div class="conversation-item" onclick="selectChatPlayer(' + p.playerId + ')">' +
            '<span>' + escapeHtml(p.username) + '</span>' +
            (p.isAi ? '<span class="ai-badge">AI</span>' : '') +
        '</div>';

        // Dropdown
        select.append('<option value="' + p.playerId + '">' +
            escapeHtml(p.username) + (p.isAi ? ' [AI]' : '') +
        '</option>');
    });

    $('#player-list-items').html(html || '<div class="text-muted" style="padding:8px;font-size:12px;">No other players</div>');

    if (currentVal) select.val(currentVal);
}

function selectChatPlayer(playerId) {
    $('#chat-recipient').val(playerId);
    // Highlight in list
    $('.conversation-item').removeClass('selected');
    $('.conversation-item').each(function() {
        if ($(this).attr('onclick') && $(this).attr('onclick').indexOf(playerId) !== -1) {
            $(this).addClass('selected');
        }
    });
}

function renderMessages() {
    if (!State.player) return;
    var myId = State.player.playerId;
    var messages = State.messages;

    if (messages.length === 0) {
        $('#chat-messages').html('<div class="empty-state"><div class="message">No messages yet. Start a conversation!</div></div>');
        return;
    }

    var html = '';
    $.each(messages, function(i, msg) {
        var isMine = msg.fromPlayerId === myId;
        var senderName = msg.fromUsername + (msg.fromIsAi ? ' [AI]' : '');
        var time = formatTime(msg.createdAt);

        html += '<div class="chat-bubble ' + (isMine ? 'mine' : 'theirs') + '">' +
            '<span class="sender">' + escapeHtml(senderName) + '</span>' +
            '<div class="message">' + escapeHtml(msg.message) + '</div>' +
            '<span class="timestamp">' + time + '</span>' +
        '</div>';
    });

    var chatEl = $('#chat-messages');
    var wasAtBottom = chatEl[0].scrollHeight - chatEl[0].scrollTop - chatEl[0].clientHeight < 50;
    chatEl.html(html);
    if (wasAtBottom) {
        chatEl.scrollTop(chatEl[0].scrollHeight);
    }
}

async function sendChatMessage() {
    var toPlayerId = parseInt($('#chat-recipient').val());
    var message = $('#chat-message-input').val().trim();

    if (!toPlayerId) { showToast('Select a recipient', 'error'); return; }
    if (!message) return;

    try {
        await API.post('/api/v1/chat/send', { toPlayerId: toPlayerId, message: message });
        $('#chat-message-input').val('');
        await loadChatData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function startChatPolling() {
    stopChatPolling();
    _chatPollInterval = setInterval(loadChatData, 3000);
}

function stopChatPolling() {
    if (_chatPollInterval) {
        clearInterval(_chatPollInterval);
        _chatPollInterval = null;
    }
}

function formatTime(ts) {
    if (!ts) return '';
    var d = new Date(ts);
    if (isNaN(d.getTime())) return ts;
    return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}
