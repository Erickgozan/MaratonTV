const BASE = 'https://www3.seriesmetro.net';
const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124',
  'Accept': 'text/html',
  'Accept-Language': 'es-MX,es;q=0.9',
};

// ── Estado ──────────────────────────────────────────────────────────────────
const state = {
  view: 'lista',       // lista | serie | capitulo
  currentSerie: null,
  currentCap: null,
  listaPage: 1,
  listaItems: [],
  history: [],
  searchQuery: '',
};

const content = document.getElementById('mainContent');

// ── BloodersTV Companion Integration ─────────────────────────────────────────
async function getSavedTvIp() {
  return new Promise((resolve) => {
    chrome.storage.local.get(['tvIp'], (result) => {
      resolve(result.tvIp || '');
    });
  });
}

async function saveTvIp(ip) {
  return new Promise((resolve) => {
    chrome.storage.local.set({ tvIp: ip }, () => {
      resolve();
    });
  });
}

async function checkTvConnection(silent = false) {
  const ip = await getSavedTvIp();
  const statusDot = document.getElementById('companionStatusDot');
  const ipInput = document.getElementById('tvIpInput');
  
  if (ipInput && ip) {
    ipInput.value = ip;
  }
  
  if (!ip) {
    statusDot.className = 'status-dot offline';
    return false;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 2000);
    
    const res = await fetch(`http://${ip}:9999/ping`, {
      method: "GET",
      signal: controller.signal
    });
    
    clearTimeout(timeoutId);
    
    if (res.ok) {
      statusDot.className = 'status-dot active';
      return true;
    }
  } catch (e) {
    // Fail silently or update dot
  }
  statusDot.className = 'status-dot offline';
  return false;
}

async function sendToTv(payload) {
  try {
    const ip = await getSavedTvIp();
    if (!ip) return;
    
    await fetch(`http://${ip}:9999/scrap`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
  } catch (e) {
    console.warn("Error enviando a BloodersTV companion:", e);
  }
}

// ── Eventos de Conexión ──────────────────────────────────────────────────────
document.getElementById('connectTvBtn')?.addEventListener('click', async () => {
  const ipInput = document.getElementById('tvIpInput');
  const ip = ipInput.value.trim().replace(/^https?:\/\//i, '').replace(/:9999\/?$/, '');
  
  if (!ip) return;
  await saveTvIp(ip);
  const success = await checkTvConnection();
  if (success) {
    alert("¡Enlazado con éxito a BloodersTV!");
  } else {
    alert("No se pudo conectar a la TV. Asegura que la app BloodersTV está abierta en Ajustes.");
  }
});

// Chequear conexión al iniciar
checkTvConnection();
setInterval(() => checkTvConnection(true), 10000);

// ── Parsers ──────────────────────────────────────────────────────────────────

async function fetchHtml(url) {
  const res = await fetch(url, { headers: HEADERS });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

function parseSeries(html) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');
  const items = [];
  doc.querySelectorAll('a[href*="/serie/"]').forEach(a => {
    if (items.find(i => i.url === a.href)) return;
    const img = a.querySelector('img');
    const h = a.querySelector('h2, h3') || a.closest('li,div')?.querySelector('h2,h3');
    const title = h?.textContent.trim() || img?.alt || '';
    const yearEl = a.querySelector('.year, [class*="year"]');
    const year = yearEl?.textContent.trim() || '';
    if (title) items.push({ title, url: a.href, img: img?.src || '', year });
  });
  const nextEl = doc.querySelector('a[href*="/page/"]:last-of-type, .next a, [class*="next"] a');
  return { items, nextPage: nextEl?.href || null };
}

function parseCapitulos(html) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');
  const caps = [];
  doc.querySelectorAll('a[href*="/capitulo/"]').forEach(a => {
    if (caps.find(c => c.url === a.href)) return;
    const h = a.querySelector('h2,h3') || a.closest('li,div')?.querySelector('h2,h3');
    const title = h?.textContent.trim() || a.textContent.trim();
    if (title) caps.push({ title, url: a.href });
  });
  const serieTitle = doc.querySelector('h1')?.textContent.trim() || '';
  const img = doc.querySelector('img[src*="tmdb"]')?.src || '';
  const descEl = doc.querySelector('.wp-content p, .serie-desc, p');
  const desc = descEl?.textContent.trim() || '';
  return { title: serieTitle, img, desc, capitulos: caps };
}

function parseCapitulo(html) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(html, 'text/html');

  // Título
  const title = doc.querySelector('h1')?.textContent.trim() || '';

  // trid desde el HTML
  const tridMatch = html.match(/trid[=&](\d+)/);
  const trid = tridMatch?.[1] || null;

  // Opciones de idioma (tabs)
  const opciones = [];
  doc.querySelectorAll('.options a, [class*="option"] a, [href*="#options-"]').forEach(a => {
    const label = a.textContent.trim();
    const m = (a.getAttribute('href') || '').match(/options-(\d+)/);
    if (label && !opciones.find(o => o.label === label)) {
      opciones.push({ label, index: m ? parseInt(m[1]) : opciones.length });
    }
  });

  // Links de embed en el HTML
  const embedLinks = [];
  doc.querySelectorAll('a[href*="trembed"], iframe[src*="trembed"]').forEach(el => {
    const u = el.href || el.src;
    if (u && !embedLinks.includes(u)) embedLinks.push(u);
  });

  // Nav anterior/siguiente
  const allCapLinks = [...doc.querySelectorAll('a[href*="/capitulo/"]')].map(a => a.href);

  return { title, trid, opciones, embedLinks, allCapLinks };
}

