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
          blueTint: "#EBF3FE",
          green: "#10B981",
          greenTint: "#EDFBF7",
          purple: "#8B5CF6",
          purpleTint: "#F5F0FF",
          amber: "#F59E0B",
          amberTint: "#FEF9EC",
          red: "#EF4444",
          redTint: "#FEECEC",
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
      },
    },
  },
  plugins: [],
} satisfies Config;
