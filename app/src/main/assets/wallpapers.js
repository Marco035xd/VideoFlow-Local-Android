const wallpaperCopy = {
  wallpaperTitle: ['Fondos de pantalla', 'Wallpapers', 'Papéis de parede'],
  wallpaperAdd: ['Añadir foto', 'Add photo', 'Adicionar foto'],
  wallpaperHelp: ['Toca una miniatura para aplicar el fondo. Las fotos quedan guardadas en esta galería.',
    'Tap a thumbnail to apply the wallpaper. Photos are saved in this gallery.',
    'Toque em uma miniatura para aplicar o fundo. As fotos ficam salvas nesta galeria.'],
  wallpaperTheme: ['Fondo del tema', 'Theme background', 'Fundo do tema'],
  wallpaperOpacity: ['Opacidad del fondo', 'Wallpaper opacity', 'Opacidade do fundo'],
  wallpaperSaved: ['Fondo aplicado y guardado.', 'Wallpaper applied and saved.', 'Papel de parede aplicado e salvo.'],
  wallpaperPhoto: ['Foto guardada', 'Saved photo', 'Foto salva'],
  wallpaper_invalid: ['Elige una foto JPG, PNG o WebP válida.', 'Choose a valid JPG, PNG or WebP photo.', 'Escolha uma foto JPG, PNG ou WebP válida.'],
  wallpaper_too_large: ['Elige una foto de hasta 15 MB y 40 megapíxeles.',
    'Choose a photo up to 15 MB and 40 megapixels.', 'Escolha uma foto de até 15 MB e 40 megapixels.'],
  wallpaper_save_failed: ['No se pudo guardar la foto.', 'Could not save the photo.', 'Não foi possível salvar a foto.'],
  wallpaper_missing: ['Selecciona un fondo guardado.', 'Select a saved wallpaper.', 'Selecione um papel de parede salvo.'],
  wallpaper_opacity_invalid: ['Elige una opacidad entre 25 % y 100 %.', 'Choose an opacity from 25% to 100%.', 'Escolha uma opacidade entre 25% e 100%.'],
};

async function initMobileWallpapers() {
  if (!window.Native) return;
  const gallery = $('#wallpaperGallery');
  const add = $('#wallpaperAdd');
  const status = $('#wallpaperStatus');
  const opacity = $('#wallpaperOpacity');
  const opacityValue = $('#wallpaperOpacityValue');
  let pending = false;
  let activePhoto = null;
  let queuedOpacity = null;

  function previewOpacity(value) {
    opacityValue.textContent = `${value} %`;
    document.body.style.setProperty('--wallpaper-opacity', String(Number(value) / 100));
  }

  function render({active, photos}) {
    activePhoto = photos.find(photo => photo.id === active) || null;
    const imagePath = '/wallpaper-images/';
    document.body.classList.toggle('has-app-wallpaper', Boolean(activePhoto));
    if (activePhoto) document.body.style.setProperty('--app-wallpaper', `url("${imagePath}${encodeURIComponent(activePhoto.id)}.webp")`);
    else document.body.style.removeProperty('--app-wallpaper');
    opacity.value = String(queuedOpacity?.id === active ? queuedOpacity.opacity : activePhoto?.opacity ?? 54);
    previewOpacity(opacity.value);

    const cards = new Map([...gallery.querySelectorAll('[data-wallpaper]')].map(card => [card.dataset.wallpaper, card]));
    let previous = cards.get('');
    for (const photo of photos) {
      if (cards.has(photo.id)) {
        previous = cards.get(photo.id);
        continue;
      }
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'wallpaper-card';
      button.dataset.wallpaper = photo.id;
      const image = document.createElement('img');
      image.className = 'wallpaper-thumb';
      image.alt = '';
      image.src = `${imagePath}${encodeURIComponent(photo.id)}-thumb.webp`;
      image.loading = 'lazy';
      const name = document.createElement('span');
      name.className = 'wallpaper-name';
      if (photo.name) name.textContent = photo.name;
      else {
        name.dataset.i = 'wallpaperPhoto';
        name.textContent = t('wallpaperPhoto');
      }
      button.append(image, name);
      previous.after(button);
      previous = button;
      cards.set(photo.id, button);
    }
    for (const [id, button] of cards) button.setAttribute('aria-pressed', String(id === active));
  }

  async function change(method, data = {}, savingOpacity = false) {
    if (pending) return;
    pending = true;
    add.disabled = true;
    gallery.setAttribute('aria-busy', 'true');
    if (!savingOpacity) opacity.disabled = true;
    status.textContent = '';
    status.classList.remove('is-error');
    try {
      const result = await rpc(method, data);
      if (result?.photos) {
        render(result);
        if (method !== 'wallpapers') status.textContent = t('wallpaperSaved');
      }
    } catch (error) {
      status.textContent = t(error.message);
      status.classList.add('is-error');
      if (savingOpacity && !queuedOpacity) {
        opacity.value = String(activePhoto.opacity);
        previewOpacity(opacity.value);
      }
    } finally {
      pending = false;
      add.disabled = false;
      opacity.disabled = !activePhoto;
      gallery.setAttribute('aria-busy', 'false');
      saveOpacity();
    }
  }

  function saveOpacity() {
    if (pending || !queuedOpacity) return;
    const changes = queuedOpacity;
    queuedOpacity = null;
    change('wallpaperOpacity', changes, true);
  }

  add.addEventListener('click', () => change('pick', {kind:'wallpaper'}));
  gallery.addEventListener('click', event => {
    const button = event.target.closest('[data-wallpaper]');
    if (button && button.getAttribute('aria-pressed') !== 'true') change('wallpaperSelect', {id:button.dataset.wallpaper});
  });
  opacity.addEventListener('input', () => previewOpacity(opacity.value));
  opacity.addEventListener('change', () => {
    if (!activePhoto) return;
    queuedOpacity = {id:activePhoto.id, opacity:Number(opacity.value)};
    saveOpacity();
  });
  await change('wallpapers');
}
