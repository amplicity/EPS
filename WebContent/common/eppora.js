function updateCPISPIlines() {
	$('.spi-content .spiline').each(function() {
		var v = parseFloat($(this).data('val'));
		if (v > 3) v = 3;
		if (v < 0) v = 0;
		$(this).rotate((v - 1.5)/1.5 * 90);
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