// ── Vistas ───────────────────────────────────────────────────────────────────

function renderLoading(msg = 'Cargando...') {
  content.innerHTML = `<div class="status"><span class="spinner"></span>${msg}</div>`;
}

function renderError(msg) {
  content.innerHTML = `<div class="status" style="color:#e5383b">⚠ ${msg}</div>`;
}

async function renderLista(page = 1, query = '') {
  renderLoading(query ? `Buscando "${query}"...` : 'Cargando catálogo...');
  state.view = 'lista';
  state.listaPage = page;

  try {
    let url;
    if (query) {
      url = `${BASE}/?s=${encodeURIComponent(query)}`;
    } else {
      url = page === 1 ? `${BASE}/cartelera-series/` : `${BASE}/cartelera-series/page/${page}/`;
    }

    const html = await fetchHtml(url);
    const { items, nextPage } = parseSeries(html);
    state.listaItems = items;

    if (!items.length) {
      content.innerHTML = `<div class="status">No se encontraron resultados.</div>`;
      return;
    }

    // Auto-transmit scraped items list to companion app
    sendToTv({
      type: 'lista',
      title: query ? `Búsqueda: ${query}` : 'Catálogo SeriesMetro',
      items: items
    });

    const grid = document.createElement('div');
    grid.className = 'serie-grid';

    items.forEach(item => {
      const card = document.createElement('div');
      card.className = 'serie-card';
      card.innerHTML = `
        <img src="${item.img}" alt="${item.title}" loading="lazy" onerror="this.style.display='none'">
        <div class="info">
          <div class="title">${item.title}</div>
          ${item.year ? `<div class="meta">${item.year}</div>` : ''}
        </div>
      `;
      card.addEventListener('click', () => renderSerie(item));
      grid.appendChild(card);
    });

    content.innerHTML = '';
    content.appendChild(grid);

    if (!query) {
      const pager = document.createElement('div');
      pager.className = 'pager';
      pager.innerHTML = `
        <button id="prevPage" ${page <= 1 ? 'disabled' : ''}>← Anterior</button>
        <span>Pág. ${page} / 273</span>
        <button id="nextPage" ${!nextPage ? 'disabled' : ''}>Siguiente →</button>
      `;
      content.appendChild(pager);
      document.getElementById('prevPage')?.addEventListener('click', () => renderLista(page - 1));
      document.getElementById('nextPage')?.addEventListener('click', () => renderLista(page + 1));
    }

  } catch (e) {
    renderError('Error cargando el catálogo. ¿Tienes conexión a internet?');
    console.error(e);
  }
}

