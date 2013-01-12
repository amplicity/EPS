function updateCPISPIlines() {
	$('.spi-content .spiline').each(function() {
		var v = parseFloat($(this).data('val'));
		if (v > 3) v = 3;
		if (v < 0) v = 0;
		$(this).rotate(v * 60);
	});
}

function winReady(tabId) {
//	if (tabId == null) tabId = false;
	$('#home-accordion').accordion({
		active: tabId,
		autoHeight: false,
		collapsible: true,
		change: function(e, ui) {
			if(ui.newHeader.length>0) location.href=ui.newHeader.find('a').attr('href');
		}
	});
	initWorkflowEditButtons();
	updateCPISPIlines();
	$('#topNavItems').menubar({
		autoExpand: true,
		position: {
			my: "left top",
			at: "left bottom-2"
		}
	});
}

function initWorkflowEditButtons() {
	$('#home-accordion .cancel-button').click(function() {
		$('#home-accordion .hiddenfield').hide();
		$('#home-accordion .showfield').show();
	});
	$('#home-accordion .edit-button').click(function() {
		$('#home-accordion .hiddenfield').hide();
		$('#home-accordion .showfield').show();
		
		$(this).parents('tr').find('.showfield').each(function() {
			var $prev = $(this).prev('span.hiddenfield').show();
			if ($prev.data('value')) $prev.children().val($(this).data('value'));
			$(this).hide();
		});
	});
}