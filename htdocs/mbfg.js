
function toggleDiv(divId)
{
	var dv = document.getElementById(divId);
	if (dv.style.display == 'block')
		dv.style.display = 'none';
	else
		dv.style.display = 'block';
}