async function renderSerie(item) {
  state.history.push({ view: 'lista', page: state.listaPage });
  state.currentSerie = item;
  state.view = 'serie';
  renderLoading(`Cargando ${item.title}...`);

  try {
    const html = await fetchHtml(item.url);
    const { title, img, desc, capitulos } = parseCapitulos(html);

    // Auto-transmit scraped chapters to companion app
    sendToTv({
      type: 'serie',
      title: title,
      img: img,
      desc: desc,
      capitulos: capitulos
    });

    content.innerHTML = `
      <div class="back-btn" id="backBtn">← Volver al catálogo</div>
      <div style="display:flex;gap:10px;align-items:flex-start;margin-bottom:12px">
        ${img ? `<img src="${img}" style="width:60px;border-radius:6px;flex-shrink:0" onerror="this.remove()">` : ''}
        <div>
          <div style="font-size:14px;font-weight:600;margin-bottom:4px">${title}</div>
          <div style="font-size:11px;color:var(--muted)">${capitulos.length} capítulo(s)</div>
        </div>
      </div>
      <div class="section-title">Capítulos</div>
      <div class="ep-list" id="epList"></div>
    `;

    document.getElementById('backBtn').addEventListener('click', () => {
      renderLista(state.listaPage, state.searchQuery);
    });

    const epList = document.getElementById('epList');
    capitulos.forEach((cap, i) => {
      const ep = document.createElement('div');
      ep.className = 'ep-item';
      ep.innerHTML = `
        <span class="ep-num">${i + 1}</span>
        <span class="ep-title">${cap.title}</span>
        <div class="ep-play">▶</div>
      `;
      ep.addEventListener('click', () => renderCapitulo(cap, i, capitulos));
      epList.appendChild(ep);
    });

  } catch (e) {
    renderError(`Error cargando la serie: ${e.message}`);
  }
}

