import type { Config } from "tailwindcss";

// Tokens below are read directly off the Figma file
// (mVXoqLOgRfnt8LjjYiezUh), not the brand-package guideline doc, per the
// project rule that Figma governs visual specifics. Two deltas from the
// brand guideline worth knowing about:
//   - Figma's display/UI typeface is Rubik (Nunito was the brand-doc
//     suggestion) — Rubik is what's actually in every text node.
//   - Figma's blue is #3B82F6 / green is #10B981, close to but not
//     identical to the brand doc's #2563EB — using the Figma values.
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#1E1A17", // primary text
        muted: "#5C5651", // secondary text
        hairline: "#EAE5DC", // borders on warm-white cards
        surface: {
          cream: "#FCFAF6", // hero gradient start
          mist: "#F0F7FF", // hero gradient end
          sand: "#F5F1E9", // alternating section background
        },
        brand: {
          blue: "#3B82F6",
          // Filled controls with white text: #3B82F6 is 3.7:1 against white (fails WCAG
          // AA for normal-size text); the brand guideline's #2563EB is 5.2:1.
          blueStrong: "#2563EB",
          blueTint: "#EBF3FE",
          green: "#10B981",
          greenTint: "#EDFBF7",
          purple: "#8B5CF6",
          purpleTint: "#F5F0FF",
          amber: "#F59E0B",
          amberTint: "#FEF9EC",
          red: "#EF4444",
          redTint: "#FEECEC",
          // Text tones: the base hues above are for icons and fills. As small text on white
          // or on their own tint they fall below WCAG AA (e.g. red 3.8:1, amber 2.1:1).
          greenStrong: "#047857",
          purpleStrong: "#6D28D9",
          amberStrong: "#92400E",
          redStrong: "#B91C1C",
        },
        // The child space's palette (product directive §8): warm cream, soft mint,
        // lavender, blue, peach, warm yellow, muted coral. Each hue has a soft fill
        // and a deep tone that passes AA contrast for text/icons on its fill.
        kid: {
          cream: "#FFF8EE",
          paper: "#FFFDF8",
          mint: "#D6F5E8",
          mintDeep: "#17694E",
          lavender: "#ECE6FF",
          lavenderDeep: "#5B3CC4",
          sky: "#DDEBFF",
          skyDeep: "#1D4ED8",
          peach: "#FFE4D3",
          peachDeep: "#A8431C",
          sun: "#FFF0C2",
          sunDeep: "#8A5700",
          coral: "#FFD9D4",
          coralDeep: "#A63A30",
        },
      },
      fontFamily: {
        // Rubik carries English/Latin display + UI text; Noto Sans Khmer
        // carries every Khmer text node. Both are loaded as Google Fonts
        // in index.html.
        display: ["Rubik", "system-ui", "sans-serif"],
        khmer: ["Noto Sans Khmer", "system-ui", "sans-serif"],
      },
      borderRadius: {
        pill: "100px",
      },
      boxShadow: {
        // Figma's shadows are warm-tinted (brown, not pure black) — a
        // deliberate detail, not a generic soft-grey card shadow.
        soft: "0px 8px 12px 0px rgba(58,42,26,0.06)",
        nav: "0px 8px 24px 0px rgba(58,42,26,0.06)",
        tight: "0px 4px 6px 0px rgba(0,0,0,0.03)",
      },
      backgroundImage: {
        hero: "linear-gradient(212deg, #FCFAF6 25%, #F0F7FF 75%)",
        world: "radial-gradient(1200px 600px at 10% -10%, #ECE6FF 0%, transparent 60%), radial-gradient(900px 500px at 110% 10%, #D6F5E8 0%, transparent 55%), linear-gradient(180deg, #FFF8EE 0%, #FFFDF8 100%)",
      },
      // Motion with a job (directive §9): each keyframe communicates a state change -
      // arrival, success, "try again", attention on the next step - never decoration.
      // All of it is neutralised under prefers-reduced-motion (index.css).
      keyframes: {
        "pop-in": {
          "0%": { transform: "scale(0.85)", opacity: "0" },
          "60%": { transform: "scale(1.04)", opacity: "1" },
          "100%": { transform: "scale(1)" },
        },
        "rise-in": {
          "0%": { transform: "translateY(12px)", opacity: "0" },
          "100%": { transform: "translateY(0)", opacity: "1" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-6px)" },
        },
        cheer: {
          "0%, 100%": { transform: "translateY(0) rotate(0)" },
          "30%": { transform: "translateY(-14px) rotate(-4deg)" },
          "60%": { transform: "translateY(-4px) rotate(3deg)" },
        },
        nudge: {
          "0%, 100%": { transform: "translateX(0)" },
          "25%": { transform: "translateX(-6px)" },
          "75%": { transform: "translateX(6px)" },
        },
        "pulse-ring": {
          "0%": { boxShadow: "0 0 0 0 rgba(37,99,235,0.35)" },
          "100%": { boxShadow: "0 0 0 18px rgba(37,99,235,0)" },
        },
        "confetti-fall": {
          "0%": { transform: "translateY(-20px) rotate(0)", opacity: "1" },
          "100%": { transform: "translateY(260px) rotate(540deg)", opacity: "0" },
        },
        "fill-bar": {
          "0%": { transform: "scaleX(0)" },
          "100%": { transform: "scaleX(1)" },
        },
      },
      animation: {
        "pop-in": "pop-in 420ms cubic-bezier(0.2, 0.8, 0.2, 1) both",
        "rise-in": "rise-in 420ms cubic-bezier(0.2, 0.8, 0.2, 1) both",
        float: "float 4s ease-in-out infinite",
        cheer: "cheer 900ms ease-in-out",
        nudge: "nudge 420ms ease-in-out",
        "pulse-ring": "pulse-ring 1.6s ease-out infinite",
        "confetti-fall": "confetti-fall 1.6s ease-in forwards",
        "fill-bar": "fill-bar 700ms cubic-bezier(0.2, 0.8, 0.2, 1) both",
      },
    },
  },
  plugins: [],
} satisfies Config;
