import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
// Fonts are served from this app, never a third party: loading them from Google would send
// every visitor's address (children's included) to Google on each page view.
import "@fontsource/rubik/400.css";
import "@fontsource/rubik/500.css";
import "@fontsource/rubik/600.css";
import "@fontsource/rubik/700.css";
import "@fontsource/rubik/800.css";
import "@fontsource/noto-sans-khmer/400.css";
import "@fontsource/noto-sans-khmer/600.css";
import "@fontsource/noto-sans-khmer/700.css";
import "./index.css";

// The client-side enrollment pointer this key held was replaced by the server's
// GET /children/{id}/enrollments; clear what earlier versions left behind.
try {
  localStorage.removeItem("bk_enrollment_pointers");
} catch {
  // Storage unavailable — nothing to clear.
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
