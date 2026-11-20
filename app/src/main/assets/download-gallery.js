const platformFilter = $('#galleryPlatform');
const platformIcons = new Map($$('[data-source]').map(button => [button.dataset.source, button.querySelector('img').src]));
for (const source of platformIcons.keys()) platformFilter.add(new Option(source, source));
platformFilter.addEventListener('change', renderDownloadGallery);
let galleryVideos = [];
let gallerySize = -1;
let galleryLoading = false;

function renderDownloadGallery() {
  const platform = platformFilter.value;
  const videos = platform ? galleryVideos.filter(video => video.source === platform) : galleryVideos;
  const template = $('#galleryCard').content.firstElementChild;
  const grid = document.createDocumentFragment();
  for (const video of videos) {
    const card = template.cloneNode(true);
    card.dataset.uri = video.uri;
    card.dataset.name = video.title;
    card.dataset.mime = video.mime;
    const thumbnail = card.querySelector('.gallery-thumb');
    thumbnail.addEventListener('error', () => { thumbnail.hidden = true; }, {once: true});
    thumbnail.src = '/download-thumbnails/' + encodeURIComponent(video.id);
    const logo = card.querySelector('.gallery-site');
    logo.src = platformIcons.get(video.source);
    logo.alt = video.source;
    const title = card.querySelector('.gallery-title');
    title.textContent = title.title = video.title;
    const time = card.querySelector('.gallery-duration');
    time.textContent = duration(video.duration);
    time.hidden = !time.textContent;
    grid.append(card);
  }
  $('#galleryGrid').replaceChildren(grid);
  const message = $('#galleryEmpty');
  message.hidden = videos.length > 0;
  message.innerHTML = videos.length ? '' : empty(
    platform ? 'emptyPlatform' : 'emptyGallery',
    platform ? 'emptyPlatformHelp' : 'emptyGalleryHelp'
  );
}

async function loadDownloadGallery(force = false) {
  if (galleryLoading || (!force && snapshot.gallerySize === gallerySize)) return;
  galleryLoading = true;
  const status = $('#galleryStatus');
  if (gallerySize < 0) {
    status.textContent = t('loadingGallery');
    status.hidden = false;
  }
  try {
    const {size, videos} = await rpc('downloadGallery');
    gallerySize = size;
    galleryVideos = videos;
    renderDownloadGallery();
    status.hidden = true;
  } catch (error) {
    status.textContent = t(error.message);
    status.hidden = false;
  } finally {
    galleryLoading = false;
  }
}
