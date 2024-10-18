        // <![CDATA[

function nvwp_showHide_button_2_1_show(event) {
  document.getElementById('button_expand_2_1').style.display = 'none';
  document.getElementById('button_collapse_2_1').style.display = 'inline';
  document.getElementById('details_2_1').style.display = 'block';
  event.preventDefault();
}
function nvwp_showHide_button_2_1_hide(event) {
  document.getElementById('button_collapse_2_1').style.display = 'none';
  document.getElementById('details_2_1').style.display = 'none';
  document.getElementById('button_expand_2_1').style.display = 'inline';
  event.preventDefault();
}

const exEl = document.getElementById("button_expand_2_1");
exEl.addEventListener("click", nvwp_showHide_button_2_1_show);

const colEl = document.getElementById("button_collapse_2_1");
colEl.addEventListener("click", nvwp_showHide_button_2_1_hide);



        // ]]>

