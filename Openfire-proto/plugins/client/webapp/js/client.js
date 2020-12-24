var users = ['admin@192.168.0.101', 'test1@192.168.0.101'];
var connection;
var url = 'http://localhost:7070/http-bind/';
var userJid;
var otherJid;

function login() {
	var id = $("#id").val();
	var password = $("#passwd").val();
	
	for(var i=0;i<users.length;i++) {
		if(users[i].indexOf(id) > -1) {
			userJid = users[i];
			
			if(i==0) otherJid = users[1];
			else if(i==1) otherJid = users[0];
			
			break;
		}
	}
	
	connection = new Strophe.Connection(url);
	connection.connect(userJid, password, function(status) {
		if (status == Strophe.Status.CONNECTING) {
		    console.log('Strophe is connecting.');
	    } else if (status == Strophe.Status.CONNFAIL) {
	    	console.log('Strophe failed to connect.');
		} else if (status == Strophe.Status.DISCONNECTING) {
			console.log('Strophe is disconnecting.');
		} else if (status == Strophe.Status.DISCONNECTED) {
			console.log('Strophe is disconnected.');
		} else if (status == Strophe.Status.CONNECTED) {
			console.log('Strophe is connected.');
		    // set presence
		    connection.send($pres());
		    // set handlers
		    connection.addHandler(onMessage, null, 'message', null, null, null);
		}
	})
}

function onMessage(msg) {
    var to = msg.getAttribute('to');
    var from = msg.getAttribute('from');
    var type = msg.getAttribute('type');
    var elems = msg.getElementsByTagName('body');

    if (type == "chat" && elems.length > 0) {
        var body = elems[0];
        console.log('CHAT: I got a message from ' + from + ': ' + Strophe.getText(body));
    }
    
    var message = "<div style='float:left'>" + from + "<br>" + Strophe.getText(body) + "</div>";
	
	$("#msgList").append(message);
    
    return true;
}

function sendMsg() {
	var msg = $("#msg").val();
	console.log('CHAT: Send a message to ' + otherJid + ': ' + msg);

	var m = $msg({
	    to: otherJid,
	    from: userJid,
	    type: 'chat'
	}).c("body").t(msg);
	connection.send(m);
	
	var message = "<div style='float:right'>" + msg + "</div>";
	
	$("#msgList").append(message);
}