async function renderCapitulo(cap, idx, allCaps) {
  state.history.push({ view: 'serie', item: state.currentSerie });
  state.currentCap = cap;
  state.view = 'capitulo';
  renderLoading('Cargando capítulo...');

  try {
    const html = await fetchHtml(cap.url);
    const { title, trid, opciones, embedLinks } = parseCapitulo(html);

    // Construir URLs de embed según las opciones disponibles
    const embedOptions = [];

    if (trid) {
      const opCount = Math.max(opciones.length, 1);
      for (let i = 0; i < opCount; i++) {
        const label = opciones[i]?.label || `Opción ${i + 1}`;
        const embedUrl = `${BASE}/?trembed=0&trid=${trid}&trtype=${i + 2}`;
        embedOptions.push({ label, url: embedUrl, index: i });
      }
    }

    // También agregar links de embed encontrados directamente
    embedLinks.forEach((u, i) => {
      if (!embedOptions.find(o => o.url === u)) {
        embedOptions.push({ label: `Enlace ${i + 1}`, url: u, index: embedOptions.length });
      }
    });

    // Auto-transmit scraped video options of chapter to companion app
    sendToTv({
      type: 'capitulo',
      title: title,
      trid: trid,
      opciones: opciones,
      embedUrls: embedLinks
    });

    // Colores por idioma
    const dotClass = (label) => {
      const l = label.toLowerCase();
      if (l.includes('latino') || l.includes('lat')) return 'dot dot-lat';
      if (l.includes('cast') || l.includes('español')) return 'dot dot-cast';
      if (l.includes('vose') || l.includes('sub')) return 'dot dot-vose';
      return 'dot dot-gen';
    };

    content.innerHTML = `
      <div class="back-btn" id="backBtn">← Volver a la serie</div>
      <div class="player-panel">
        <div class="player-title">${title}</div>

        ${trid ? `<div class="trid-info">trid: ${trid}</div>` : ''}

        <div>
          <div class="opciones-label">Selecciona idioma / opción:</div>
          <div class="opciones-grid" id="opcionesGrid">
            ${embedOptions.length === 0 ? `<div class="notice">No se detectaron opciones en este capítulo.<br>Abre la página manualmente.</div>` : ''}
          </div>
        </div>

        <div>
          <div class="opciones-label">Abrir en el sitio:</div>
          <div class="opciones-grid">
            <button class="opcion-btn" id="openSite">
              <span class="dot dot-gen"></span>
              Ver en SeriesMetro
            </button>
          </div>
        </div>

        <div class="nav-row">
          <button class="nav-btn" id="prevCap" ${idx <= 0 ? 'disabled' : ''}>← Cap. anterior</button>
          <button class="nav-btn" id="nextCap" ${idx >= allCaps.length - 1 ? 'disabled' : ''}>Cap. siguiente →</button>
        </div>
      </div>
    `;

    document.getElementById('backBtn').addEventListener('click', () => {
      renderSerie(state.currentSerie);
    });

    document.getElementById('openSite').addEventListener('click', () => {
      chrome.tabs.create({ url: cap.url });
    });

    const grid = document.getElementById('opcionesGrid');
    embedOptions.forEach(opt => {
      const btn = document.createElement('button');
      btn.className = 'opcion-btn';
      btn.innerHTML = `<span class="${dotClass(opt.label)}"></span>${opt.label}`;
      btn.addEventListener('click', () => {
        chrome.tabs.create({ url: opt.url });
      });
      grid.appendChild(btn);
    });

    document.getElementById('prevCap')?.addEventListener('click', () => {
      renderCapitulo(allCaps[idx - 1], idx - 1, allCaps);
    });
    document.getElementById('nextCap')?.addEventListener('click', () => {
      renderCapitulo(allCaps[idx + 1], idx + 1, allCaps);
    });

  } catch (e) {
    renderError(`Error: ${e.message}`);
  }
}

// ── Tabs ─────────────────────────────────────────────────────────────────────

document.querySelectorAll('.tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    const t = tab.dataset.tab;
    if (t === 'catalogo') renderLista(1);
    if (t === 'recientes') renderRecientes();
  });
});

async function renderRecientes() {
  renderLoading('Cargando recientes...');
  try {
    const html = await fetchHtml(`${BASE}/cartelera-series/`);
    const { items } = parseSeries(html);
    const recientes = items.slice(0, 10);

    content.innerHTML = `<div class="section-title">Últimas añadidas</div><div class="ep-list" id="recList"></div>`;
    const list = document.getElementById('recList');

    recientes.forEach(item => {
      const el = document.createElement('div');
      el.className = 'ep-item';
      el.innerHTML = `
        ${item.img ? `<img src="${item.img}" style="width:32px;height:46px;object-fit:cover;border-radius:4px;flex-shrink:0" onerror="this.remove()">` : ''}
        <span class="ep-title">${item.title}</span>
        <div class="ep-play">▶</div>
      `;
      el.addEventListener('click', () => renderSerie(item));
      list.appendChild(el);
    });
  } catch (e) {
    renderError('Error cargando recientes.');
  }
}

// ── Búsqueda ─────────────────────────────────────────────────────────────────

document.getElementById('searchBtn').addEventListener('click', () => {
  const q = document.getElementById('searchInput').value.trim();
  if (!q) { renderLista(1); return; }
  state.searchQuery = q;
  renderLista(1, q);
});

document.getElementById('searchInput').addEventListener('keydown', e => {
  if (e.key === 'Enter') document.getElementById('searchBtn').click();
});

// ── Init ─────────────────────────────────────────────────────────────────────

renderLista(1);
