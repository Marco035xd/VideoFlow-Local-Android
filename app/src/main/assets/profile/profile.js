(() => {
  'use strict';
  const root = document.querySelector('#page-profile');
  if (!root) return;
  const assets = new URL('./', document.currentScript.src);
  const select = selector => root.querySelector(selector);
  const all = selector => [...root.querySelectorAll(selector)];
  const imageTypes = {jpg:'image/jpeg', jpeg:'image/jpeg', png:'image/png', gif:'image/gif', webp:'image/webp'};
  const videoTypes = {mp4:'video/mp4', webm:'video/webm'};
  const limit = {avatar:8 * 1024 ** 2, banner:25 * 1024 ** 2};
  const presets = ['panda', 'astronaut', 'cat', 'planet'];
  const crop = () => ({zoom:100, x:50, y:50});
  const blankProfile = () => ({name:'', username:'', bio:'', avatar:{preset:'', ...crop()}, banner:crop()});
  const copy = {
    myProfile:['Mi perfil','My profile','Meu perfil'],
    subscriptions:['Suscripciones','Subscriptions','Assinaturas'],
    edit:['Editar perfil','Edit profile','Editar perfil'],
    save:['Guardar perfil','Save profile','Salvar perfil'],
    cancel:['Cancelar','Cancel','Cancelar'],
    saved:['Perfil guardado.','Profile saved.','Perfil salvo.'],
    local:['Tu perfil se guarda en este dispositivo.','Your profile is saved on this device.','Seu perfil é salvo neste dispositivo.'],
    name:['Nombre visible','Display name','Nome de exibição'],
    username:['Nombre de usuario','Username','Nome de usuário'],
    usernameHint:['Usa letras, números, punto o guion bajo.','Use letters, numbers, periods or underscores.','Use letras, números, pontos ou sublinhados.'],
    about:['Información personal','About you','Sobre você'],
    avatar:['Foto de perfil','Profile picture','Foto de perfil'],
    choosePhoto:['Elegir foto','Choose photo','Escolher foto'],
    sample:['Fotos de ejemplo','Sample pictures','Fotos de exemplo'],
    panda:['Panda rojo','Red panda','Panda-vermelho'],
    astronaut:['Astronauta','Astronaut','Astronauta'],
    cat:['Gato azul','Blue cat','Gato azul'],
    planet:['Planeta','Planet','Planeta'],
    photoHint:['JPG, PNG, WebP o GIF hasta 8 MB. Usa GIF para una foto en movimiento.','JPG, PNG, WebP or GIF up to 8 MB. Use GIF for an animated picture.','JPG, PNG, WebP ou GIF de até 8 MB. Use GIF para uma foto animada.'],
    importPhoto:['Descargar foto desde un enlace','Download picture from a link','Baixar foto de um link'],
    importHelp:['Pega la dirección HTTPS directa de una imagen.','Paste the direct HTTPS address of an image.','Cole o endereço HTTPS direto de uma imagem.'],
    importButton:['Descargar foto','Download picture','Baixar foto'],
    browser:['Abrir en el navegador','Open in browser','Abrir no navegador'],
    avatarReset:['Usar foto predeterminada','Use default picture','Usar foto padrão'],
    banner:['Fondo del perfil','Profile banner','Fundo do perfil'],
    bannerHint:['Añade una imagen o un video MP4 o WebM de hasta 30 segundos y 25 MB. El video se repite en silencio.','Add an image or an MP4 or WebM video up to 30 seconds and 25 MB. Video loops silently.','Adicione uma imagem ou um vídeo MP4 ou WebM de até 30 segundos e 25 MB. O vídeo é repetido sem som.'],
    chooseBanner:['Elegir imagen o video','Choose image or video','Escolher imagem ou vídeo'],
    bannerReset:['Usar fondo del tema','Use theme background','Usar fundo do tema'],
    playBanner:['Reproducir banner','Play banner','Reproduzir banner'],
    pauseBanner:['Pausar banner','Pause banner','Pausar banner'],
    crop:['Ajustar encuadre','Adjust framing','Ajustar enquadramento'],
    zoom:['Zoom','Zoom','Zoom'],
    horizontal:['Posición horizontal','Horizontal position','Posição horizontal'],
    vertical:['Posición vertical','Vertical position','Posição vertical'],
    center:['Restablecer encuadre','Reset framing','Redefinir enquadramento'],
    preview:['Vista previa del perfil','Profile preview','Prévia do perfil'],
    pro:['Usuario Pro','Pro user','Usuário Pro'],
    concept:['Vista previa de suscripciones','Subscription preview','Prévia de assinaturas'],
    proHelp:['Estos son beneficios propuestos. Las suscripciones están en etapa de diseño.','These are proposed benefits. Subscriptions are in the design stage.','Estes são benefícios propostos. As assinaturas estão em fase de projeto.'],
    proLooks:['Personalización avanzada','Advanced customization','Personalização avançada'],
    proLooksHelp:['Más estilos para tu perfil y tus espacios de trabajo.','More styles for your profile and workspaces.','Mais estilos para seu perfil e espaços de trabalho.'],
    proPresets:['Ajustes guardados','Saved presets','Configurações salvas'],
    proPresetsHelp:['Perfiles de calidad y formato que puedes reutilizar.','Reusable presets for quality and file format.','Predefinições reutilizáveis de qualidade e formato.'],
    proLibrary:['Herramientas de organización','Organization tools','Ferramentas de organização'],
    proLibraryHelp:['Más opciones para ordenar tu biblioteca multimedia.','More ways to organize your media library.','Mais maneiras de organizar sua biblioteca multimídia.'],
    invalid:['Elige una imagen válida en JPG, PNG, GIF o WebP.','Choose a valid JPG, PNG, GIF or WebP image.','Escolha uma imagem JPG, PNG, GIF ou WebP válida.'],
    mediaInvalid:['Elige una imagen válida o un video MP4 o WebM compatible.','Choose a valid image or a compatible MP4 or WebM video.','Escolha uma imagem válida ou um vídeo MP4 ou WebM compatível.'],
    size:['El archivo supera el tamaño indicado.','The file exceeds the size limit.','O arquivo excede o limite de tamanho.'],
    duration:['El video debe durar entre 0 y 30 segundos.','The video must be between 0 and 30 seconds long.','O vídeo deve ter entre 0 e 30 segundos.'],
    link:['Pega un enlace directo y válido que comience con https://.','Paste a valid direct link starting with https://.','Cole um link direto válido que comece com https://.'],
    network:['Este sitio no permite importar la foto directamente. Ábrela en el navegador, guárdala y usa «Elegir foto».','This site does not allow direct image import. Open it in your browser, save it and choose the file.','Este site não permite importar a foto diretamente. Abra-a no navegador, salve-a e escolha o arquivo.'],
    busy:['Preparando archivo…','Preparing file…','Preparando arquivo…'],
    storage:['No se pudo leer o guardar el perfil en este dispositivo.','Could not read or save the profile on this device.','Não foi possível ler ou salvar o perfil neste dispositivo.'],
  };
  const lang = () => document.documentElement.lang === 'en' ? 1 : document.documentElement.lang === 'pt' ? 2 : 0;
  const t = key => copy[key][lang()];
  const portrait = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="12" cy="8" r="3"/><path d="M5 20a7 7 0 0 1 14 0"/></svg>';
  const button = (key, attrs = '') => `<button class="vf-profile-btn" type="button" data-profile-text="${key}" ${attrs}></button>`;
  const cropFields = kind => `<details class="vf-crop" data-crop-panel="${kind}" hidden>
    <summary data-profile-text="crop"></summary>
    <div class="vf-crop-preview ${kind === 'avatar' ? 'vf-profile-avatar' : 'vf-profile-cover'}" data-crop-preview="${kind}" aria-hidden="true"></div>
    ${[['zoom', 'zoom', 100, 220], ['horizontal', 'x', 0, 100], ['vertical', 'y', 0, 100]].map(([label, key, min, max]) =>
      `<label><span data-profile-text="${label}"></span><output></output><input type="range" min="${min}" max="${max}" data-crop="${kind}" data-axis="${key}"></label>`).join('')}
    ${button('center', `data-center="${kind}"`)}
  </details>`;
  root.innerHTML = `<div class="vf-profile" translate="no">
    <nav class="vf-profile-tabs" aria-label="Perfil">
      ${button('myProfile', 'data-profile-tab="profile" aria-pressed="true" aria-controls="vfProfileMain"')}
      ${button('subscriptions', 'data-profile-tab="pro" aria-pressed="false" aria-controls="vfProfilePro"')}
    </nav>
    <div id="vfProfileMain">
      <section class="vf-profile-card" aria-labelledby="vfProfileName">
        <div class="vf-profile-cover-wrap">
          <div class="vf-profile-cover" data-frame="banner" aria-hidden="true"></div>
          ${button('playBanner', 'data-banner-motion hidden')}
        </div>
        <div class="vf-profile-body">
          <div class="vf-profile-avatar" data-frame="avatar" aria-hidden="true">${portrait}</div>
          <div class="vf-profile-identity">
            <h3 id="vfProfileName"></h3><span data-profile-username hidden></span>
          </div>
          ${button('edit', 'data-edit aria-controls="vfProfileEditor" aria-expanded="false"')}
          <div class="vf-profile-bio" data-profile-bio hidden><h4 data-profile-text="about"></h4><p></p></div>
        </div>
      </section>
      <section class="vf-profile-editor" id="vfProfileEditor" hidden>
        <form data-profile-form>
          <fieldset data-profile-fields>
            <legend data-profile-text="edit"></legend>
            <div class="vf-profile-field-grid">
              <label><span data-profile-text="name"></span><input name="name" maxlength="60" autocomplete="off"></label>
              <label><span data-profile-text="username"></span><input name="username" maxlength="30" autocomplete="off" autocapitalize="none" spellcheck="false"><small data-profile-text="usernameHint"></small></label>
            </div>
            <label><span data-profile-text="about"></span><textarea name="bio" maxlength="300" rows="3"></textarea></label>
            <div class="vf-profile-field-grid vf-profile-media">
              <section>
                <h4 data-profile-text="avatar"></h4>
                <p data-profile-text="photoHint"></p>
                <div class="vf-profile-actions">
                  ${button('choosePhoto', 'data-pick="avatar"')}
                  ${button('avatarReset', 'data-reset="avatar"')}
                </div>
                <span class="vf-profile-caption" data-profile-text="sample"></span>
                <div class="vf-profile-presets">${presets.map(preset => `<button type="button" data-preset="${preset}" aria-pressed="false"><img src="${new URL(`av-${preset}.webp`, assets)}" alt=""></button>`).join('')}</div>
                <details class="vf-profile-online">
                  <summary data-profile-text="importPhoto"></summary>
                  <label class="vf-profile-link"><span data-profile-text="importHelp"></span><input type="text" inputmode="url" placeholder="https://…" data-profile-url autocomplete="off" autocapitalize="none"></label>
                  ${button('importButton', 'data-profile-import')}
                  <a data-profile-text="browser" data-profile-link target="_blank" rel="noopener noreferrer" hidden></a>
                </details>
                ${cropFields('avatar')}
              </section>
              <section>
                <h4 data-profile-text="banner"></h4>
                <p data-profile-text="bannerHint"></p>
                <div class="vf-profile-actions">
                  ${button('chooseBanner', 'data-pick="banner"')}
                  ${button('bannerReset', 'data-reset="banner"')}
                </div>
                ${cropFields('banner')}
              </section>
            </div>
            <div class="vf-profile-actions vf-profile-save">
              <button class="vf-profile-btn vf-profile-primary" type="submit" data-profile-text="save"></button>
              ${button('cancel', 'data-profile-cancel')}
            </div>
          </fieldset>
        </form>
      </section>
      <p class="vf-profile-status" role="status" data-profile-status></p>
      <p class="vf-profile-note" data-profile-text="local"></p>
    </div>
    <section id="vfProfilePro" class="vf-pro" hidden aria-labelledby="vfProTitle">
      <div class="vf-pro-intro"><p data-profile-text="concept"></p><h3 id="vfProTitle" data-profile-text="pro"></h3><p data-profile-text="proHelp"></p></div>
      <ul class="vf-pro-benefits">${[['proLooks','proLooksHelp'], ['proPresets','proPresetsHelp'], ['proLibrary','proLibraryHelp']].map(([title, description]) =>
        `<li><strong data-profile-text="${title}"></strong><span data-profile-text="${description}"></span></li>`).join('')}</ul>
    </section>
    <input type="file" data-profile-file hidden>
  </div>`;
  const form = select('[data-profile-form]');
  const fields = select('[data-profile-fields]');
  const editor = select('#vfProfileEditor');
  const edit = select('[data-edit]');
  const status = select('[data-profile-status]');
  const input = select('[data-profile-file]');
  const onlineInput = select('[data-profile-url]');
  const onlineLink = select('[data-profile-link]');
  const motionButton = select('[data-banner-motion]');
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const objectUrls = new Map();
  let profile = blankProfile();
  let draft;
  let database;
  let statusKey = '';
  let motionOverride = null;

  function message(key = '', error = false) {
    statusKey = key;
    status.textContent = key ? t(key) : '';
    status.classList.toggle('is-error', error);
  }

  function translate() {
    for (const element of all('[data-profile-text]')) element.textContent = t(element.dataset.profileText);
    for (const button of all('[data-preset]')) button.setAttribute('aria-label', t(button.dataset.preset));
    root.querySelector('nav').setAttribute('aria-label', t('myProfile'));
    if (!(draft || profile).name) select('#vfProfileName').textContent = t('myProfile');
    if (statusKey) status.textContent = t(statusKey);
    syncMotionButton();
  }

  function openDatabase() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open('videoflow-profile', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('profile');
      request.onsuccess = () => resolve(request.result);
      request.onerror = request.onblocked = () => reject(new Error('storage'));
    });
  }

  function load() {
    return new Promise((resolve, reject) => {
      const request = database.transaction('profile').objectStore('profile').get('local');
      request.onsuccess = () => resolve(request.result ?? blankProfile());
      request.onerror = () => reject(new Error('storage'));
    });
  }

  function save(value) {
    return new Promise((resolve, reject) => {
      const transaction = database.transaction('profile', 'readwrite');
      transaction.oncomplete = resolve;
      transaction.onabort = () => reject(new Error('storage'));
      transaction.objectStore('profile').put(value, 'local');
    });
  }

  function syncMotion() {
    const allowed = motionOverride ?? !reducedMotion.matches;
    const show = allowed && root.classList.contains('active-page') && !select('#vfProfileMain').hidden && !document.hidden;
    for (const video of all('video')) {
      const details = video.closest('details');
      if (show && (!details || details.open && !editor.hidden)) video.play().catch(syncMotionButton);
      else video.pause();
    }
    syncMotionButton();
  }

  function syncMotionButton() {
    const video = select('[data-frame="banner"] video');
    motionButton.hidden = !video;
    motionButton.textContent = t(video && !video.paused ? 'pauseBanner' : 'playBanner');
  }

  function paintFrame(frame, kind, value, url) {
    if (frame.dataset.mediaUrl !== url) {
      const video = frame.querySelector('video');
      if (video) { video.pause(); video.removeAttribute('src'); video.load(); }
      frame.replaceChildren();
      frame.dataset.mediaUrl = url;
      if (!url && kind === 'avatar') frame.innerHTML = portrait;
      if (url) {
        const media = document.createElement(value.blob?.type.startsWith('video/') ? 'video' : 'img');
        if (media instanceof HTMLVideoElement) {
          media.muted = true;
          media.defaultMuted = true;
          media.loop = true;
          media.playsInline = true;
          media.preload = 'metadata';
          media.addEventListener('playing', syncMotionButton);
          media.addEventListener('pause', syncMotionButton);
        } else media.alt = '';
        media.src = url;
        frame.append(media);
      }
    }
    frame.style.setProperty('--vf-x', value.x + '%');
    frame.style.setProperty('--vf-y', value.y + '%');
    frame.style.setProperty('--vf-zoom', value.zoom / 100);
  }

  function renderFrame(kind, value) {
    const previous = objectUrls.get(kind);
    const current = value.blob || value.preset || '';
    if (previous?.source !== current) {
      if (kind === 'banner') motionOverride = null;
      if (previous?.source instanceof Blob) URL.revokeObjectURL(previous.url);
      const url = value.blob ? URL.createObjectURL(value.blob) : value.preset ? new URL(`av-${value.preset}.webp`, assets).href : '';
      objectUrls.set(kind, {source:current, url});
    }
    const {url} = objectUrls.get(kind);
    const panel = select(`[data-crop-panel="${kind}"]`);
    panel.hidden = !current;
    paintFrame(select(`[data-frame="${kind}"]`), kind, value, url);
    paintFrame(panel.querySelector('[data-crop-preview]'), kind, value, panel.open && draft ? url : '');
    for (const slider of panel.querySelectorAll('input')) {
      slider.value = value[slider.dataset.axis];
      slider.previousElementSibling.textContent = slider.value + ' %';
    }
    if (kind === 'banner') syncMotion();
  }

  function render() {
    const value = draft || profile;
    select('#vfProfileName').textContent = value.name || t('myProfile');
    const username = select('[data-profile-username]');
    username.hidden = !value.username;
    username.textContent = '@' + value.username;
    const bio = select('[data-profile-bio]');
    bio.hidden = !value.bio;
    bio.querySelector('p').textContent = value.bio;
    for (const kind of ['avatar', 'banner']) renderFrame(kind, value[kind]);
    for (const control of all('[data-preset]')) control.setAttribute('aria-pressed', String(value.avatar.preset === control.dataset.preset));
  }

  function setEditing(enabled) {
    editor.hidden = !enabled;
    root.classList.toggle('is-editing-profile', enabled);
    edit.setAttribute('aria-expanded', String(enabled));
    edit.hidden = enabled;
    if (enabled) {
      draft = structuredClone(profile);
      for (const key of ['name', 'username', 'bio']) {
        form.elements[key].setCustomValidity('');
        form.elements[key].value = draft[key];
      }
      requestAnimationFrame(() => form.elements.name.focus({preventScroll:true}));
    } else {
      draft = undefined;
      onlineInput.value = '';
      onlineLink.hidden = true;
    }
    message();
    render();
  }

  function validateMedia(blob, kind) {
    const video = blob.type.startsWith('video/');
    return new Promise((resolve, reject) => {
      const media = video ? document.createElement('video') : new Image();
      const url = URL.createObjectURL(blob);
      const error = kind === 'avatar' ? 'invalid' : 'mediaInvalid';
      const settle = key => {
        clearTimeout(timer);
        media.onload = media.onerror = media.onloadedmetadata = null;
        if (video) { media.removeAttribute('src'); media.load(); }
        URL.revokeObjectURL(url);
        if (key) reject(new Error(key));
        else resolve(blob);
      };
      const timer = setTimeout(() => settle(error), 10000);
      media.onerror = () => settle(error);
      if (video) {
        media.preload = 'metadata';
        media.onloadedmetadata = () => settle(!media.videoWidth ? error :
          !Number.isFinite(media.duration) || media.duration <= 0 || media.duration > 30 ? 'duration' : '');
      } else media.onload = () => settle(media.naturalWidth && media.naturalHeight ? '' : error);
      media.src = url;
    });
  }

  async function assignMedia(kind, blob, filename = '') {
    if (!blob?.size || blob.size > limit[kind]) throw new Error(blob?.size ? 'size' : 'invalid');
    const extension = filename.split('.').at(-1)?.toLowerCase();
    const accepted = kind === 'banner' ? {...imageTypes, ...videoTypes} : imageTypes;
    const type = Object.values(accepted).includes(blob.type) ? blob.type : accepted[extension];
    if (!type) throw new Error(kind === 'avatar' ? 'invalid' : 'mediaInvalid');
    draft[kind] = {blob:await validateMedia(blob.slice(0, blob.size, type), kind), ...crop()};
    render();
  }

  async function perform(action) {
    if (!draft || fields.disabled) return;
    fields.disabled = true;
    message('busy');
    try { await action(); message(); }
    catch (error) {
      message(Object.hasOwn(copy, error.message) ? error.message : 'storage', true);
      window.VideoFlowSounds?.play('error');
    } finally { fields.disabled = false; }
  }

  async function downloadPhoto() {
    await perform(async () => {
      onlineLink.hidden = true;
      let url;
      try {
        url = new URL(onlineInput.value.trim());
        if (url.protocol !== 'https:' || url.username || url.password) throw new Error();
      } catch { throw new Error('link'); }
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 12000);
      let response;
      const parts = [];
      let bytes = 0;
      try {
        response = await fetch(url, {mode:'cors', credentials:'omit', referrerPolicy:'no-referrer', signal:controller.signal});
        if (!response.ok) throw new Error('network');
        if (Number(response.headers.get('content-length')) > limit.avatar) throw new Error('size');
        const reader = response.body.getReader();
        try {
          while (true) {
            const {done, value} = await reader.read();
            if (done) break;
            bytes += value.length;
            if (bytes > limit.avatar) throw new Error('size');
            parts.push(value);
          }
        } finally { reader.releaseLock(); }
      } catch (error) {
        if (error.message === 'size') { controller.abort(); throw error; }
        onlineLink.href = url.href;
        onlineLink.hidden = false;
        throw new Error('network');
      } finally { clearTimeout(timer); }
      const type = response.headers.get('content-type')?.split(';')[0]?.trim().toLowerCase();
      const filename = new URL(response.url || url.href).pathname;
      await assignMedia('avatar', new Blob(parts, {type}), filename);
    });
  }

  async function choose(kind) {
    if (!window.Native) {
      input.dataset.kind = kind;
      input.accept = Object.values(kind === 'avatar' ? imageTypes : {...imageTypes, ...videoTypes}).join(',');
      input.value = '';
      input.click();
      return;
    }
    await perform(async () => {
      const file = await rpc('pick', {kind:kind === 'avatar' ? 'profileAvatar' : 'profileBanner'});
      if (!file?.path) return;
      try {
        const response = await fetch(file.path);
        if (!response.ok) throw new Error('mediaInvalid');
        const blob = await response.blob();
        await assignMedia(kind, blob.slice(0, blob.size, file.mime || ''), file.name);
      } finally { await rpc('releaseProfileMedia', {path:file.path}).catch(() => {}); }
    });
  }

  input.addEventListener('change', () => {
    const file = input.files[0];
    if (file) perform(() => assignMedia(input.dataset.kind, file, file.name));
  });
  form.addEventListener('input', event => {
    if (!draft) return;
    const target = event.target;
    if (target.dataset.crop) {
      draft[target.dataset.crop][target.dataset.axis] = Number(target.value);
      renderFrame(target.dataset.crop, draft[target.dataset.crop]);
    } else if (['name','username','bio'].includes(target.name)) {
      target.setCustomValidity('');
      const value = target.value.trim();
      draft[target.name] = target.name === 'username' ? value.replace(/^@/, '') : value;
      render();
    }
  });
  form.addEventListener('submit', event => {
    event.preventDefault();
    if (!draft || fields.disabled) return;
    if (draft.username && !/^[\p{L}\p{N}._]+$/u.test(draft.username)) {
      form.elements.username.setCustomValidity(t('usernameHint'));
      form.elements.username.reportValidity();
      return;
    }
    perform(async () => {
      await save(draft);
      profile = draft;
      setEditing(false);
      edit.focus({preventScroll:true});
    }).then(() => { if (!draft) message('saved'); });
  });
  root.addEventListener('click', event => {
    const control = event.target.closest('button');
    if (!control || control.disabled) return;
    const {profileTab, pick, preset, reset, center} = control.dataset;
    if (profileTab) {
      select('#vfProfileMain').hidden = profileTab !== 'profile';
      select('#vfProfilePro').hidden = profileTab !== 'pro';
      for (const tab of all('[data-profile-tab]')) tab.setAttribute('aria-pressed', String(tab === control));
      syncMotion();
    } else if (control === motionButton) {
      motionOverride = select('[data-frame="banner"] video').paused;
      syncMotion();
    } else if (control.matches('[data-edit]')) setEditing(true);
    else if (!draft) return;
    else if (control.matches('[data-profile-cancel]')) { setEditing(false); edit.focus(); }
    else if (control.matches('[data-profile-import]')) downloadPhoto();
    else if (pick) choose(pick);
    else if (preset || reset || center) {
      if (preset) draft.avatar = {preset, ...crop()};
      if (reset) draft[reset] = crop();
      if (center) Object.assign(draft[center], crop());
      render();
    }
  });
  onlineInput.addEventListener('input', () => { onlineLink.hidden = true; });
  for (const panel of all('[data-crop-panel]')) panel.addEventListener('toggle', () => {
    const kind = panel.dataset.cropPanel;
    renderFrame(kind, (draft || profile)[kind]);
  });
  onlineInput.addEventListener('keydown', event => {
    if (event.key === 'Enter') { event.preventDefault(); downloadPhoto(); }
  });
  new MutationObserver(translate).observe(document.documentElement, {attributes:true, attributeFilter:['lang']});
  new MutationObserver(syncMotion).observe(root, {attributes:true, attributeFilter:['class']});
  document.addEventListener('visibilitychange', syncMotion);
  reducedMotion.addEventListener('change', syncMotion);
  translate();
  render();
  edit.disabled = true;
  (async () => {
    try { database = await openDatabase(); profile = await load(); edit.disabled = false; render(); }
    catch { message('storage', true); }
  })();
})();
