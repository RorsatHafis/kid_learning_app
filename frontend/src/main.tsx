import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
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
