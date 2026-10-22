export const THEME_STORAGE_KEY = "dudecide-theme";

export type ThemeChoice = "light" | "dark";

// Runs before paint so an explicit choice never flashes the other theme.
export const themeBootstrapScript = `(function(){try{var t=localStorage.getItem("${THEME_STORAGE_KEY}");if(t==="light"||t==="dark"){document.documentElement.dataset.theme=t;}}catch(e){}})();`;
