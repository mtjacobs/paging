function findIndividualBy(attr, value) {
	for(var key in contacts.individuals) {
		if(contacts.individuals[key][attr] == value) return key;
	}
}

function addMessages(messages) {
	messages.sort((a, b) => a.timestamp - b.timestamp).map(function(msg) {
		$('<tr></tr>').append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top"></td>').text(msg.outbound ? "TO" : "FROM"))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top; min-width: 120px"></td>').text(msg.outbound ? (msg.recipients || []).join(", ") : findIndividualBy("sms", msg.sender)))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top"></td>').text(msg.message))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top; min-width: 80px"></td>').text(new Date(msg.timestamp).toLocaleTimeString()))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top"></td>').append($('<button>Reply</button>').click(function() {
				(msg.outbound ? msg.recipients : [msg.sender]).map(function(addr) {
					$('input:checkbox[address="' + (findIndividualBy('sms', addr) || addr) + '"]')[0].checked=true;
				});
				textarea.focus();
			})))
			.prependTo(message_container);
	});	
}

function buildPagingUI() {
	var top = $('<div style="position: absolute; width: 100%; height: 130px; top: 0; border-bottom: 1px solid #CCCCCC; padding-bottom: 10px"></div>').appendTo(document.body);
	var div = $('<div>Priority </div>').appendTo(top);
	$('<select id="priority"><option value="low">Low (SMS)</option><option value="high">High (SMS + Phone)</option></select>').appendTo(div);
	textarea = $('<textarea rows="5" cols="60"></textarea>').appendTo($('<div></div>').appendTo(div));
	var submit = $('<button>SEND</button>').appendTo($('<div></div>').appendTo(div));
	
	var container = $('<div style="position: absolute; left: 0px; botom: 0px; top: 150px; width: 100%"></div>').appendTo(document.body);
	var subcontainer = $('<div style="height: 100%; padding-left: 200px"></div>').appendTo(container);
	
	var left = $('<div style="width: 100%; height: 100%; overflow-y: auto"></div>').appendTo($('<div style="position: relative; float: left; height: 100%; display: block; width: 190px; margin-left: -200px; overflow-y: hidden; border-right: 1px solid #CCCCCC;"></div>').appendTo(subcontainer));
	
	var div = $('<div><b>Groups</b></div>').appendTo(left);
	Object.keys(contacts.groups).sort().map(function(name) {
		$('<input type="checkbox" class="group">').attr("address", name).prependTo($('<div><div>').html(name).appendTo(div));
	});
	var div = $('<div style="margin-top: 10px"><b>Individuals</b></div>').appendTo(left);
	Object.keys(contacts.individuals).sort().map(function(name) {
		$('<input type="checkbox" class="individual">').attr("address", name).prependTo($('<div><div>').html(name).appendTo(div));
	});
	
	
	var right = $('<div style="position: relative; height: 100%; width: 100%; float: left"></div>').appendTo(subcontainer);
	message_container = $('<tbody></tbody>').appendTo($('<table cellspacing="0" cellpadding="0"><thead><tr><th style="text-align: left">Dir</th><th style="text-align: left">Contact</th><th style="text-align: left">Message</th><th style="text-align: left">Time</th><th style="text-align: left">Actions</th></tr></thead></table>').appendTo(right));
	$('').appendTo(message_container);
	
	submit.click(function() {
		var contacts = { groups: [], individuals: [] };
		$('input:checkbox').each(function(index, item) {
			if(item.checked) contacts[item.classList.contains('group') ? 'groups' : 'individuals'].push(item.getAttribute("address"));
		});
		
		if(contacts.groups.length == 0 && contacts.individuals.length == 0) {
			alert("Please select at least one contact before sending the message");
			return;
		}
		
		$.ajax({
			url: '/send',
			type: 'POST',
			data: "contacts=" + encodeURIComponent(JSON.stringify(contacts)) + "&message=" + encodeURIComponent(textarea.val()) + "&priority=" + $('#priority').val(),
			success: function(obj) {
				alert("Message sent");
			}
		});
		textarea.val('');
		$('#priority').val('low');
		$('input:checkbox').each(function(index, item) { item.checked = false });
	});
	
	addMessages(messages);
	window.setInterval(function() {
		$.ajax({
			url: '/messages?since=' + timestamp,
			type: 'GET',
			success: function(obj) {
				timestamp = obj.timestamp;
				addMessages(obj.messages);
			}
		});
	}, 2000);

}