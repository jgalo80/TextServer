var wsPath = "/websocket";
var output;
var user;
var websocket;

function init() {
	output = document.getElementById("output");
}

function testWebSocket(user) {
    wsScheme = window.location.protocol == "http:" ? "ws:" : "wss:";
    wsUri = wsScheme + "//" + window.location.host + ":" + window.location.port + wsPath;
	websocket = new WebSocket(wsUri);
	websocket.onopen = function (evt) {
		onOpen(evt)
	};
	websocket.onclose = function (evt) {
		onClose(evt)
	};
	websocket.onmessage = function (evt) {
		onMessage(evt)
	};
	websocket.onerror = function (evt) {
		onError(evt)
	};
}

function onOpen(evt) {
	writeToScreen("CONNECTED");
}

function onClose(evt) {
	writeToScreen("DISCONNECTED");
}

function onMessage(evt) {
	writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data + '</span>');
}

function onError(evt) {
	writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data);
}

function doSend(message) {
	websocket.send(message);
	writeToScreen("SENT: " + message);
}

function sendAdd(symbol) {
	var message = '{"command":"add", "tickerSymbol":"' + symbol + '"}';
	websocket.send(message);
	writeToScreen("SENT: " + message);
}

function sendRemove(symbol) {
	var message = '{"command":"remove", "tickerSymbol":"' + symbol + '"}'
	websocket.send(message);
	writeToScreen("SENT: " + message);
}

function writeToScreen(message) {
    console.log(message);
	var pre = document.createElement("p");
	pre.style.wordWrap = "break-word";
	pre.innerHTML = message;
	output.appendChild(pre);
}

window.addEventListener("load", init, false);

$(document).ready(function(){
    $("#login").click(function() {
        console.log("Usuario: " + $("#user").val());
        $.ajax({
            url: "/login",
            type: "POST",
            data: {user: $("#user").val()},
            success: function(result) {
                user = $("#user").val();
                writeToScreen("LOGGED IN");
                testWebSocket();
            }
        });
    });
    $("#send").click(function() {
        writeToScreen("Enviando [" + $("#msg").val() + "] ...");
        var message = '{"command":"BROADCAST_MESSAGE", "payload":"' + $("#msg").val() + '"}';
        websocket.send(message);
    });
});
