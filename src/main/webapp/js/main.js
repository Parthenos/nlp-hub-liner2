$(document).ready(function(){	

	$( "#execute-button" ).click(function() {
		var firstnameBox =  $.trim( $('#input-textarea').val() )
		if (firstnameBox == "") {
			$('#input-textarea').css("border-color","red");
			$('#input-textarea').attr("placeholder", "Paste your text here!");
		}
		else {
			$('#input-textarea').css("border-color","#555");
			doStartComputation();
		}
	});

	doCallback = function(uri) {
		$.ajax({
			url : "nlphub-servlet",
			type : 'GET',
			datatype : 'json',
			data : {
				uri :  uri
			},
			// This works well for JSON returns, for XML we just show the XML without highlighting
			/*
			success : function(data) {
				var obj = eval("(" + data + ")");
				var str = JSON.stringify(obj, undefined, 4);
				$('#result').html(syntaxHighlight(str));
			}
			*/
            success : function(data) {
                $('#result').html(data);
            }
		});
	}

	doStartComputation = function() {
		var options = "default";
		options = $("input[type=checkbox]:checked").map(
				function () {return this.value;}).get().join("|");
		$('#result').html("");
		var token2Send = getUrlParameter("token") == null ? "" : getUrlParameter("token");
		webSocket.send({
			'action': 'start',
			'text' :  $("#input-textarea").val(),
			'options' : options,
			'token' : token2Send
		}).done(function() {
			showProgressBar(true);
			enableCommands(false);
		}).fail(function(e) {
			console.log("failed");
		});
	}

	doHandleResponse = function(message) {
		if (message.response == "error") {
			$('#result').html("<span style='color: red;'>"+message.value+"</span>");
			enableCommands(true);
			showProgressBar(false);
		} 
		else if (message.response == "computing") {
			showProgressBar(true);
			enableCommands(false);
		} 
		else if (message.response == "computed") {
			showProgressBar(false);
			enableCommands(true);
			console.log("message="+message.value);
			if (message.value.startsWith("http")) {
				$('#downloadLink').html("Result:&nbsp;" +
					"<a class=\"waves-effect waves-light btn  red darken-1\" href=\""+message.value+"\">Download</a>&nbsp;"); //+
						//"<a class=\"waves-effect waves-light btn blue darken-1\" href=\"Javascript:doCallback(encodeURI('"+message.value.trim()+"'));\">View</a>");
			} 
		}
	}
});

function showProgressBar(show) {
	var display = (show) ? "visible" : "hidden";
	$('#progressBar').css('visibility', display);
}

function enableCommands(enable) {
	if (enable) {
		$('#execute-button').removeAttr("disabled");
		$('#execute-button').text('Execute');
		$('input[type=checkbox]').removeAttr("disabled");
	} else {
		$('#execute-button').text('Computing ...');
		$('input[type=checkbox]').attr('disabled', 'true');
		$('#execute-button').attr('disabled', 'disabled');
		$('#downloadLink').html("");
	}
}

function syntaxHighlight(json) {
	if (typeof json != 'string') {
		json = JSON.stringify(json, undefined, 1);
	}
	json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
	return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
		var cls = 'number';
		if (/^"/.test(match)) {
			if (/:$/.test(match)) {
				cls = 'key';
			} else {
				cls = 'string';
			}
		} else if (/true|false/.test(match)) {
			cls = 'boolean';
		} else if (/null/.test(match)) {
			cls = 'null';
		}
		return '<span class="' + cls + '">' + match + '</span>';

	});
}

function getUrlParameter(name) {
	name = name.replace(/[\[]/, '\\[').replace(/[\]]/, '\\]');
	var regex = new RegExp('[\\?&]' + name + '=([^&#]*)');
	var results = regex.exec(location.search);
	return results === null ? '' : decodeURIComponent(results[1].replace(/\+/g, ' '));
};