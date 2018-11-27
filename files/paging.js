function findIndividualBy(attr, value) {
	for(var key in contacts.individuals) {
		if(contacts.individuals[key][attr] == value) return key;
	}
}

function addMessages(messages) {
	messages.sort((a, b) => a.timestamp - b.timestamp).map(function(msg) {
		$('<tr></tr>').append($('<td style="padding-right: 2em; padding-bottom: 5px"></td>').text(msg.outbound ? "TO" : "FROM"))
			.append($('<td style="padding-right: 2em; padding-bottom: 5px"></td>').text(msg.outbound ? (msg.recipients || []).join(", ") : findIndividualBy("sms", msg.sender)))
			.append($('<td style="padding-right: 2em; padding-bottom: 5px"></td>').text(msg.message))
			.append($('<td style="padding-right: 2em; padding-bottom: 5px"></td>').text(new Date(msg.timestamp).toLocaleTimeString()))
			.append($('<td style="padding-right: 2em; padding-bottom: 5px"></td>').append($('<button>Reply</button>').click(function() {
				(msg.outbound ? msg.recipients : [msg.sender]).map(function(addr) {
					$('input:checkbox[address="' + (findIndividualBy('sms', addr) || addr) + '"]')[0].checked=true;
				});
				textarea.focus();
			})))
			.prependTo(message_container);
	});	
}

function buildPagingUI() {
	var top = $('<div style="border-bottom: 1px solid #CCCCCC; padding-bottom: 10px"></div>').appendTo(document.body);
	var div = $('<div>Priority </div>').appendTo(top);
	$('<select id="priority"><option value="low">Low (SMS)</option><option value="high">High (SMS + Phone)</option></select>').appendTo(div);
	textarea = $('<textarea rows="5" cols="60"></textarea>').appendTo($('<div></div>').appendTo(div));
	var submit = $('<button>SEND</button>').appendTo($('<div></div>').appendTo(div));
	
	var left = $('<div style="float: left; padding-top: 10px"></div>').appendTo(document.body);
	
	var div = $('<div><b>Groups</b></div>').appendTo(left);
	Object.keys(contacts.groups).sort().map(function(name) {
		$('<input type="checkbox" class="group">').attr("address", name).prependTo($('<div><div>').html(name).appendTo(div));
	});
	var div = $('<div style="margin-top: 10px"><b>Individuals</b></div>').appendTo(left);
	Object.keys(contacts.individuals).sort().map(function(name) {
		$('<input type="checkbox" class="individual">').attr("address", name).prependTo($('<div><div>').html(name).appendTo(div));
	});
	
	var right = $('<div style="float: left; border-left: 1px solid #CCCCCC; padding-left: 10px; margin-left: 10px; padding-top: 10px"></div>').appendTo(document.body);
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