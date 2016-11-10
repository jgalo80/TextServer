var wsPath = "/websocket";
var output;
var user;
var websocket;

function init() {
	output = document.getElementById("output");
}

function testWebSocket(user) {
    wsScheme = window.location.protocol == "http:" ? "ws:" : "wss:";
    wsUri = wsScheme + "//" + window.location.host + wsPath;
    writeToScreen("Conecting to " + wsUri + "...");
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
    $("#register").click(function() {
        console.log("Registrando usuario: " + $("#user").val());
        $.ajax({
            url: "/register",
            type: "POST",
            data: {user: $("#user").val(), passwd: $("#passwd").val()},
            success: function(result) {
                user = $("#user").val();
                writeToScreen("REGISTERED user " + user);
            }
        });
    });
    $("#login").click(function() {
        console.log("Usuario: " + $("#user").val());
        $.ajax({
            url: "/login",
            type: "POST",
            data: {user: $("#user").val(), passwd: $("#passwd").val()},
            success: function(result) {
                user = $("#user").val();
                writeToScreen("LOGGED IN " + user);
                testWebSocket();
            }
        });
    });
    $("#send").click(function() {
        writeToScreen("Enviando [" + $("#msg").val() + "] ...");
        var message = '{"command":"MSG", "payload":"' + $("#msg").val() + '"}';
        websocket.send(message);
    });
    $("#broadcast").click(function() {
        writeToScreen("Enviando broadcast [" + $("#msg").val() + "] ...");
        var message = '{"command":"BROADCAST", "payload":"' + $("#msg").val() + '"}';
        websocket.send(message);
    });
});
