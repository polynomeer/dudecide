import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./lib/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        ink: "var(--color-ink)",
        mist: "var(--color-mist)",
        sand: "var(--color-sand)",
        ember: "var(--color-ember)",
        pine: "var(--color-pine)",
        sky: "var(--color-sky)",
      },
      boxShadow: {
        panel: "0 18px 60px rgba(15, 23, 42, 0.14)",
      },
      borderRadius: {
        "4xl": "2rem",
      },
    },
  },
  plugins: [],
};

export default config;
