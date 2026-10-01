import type { Config } from "tailwindcss";

/**
 * Bookeyond Kid — "Blue hour on the Mekong".
 *
 * Blue is the identity: the Tonle scale runs from night ink to morning mist and carries
 * structure, text and primary action. Warm colour is reserved for *light* (the current step,
 * success, focus, the buddy's glow) so it always means something. Lotus, palm and dusk are
 * quiet nature accents. Every text pairing noted below meets WCAG AA on its intended ground.
 */
const tonle = {
  50: "#F2F5FB",
  100: "#E3EAF7",
  200: "#C9D6EF",
  300: "#A2B8E3",
  400: "#6E8FD0",
  500: "#4468B8",
  600: "#2E4F9E", // links on paper (6.9:1)
  700: "#223D85", // primary action fill, white text 9.6:1
  800: "#182E66",
  900: "#11214D",
  950: "#0A1533", // night ink
};

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  // Touch screens: a tap must not leave a button looking hovered.
  future: { hoverOnlyWhenSupported: true },
  theme: {
    extend: {
      colors: {
        tonle,
        // Text: navy ink, never pure black. soft 7.4:1, faint 4.9:1 on paper.
        ink: { DEFAULT: "#0E1B3A", soft: "#46526E", faint: "#636E88" },
        paper: { DEFAULT: "#F6F7FB", raised: "#FFFFFF", sunk: "#EDF0F7" },
        line: { DEFAULT: "#DCE2EE", strong: "#C3CCDD" },
        // The one warm voice: light. Fill only; text on it uses light.deep (ink on fill 8.9:1).
        light: { DEFAULT: "#F3A83B", soft: "#FCEBD0", glow: "#FFD27A", deep: "#8A4A0B", ember: "#E0782F" },
        lotus: { DEFAULT: "#E48BA0", soft: "#FBE6EB", deep: "#9C3656" },
        palm: { DEFAULT: "#2E7A5A", soft: "#E3F1EA", deep: "#1E5A41" },
        dusk: { DEFAULT: "#6D5BA8", soft: "#ECE9F6", deep: "#4A3B86" },
        brick: { DEFAULT: "#C2412F", soft: "#FBE8E4", deep: "#9E2F20" },

        // Legacy names, remapped onto the new system while screens are rebuilt.
        muted: "#46526E",
        hairline: "#DCE2EE",
        surface: { cream: "#F6F7FB", mist: "#F2F5FB", sand: "#EDF0F7" },
        brand: {
          blue: tonle[500],
          blueStrong: tonle[700],
          blueTint: tonle[100],
          green: "#2E7A5A",
          greenTint: "#E3F1EA",
          greenStrong: "#1E5A41",
          purple: "#6D5BA8",
          purpleTint: "#ECE9F6",
          purpleStrong: "#4A3B86",
          amber: "#F3A83B",
          amberTint: "#FCEBD0",
          amberStrong: "#8A4A0B",
          red: "#C2412F",
          redTint: "#FBE8E4",
          redStrong: "#9E2F20",
        },
        kid: {
          cream: tonle[50],
          paper: "#FFFFFF",
          mint: "#E3F1EA",
          mintDeep: "#1E5A41",
          lavender: "#ECE9F6",
          lavenderDeep: "#4A3B86",
          sky: tonle[100],
          skyDeep: tonle[700],
          peach: "#FCEBD0",
          peachDeep: "#8A4A0B",
          sun: "#FCEBD0",
          sunDeep: "#8A4A0B",
          coral: "#FBE8E4",
          coralDeep: "#9E2F20",
        },
      },
      fontFamily: {
        // Display: Bricolage Grotesque (optical sizes). Text/UI: Kantumruy Pro, a Cambodian
        // design that sets Khmer and Latin in one rhythm. Both self-hosted (main.tsx).
        display: ['"Bricolage Grotesque Variable"', '"Kantumruy Pro Variable"', "system-ui", "sans-serif"],
        sans: ['"Kantumruy Pro Variable"', "system-ui", "sans-serif"],
        khmer: ['"Kantumruy Pro Variable"', "system-ui", "sans-serif"],
      },
      fontSize: {
        // A deliberate scale: small UI steps, generous display steps.
        "2xs": ["0.6875rem", { lineHeight: "1rem", letterSpacing: "0.01em" }],
        display: ["clamp(2.5rem, 1.6rem + 3.6vw, 4.75rem)", { lineHeight: "1.02", letterSpacing: "-0.035em" }],
        headline: ["clamp(1.875rem, 1.4rem + 1.9vw, 2.75rem)", { lineHeight: "1.08", letterSpacing: "-0.025em" }],
        title: ["clamp(1.375rem, 1.2rem + 0.7vw, 1.75rem)", { lineHeight: "1.2", letterSpacing: "-0.015em" }],
      },
      borderRadius: {
        pill: "999px",
        paper: "22px",
      },
      boxShadow: {
        // Depth is paper on paper: small offset, soft blur, tinted with night ink.
        soft: "0 1px 2px rgba(10,21,51,0.06), 0 8px 24px -12px rgba(10,21,51,0.18)",
        nav: "0 1px 0 rgba(10,21,51,0.06)",
        tight: "0 1px 2px rgba(10,21,51,0.08)",
        paper: "0 1px 1px rgba(10,21,51,0.05), 0 12px 32px -18px rgba(10,21,51,0.35)",
        lift: "0 2px 4px rgba(10,21,51,0.08), 0 24px 48px -24px rgba(10,21,51,0.45)",
        glow: "0 0 0 1px rgba(243,168,59,0.45), 0 0 32px 4px rgba(255,210,122,0.55)",
      },
      transitionTimingFunction: {
        // Emil-style curves: fast out, gentle settle. Never ease-in for entrances.
        out: "cubic-bezier(0.23, 1, 0.32, 1)",
        "in-out": "cubic-bezier(0.77, 0, 0.175, 1)",
        drawer: "cubic-bezier(0.32, 0.72, 0, 1)",
      },
      keyframes: {
        "pop-in": {
          "0%": { transform: "scale(0.94)", opacity: "0" },
          "100%": { transform: "scale(1)", opacity: "1" },
        },
        "rise-in": {
          "0%": { transform: "translateY(8px)", opacity: "0" },
          "100%": { transform: "translateY(0)", opacity: "1" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-5px)" },
        },
        cheer: {
          "0%, 100%": { transform: "translateY(0) rotate(0)" },
          "35%": { transform: "translateY(-12px) rotate(-5deg)" },
          "65%": { transform: "translateY(-3px) rotate(3deg)" },
        },
        nudge: {
          "0%, 100%": { transform: "translateX(0)" },
          "25%": { transform: "translateX(-5px)" },
          "75%": { transform: "translateX(5px)" },
        },
        "pulse-ring": {
          "0%": { boxShadow: "0 0 0 0 rgba(255,210,122,0.55)" },
          "100%": { boxShadow: "0 0 0 22px rgba(255,210,122,0)" },
        },
        "glow-breathe": {
          "0%, 100%": { opacity: "0.55", transform: "scale(1)" },
          "50%": { opacity: "0.9", transform: "scale(1.06)" },
        },
        "light-up": {
          "0%": { filter: "brightness(0.6) saturate(0.6)", transform: "scale(0.96)" },
          "60%": { filter: "brightness(1.25) saturate(1.1)", transform: "scale(1.03)" },
          "100%": { filter: "brightness(1) saturate(1)", transform: "scale(1)" },
        },
        "drift-up": {
          "0%": { transform: "translateY(0) scale(0.9)", opacity: "0" },
          "15%": { opacity: "1" },
          "100%": { transform: "translateY(-220px) scale(1)", opacity: "0" },
        },
        "fill-bar": {
          "0%": { transform: "scaleX(0)" },
          "100%": { transform: "scaleX(1)" },
        },
        "confetti-fall": {
          "0%": { transform: "translateY(-20px) rotate(0)", opacity: "1" },
          "100%": { transform: "translateY(260px) rotate(540deg)", opacity: "0" },
        },
        // Waiting indicators appear only if the wait is noticeable.
        "fade-in-late": {
          "0%, 40%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        // A lantern on its string, moving in the evening air.
        sway: {
          "0%, 100%": { transform: "rotate(-4deg)" },
          "50%": { transform: "rotate(4deg)" },
        },
        // Turning to the next page of the activity.
        "page-in": {
          "0%": { transform: "translateX(28px) rotate(0.6deg)", opacity: "0" },
          "100%": { transform: "translateX(0) rotate(0)", opacity: "1" },
        },
        // Sparks thrown out from a right answer; direction comes from --bx/--by.
        burst: {
          "0%": { transform: "translate(0, 0) scale(0.4)", opacity: "0" },
          "20%": { opacity: "1" },
          "100%": { transform: "translate(var(--bx), var(--by)) scale(1)", opacity: "0" },
        },
        // Finished lanterns released into the sky.
        "rise-away": {
          "0%": { transform: "translateY(0) rotate(0)", opacity: "1" },
          "100%": { transform: "translateY(-150px) rotate(var(--tilt, 4deg))", opacity: "0.15" },
        },
        "lantern-lit": {
          "0%": { transform: "scale(0.9)", filter: "brightness(0.7)" },
          "55%": { transform: "scale(1.14)", filter: "brightness(1.3)" },
          "100%": { transform: "scale(1)", filter: "brightness(1)" },
        },
        // A book on the river taking the light: its waiting cover fades from over the lit one.
        "book-unlit": {
          "0%": { opacity: "1" },
          "100%": { opacity: "0" },
        },
        // The light pooling around a book as it is lit.
        bloom: {
          "0%": { opacity: "0", transform: "scale(0.6)" },
          "60%": { opacity: "1", transform: "scale(1.12)" },
          "100%": { opacity: "0.55", transform: "scale(1)" },
        },
        // The firefly crossing from the book just lit to the next one; distance from --hop-x.
        hop: {
          "0%": { transform: "translate(var(--hop-x), 0)" },
          "45%": { transform: "translate(calc(var(--hop-x) * 0.45), -22px)" },
          "100%": { transform: "translate(0, 0)" },
        },
      },
      animation: {
        "pop-in": "pop-in 240ms cubic-bezier(0.23, 1, 0.32, 1) both",
        "rise-in": "rise-in 320ms cubic-bezier(0.23, 1, 0.32, 1) both",
        float: "float 4.5s ease-in-out infinite",
        cheer: "cheer 800ms cubic-bezier(0.23, 1, 0.32, 1)",
        nudge: "nudge 360ms ease-in-out",
        "pulse-ring": "pulse-ring 2s cubic-bezier(0.23, 1, 0.32, 1) infinite",
        "glow-breathe": "glow-breathe 3.2s ease-in-out infinite",
        "light-up": "light-up 700ms cubic-bezier(0.23, 1, 0.32, 1) both",
        "drift-up": "drift-up 2.6s cubic-bezier(0.23, 1, 0.32, 1) forwards",
        "fill-bar": "fill-bar 700ms cubic-bezier(0.23, 1, 0.32, 1) both",
        "confetti-fall": "confetti-fall 1.6s ease-in forwards",
        "fade-in-late": "fade-in-late 500ms ease-out both",
        sway: "sway 3.6s ease-in-out infinite",
        "page-in": "page-in 420ms cubic-bezier(0.23, 1, 0.32, 1) both",
        burst: "burst 700ms cubic-bezier(0.23, 1, 0.32, 1) forwards",
        "rise-away": "rise-away 3.4s cubic-bezier(0.45, 0, 0.55, 1) forwards",
        "lantern-lit": "lantern-lit 620ms cubic-bezier(0.23, 1, 0.32, 1) both",
        "book-unlit": "book-unlit 700ms cubic-bezier(0.23, 1, 0.32, 1) both",
        bloom: "bloom 1100ms cubic-bezier(0.23, 1, 0.32, 1) both",
        hop: "hop 900ms cubic-bezier(0.16, 1, 0.3, 1) both",
      },
    },
  },
  plugins: [],
} satisfies Config;
