chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.action === 'getPageData') {
    sendResponse(extractPageData());
  }
  return true;
});

function extractPageData() {
  const url = location.href;

  if (url.includes('/serie/')) return extractSerieData();
  if (url.includes('/capitulo/')) return extractCapituloData();
  if (url.includes('/cartelera-series') || url === 'https://www3.seriesmetro.net/' || url.includes('/page/')) return extractListaData();

  return { type: 'unknown', url };
}

function extractListaData() {
  const items = [];
  document.querySelectorAll('h2, h3').forEach(h => {
    const a = h.closest('a') || h.querySelector('a') || h.previousElementSibling;
    const link = h.closest('[href]') || document.querySelector(`a[href*="/serie/"]:has(img)`);
    const parent = h.closest('li, div, article');
    if (!parent) return;
    const anchor = parent.querySelector('a[href*="/serie/"]');
    const img = parent.querySelector('img');
    const title = h.textContent.trim();
    if (anchor && title && !items.find(i => i.url === anchor.href)) {
      items.push({ title, url: anchor.href, img: img ? img.src : null });
    }
  });

  const nextPage = document.querySelector('a[href*="/page/"]:last-of-type, .next a');

  return { type: 'lista', items, nextPage: nextPage ? nextPage.href : null };
}

function extractSerieData() {
  const title = document.querySelector('h1')?.textContent.trim() || '';
  const img = document.querySelector('.serie-img img, img[src*="tmdb"]')?.src || '';
  const desc = document.querySelector('p')?.textContent.trim() || '';
  const capitulos = [];

  document.querySelectorAll('a[href*="/capitulo/"]').forEach(a => {
    const text = a.textContent.trim() || a.closest('li, div')?.querySelector('h2, h3')?.textContent.trim() || '';
    if (text && !capitulos.find(c => c.url === a.href)) {
      capitulos.push({ titulo: text, url: a.href });
    }
  });

  return { type: 'serie', title, img, desc, capitulos };
}

function extractCapituloData() {
  const title = document.querySelector('h1')?.textContent.trim() || '';

  const embedUrls = [];
  document.querySelectorAll('a[href*="trembed"], iframe[src*="trembed"]').forEach(el => {
    const u = el.href || el.src;
    if (u) embedUrls.push(u);
  });

  const rawHtml = document.body.innerHTML;
  const tridMatch = rawHtml.match(/trid[=&](\d+)/);
  const trid = tridMatch ? tridMatch[1] : null;

  const opciones = [];
  document.querySelectorAll('.options a, [class*="option"] a, [href*="#options"]').forEach(a => {
    const text = a.textContent.trim();
    const m = (a.href || a.getAttribute('href') || '').match(/options-(\d+)/);
    if (text) opciones.push({ label: text, index: m ? parseInt(m[1]) : opciones.length });
  });

  const prev = document.querySelector('a[href*="/capitulo/"]:first-of-type')?.href || null;
  const next = document.querySelector('.next-ep a, a[aria-label="Siguiente"]')?.href || null;

  return { type: 'capitulo', title, trid, embedUrls, opciones, prev, next };
}
