function findIndividualBy(attr, value) {
	for(var key in contacts.individuals) {
		if(contacts.individuals[key][attr] == value) return key;
	}
}

function addMessages(messages) {
	messages.sort((a, b) => a.timestamp - b.timestamp).map(function(msg) {
		var message = (msg.message || "").toLowerCase();
		var color = "black";
		if(message == "yes" || message == "y" || message.indexOf("y ") == 0 || message.indexOf("yes ") == 0) color = "#00AA00";
		if(message == "no" || message == "n" || message.indexOf("n ") == 0 || message.indexOf("no ") == 0) color = "#AA0000";
		$('<tr style="color: ' + color + '"></tr>').append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top"></td>').text(msg.outbound ? "TO" : "FROM"))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top; min-width: 120px"></td>').text(msg.outbound ? (msg.recipients || []).join(", ") : findIndividualBy("sms", msg.sender) || msg.sender))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top"></td>').text(msg.message))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top; min-width: 80px"></td>').text(new Date(msg.timestamp).toLocaleTimeString()))
			.append($('<td style="padding-right: 1em; padding-bottom: 5px; vertical-align: top"></td>').append((msg.outbound || findIndividualBy("sms", msg.sender)) ? $('<button>Reply</button>').click(function() {
				(msg.outbound ? msg.recipients : [msg.sender]).map(function(addr) {
					$('input:checkbox[address="' + (findIndividualBy('sms', addr) || addr) + '"]')[0].checked=true;
				});
				textarea.focus();
			}) : null))
			.prependTo(message_container);
	});	
}

function buildPagingUI() {
	var top = $('<div style="position: absolute; width: 100%; height: 130px; top: 0; border-bottom: 1px solid #CCCCCC; padding-bottom: 10px"></div>').appendTo(document.body);
	var div = $('<div>Priority </div>').appendTo(top);
	$('<select id="priority"><option value="low">Non-Emergency SMS</option><option value="medium">Emergency SMS</option><option value="high">Emergency SMS + Phone</option></select>').appendTo(div).change(function() {
		var val = $('#priority').val();
		if(val == "low") {
			$('#levelwarning').css('color', 'green').text('Non-Emergency SMS');
		}
		if(val == "medium") {
			$('#levelwarning').css('color', 'orange').text('Emergency SMS');
		}
		if(val == "high") {
			$('#levelwarning').css('color', 'red').text('Emergency SMS + Phone Call');
		}
	});
	textarea = $('<textarea rows="5" cols="60"></textarea>').appendTo($('<div></div>').appendTo(div));
	var submit = $('<button>SEND</button>').prependTo($('<div><span>  </span><span id="levelwarning" style="color: green">Non-Emergency SMS</span></div>').appendTo(div));
	
	var container = $('<div style="position: absolute; left: 0px; botom: 0px; top: 150px; width: 100%"></div>').appendTo(document.body);
	var subcontainer = $('<div style="height: 100%; padding-left: 200px"></div>').appendTo(container);
	
	var left = $('<div style="width: 100%; height: 100%; overflow-y: auto"></div>').appendTo($('<div style="position: relative; float: left; height: 100%; display: block; width: 190px; margin-left: -200px; overflow-y: hidden; border-right: 1px solid #CCCCCC;"></div>').appendTo(subcontainer));
	
	var div = $('<div><b>Groups</b></div>').appendTo(left);
	var supergroup = $('<select><option>Select Callout Group</option></select>').appendTo($('<div></div>').appendTo(div)).change(function() {
		var sg = contacts.supergroups[supergroup.val()];
		$('input:checkbox').each(function(index, item) {
			if(item.classList.contains('group')) item.checked=false;
		});
		if(sg == null) return;
		for(var i = 0; i < sg.length; i++) {
			$('input[address="' + sg[i] + '"]')[0].checked = true;
		}
	});
	Object.keys(contacts.supergroups).sort().map(function(name) {
		$('<option>' + name + '</option>').appendTo(supergroup);
	});
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


function buildReportUI() {
	var div = $('<div></div>').appendTo(document.body);
	Object.keys(contacts.individuals).sort().map(function(pname) {
		var groups2 = [];
		for(var gname in contacts.groups) {
			for(var i = 0; i < contacts.groups[gname].length; i++) {
				if(pname == contacts.groups[gname][i]) groups2.push(gname);
			}
		}
		var groups = [];
		if(groups2.indexOf('A Skiers') >= 0 || groups2.indexOf('A Snowmobile') >= 0 || groups2.indexOf('A Snowcat') >= 0) groups.push('A');
		if(groups2.indexOf('B Skiers') >= 0 || groups2.indexOf('B Snowmobile') >= 0 || groups2.indexOf('OHV') >= 0) groups.push('B');
		if(groups2.indexOf('Coordinators') >= 0) groups.push('Coordinators');
		var person = contacts.individuals[pname];
		if(groups.length > 0) $('<div></div>').text(pname.split(' ').join(',') + ',' + person.sms + ',' + (person.email || '') + ',' + groups.join(' ')).appendTo(div);
		/*
		$('<div></div>').text(name + ',,,').appendTo(div);
		for(var i = 0; i < contacts.groups[name].length; i++) {
			var pname = contacts.groups[name][i];
			$('<div></div>').text(pname.split(' ').reverse().join(',') + ',' + person.sms + ',' + (person.email || '')).appendTo(div);
		}*/
	});

}