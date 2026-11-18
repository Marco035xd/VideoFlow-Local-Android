(() => {
  'use strict';
  const panel = document.querySelector('[data-sound-settings]');
  if (!panel) return;
  const assets = new URL('./', document.currentScript.src);
  const kinds = ['click', 'complete', 'error'];
  const sounds = {
    title:['Sonidos (Tester)','Sounds (Tester)','Sons (teste)'],
    help:['Activa los sonidos que quieres escuchar. Los cambios se guardan al instante.','Enable the sounds you want to hear. Changes are saved immediately.','Ative os sons que quer ouvir. As alterações são salvas imediatamente.'],
    click:['Clic en los controles','Control clicks','Cliques nos controles'],
    complete:['Descarga completada','Download complete','Download concluído'],
    error:['Error','Error','Erro'],
    preview:['Probar sonido','Test sound','Testar som'],
    failed:['No se pudo guardar el ajuste de sonido.','Could not save sound settings.','Não foi possível salvar a configuração de som.'],
  };
  const language = () => document.documentElement.lang === 'en' ? 1 : document.documentElement.lang === 'pt' ? 2 : 0;
  const t = key => sounds[key][language()];
  const audio = Object.fromEntries(kinds.map(kind => {
    const sample = new Audio(new URL(`sound-${kind}.wav`, assets));
    sample.preload = 'auto';
    return [kind, sample];
  }));
  const enabled = Object.fromEntries(kinds.map(kind => [kind, false]));
  const play = (kind, preview = false) => {
    const sample = audio[kind];
    if (!sample || (!preview && !enabled[kind])) return;
    sample.currentTime = 0;
    sample.play().catch(() => {});
  };
  window.VideoFlowSounds = {play};

  panel.classList.add('vf-sounds');
  panel.setAttribute('translate', 'no');
  panel.innerHTML = `<fieldset>
    <legend data-sound-text="title"></legend><p data-sound-text="help"></p>
    ${kinds.map(kind => `<div class="vf-sound-row">
      <label><input type="checkbox" name="${kind}"><span data-sound-text="${kind}"></span></label>
      <button class="vf-profile-btn" type="button" data-sound-preview="${kind}" data-sound-text="preview"></button>
    </div>`).join('')}
  </fieldset><p class="is-error" data-sound-text="failed" role="status" hidden></p>`;
  const fields = panel.querySelector('fieldset');
  const error = panel.querySelector('.is-error');
  const controls = [...fields.querySelectorAll('input')];
  function translate() {
    for (const node of panel.querySelectorAll('[data-sound-text]')) node.textContent = t(node.dataset.soundText);
    for (const button of panel.querySelectorAll('[data-sound-preview]')) {
      button.setAttribute('aria-label', `${t('preview')} · ${t(button.dataset.soundPreview)}`);
    }
  }
  async function settings(changes) {
    const data = window.Native
      ? await rpc('soundSettings', changes || {})
      : await api('/api/settings', changes && {method:'PUT', body:JSON.stringify({sounds:changes})});
    return window.Native ? data : data.settings.sounds;
  }
  function apply(value) {
    for (const input of controls) input.checked = enabled[input.name] = value?.[input.name] === true;
  }
  fields.addEventListener('change', async event => {
    const {name, checked} = event.target;
    enabled[name] = checked;
    fields.disabled = true;
    error.hidden = true;
    try { apply(await settings({[name]:checked})); }
    catch { apply({...enabled, [name]:!checked}); error.hidden = false; }
    finally { fields.disabled = false; }
  });
  document.addEventListener('click', event => {
    if (!event.isTrusted) return;
    const control = event.target.closest('button, summary, a[href], select, input[type="checkbox"], label');
    if (!control || control.closest(':disabled,[aria-disabled="true"]')) return;
    if (control.matches('[data-sound-preview]')) play(control.dataset.soundPreview, true);
    else if (!panel.contains(control) && !control.matches('label')) play('click');
  }, true);
  new MutationObserver(translate).observe(document.documentElement, {attributes:true, attributeFilter:['lang']});
  translate();
  fields.disabled = true;
  settings().then(apply).catch(() => {error.hidden = false;}).finally(() => {fields.disabled = false;});
})();
