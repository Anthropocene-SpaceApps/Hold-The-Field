// Colours shared by the canvas-drawn parts of the UI (the CSS uses the same values as custom properties).
// Same palette as the desktop build's ui/Theme.java.
export const COLORS = {
  text: '#eef4fb', muted: '#8fa3bc', faint: '#5f7089',
  accent: '#4da3ff', good: '#5bd18a', warn: '#ffb84d', bad: '#ff5d5d',
  water: '#58a9e8', crop: '#e6be4b', soil: '#c49a6c', rain: '#7fb8ff', temp: '#ff9a5c', loss: '#cccccc',
};

/** Inline SVG icons (24x24, currentColor). */
const P = {
  bund: '<path d="M3 17h18v3H3zM4 13h7v3H4zm9 0h7v3h-7zM7 9h10v3H7z"/>',
  sickle: '<path d="M14.5 3.5a8 8 0 0 1 2.9 11.2 8 8 0 0 1-9.6 2.8 6.6 6.6 0 0 0 8.2-4.1 6.6 6.6 0 0 0-1.5-9.9z"/><path d="M8.3 16.2 4 20.5l-1.5-1.5 4.3-4.3z"/>',
  satellite: '<path d="M10 4.5 13.5 8 8 13.5 4.5 10zM15 9.5l2.5-2.5 3 3-2.5 2.5zM3 15l2.5-2.5 3 3L6 18zM12.5 14.5c1.5 0 3 1.5 3 3h1.5a4.5 4.5 0 0 0-4.5-4.5zM12.5 11.5a6 6 0 0 1 6 6H20a7.5 7.5 0 0 0-7.5-7.5z"/>',
  chart: '<path d="M4 20V10h3v10zm6.5 0V4h3v16zM17 20v-7h3v7z"/>',
  drop: '<path d="M12 2.5S5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 12 2.5 12 2.5z"/>',
  coin: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v10M9.5 9.5h4a1.5 1.5 0 0 1 0 3h-3a1.5 1.5 0 0 0 0 3h4" stroke="#1b2333" stroke-width="1.6" fill="none"/>',
  rain: '<path d="M7 14a4.5 4.5 0 0 1-.6-9 6 6 0 0 1 11.4 1.6A3.7 3.7 0 0 1 17.5 14z"/><path d="M8 16.5l-1.2 3M12 16.5l-1.2 3M16 16.5l-1.2 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
  thermo: '<path d="M10 4.5a2 2 0 0 1 4 0v9.2a4 4 0 1 1-4 0z"/>',
  soil: '<path d="M2 15h20v6H2z"/><path d="M12 14V8m0 0c0-2.5 2-4 4.5-4 0 2.5-2 4-4.5 4zm0 2c0-2-1.6-3.2-3.6-3.2 0 2 1.6 3.2 3.6 3.2z"/>',
  play: '<path d="M7 4.5v15l12-7.5z"/>',
  pause: '<path d="M6 4.5h4v15H6zm8 0h4v15h-4z"/>',
  wheat: '<path d="M12 21V8M12 8c-2-1-3-3-3-5 2 .5 3 2 3 5zm0 0c2-1 3-3 3-5-2 .5-3 2-3 5zm0 5c-2-1-3.5-3-3.5-5 2 .5 3.5 2 3.5 5zm0 0c2-1 3.5-3 3.5-5-2 .5-3.5 2-3.5 5zm0 5c-2-1-3.5-3-3.5-5 2 .5 3.5 2 3.5 5zm0 0c2-1 3.5-3 3.5-5-2 .5-3.5 2-3.5 5z" stroke="currentColor" stroke-width="1.3" fill="none"/>',
  menu: '<path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z"/>',
  sound: '<path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16 8.5a5 5 0 0 1 0 7M18.5 6a8.5 8.5 0 0 1 0 12" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linecap="round"/>',
  mute: '<path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16.5 9.5l5 5m0-5-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
  camera: '<path d="M4 8h3l2-2.5h6L17 8h3v11H4z"/><circle cx="12" cy="13.5" r="3.4" fill="#1b2333"/>',
};
export const icon = (name, cls = '') => `<svg class="ico ${cls}" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">${P[name] || ''}</svg>`;
