import type { Config } from "tailwindcss";

const token = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

const config: Config = {
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./lib/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        bg: token("bg"),
        surface: token("surface"),
        "surface-2": token("surface-2"),
        ink: token("ink"),
        "on-ink": token("on-ink"),
        muted: token("muted"),
        line: token("line"),
        accent: token("accent"),
        "on-accent": token("on-accent"),
        "accent-text": token("accent-text"),
        "accent-soft": token("accent-soft"),
        "accent-soft-ink": token("accent-soft-ink"),
        "warn-soft": token("warn-soft"),
        "warn-ink": token("warn-ink"),
        "risk-soft": token("risk-soft"),
        "risk-ink": token("risk-ink"),
        danger: token("danger"),
        // Legacy names kept so older components follow the new tokens.
        mist: token("on-ink"),
        sand: token("surface-2"),
        ember: token("risk-ink"),
        pine: token("accent"),
        sky: token("accent-soft"),
      },
      fontFamily: {
        body: "var(--font-body)",
        mono: "var(--font-mono-ui)",
      },
      boxShadow: {
        panel: "var(--shadow-panel)",
      },
      borderRadius: {
        "4xl": "2rem",
      },
    },
  },
  plugins: [],
};

export default config;
