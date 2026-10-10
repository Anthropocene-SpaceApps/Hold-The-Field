// The product name lives here and nowhere else in the web build. Rename the game by editing this file
// (plus <title> in index.html and the README), nothing else needs to change.
export const BRAND = Object.freeze({
  name: 'Agrocene',
  // the title screen draws the name in two colours, like the desktop build
  nameParts: ['Agro', 'cene'],
  version: '1.1.0',
  tagline: 'The flood comes early. So do we.',
  // The farmer and his daughter from the video script. Code and the shared translation files say "Rahim" (the
  // desktop build's name); the web build shows these names instead (see persona() in i18n.js).
  farmer: { en: 'Hashem', bn: 'হাশেম' },
  daughter: { en: 'Nodi', bn: 'নদী' },
  team: 'Team Anthropocene',
  event: 'NASA SPACE APPS CHALLENGE 2026  /  FIELD SHIFT',
  storageKey: 'agrocene',
});
