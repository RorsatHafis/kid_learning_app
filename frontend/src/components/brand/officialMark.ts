import badge from "@/assets/brand/bookeyond-logo.webp";

/**
 * PD-34 (resolved, Y41): the official Bookeyond logo, as the owner supplied it - the round badge with
 * the signal rising from an open book and the "bookeyond" lettering. It is used as given, never redrawn:
 * the area outside the circle is transparent and, as the owner allowed, its blue is the platform's deep
 * river (tonle-800). Source and both colourings: `brand/official/` (`make_assets.py` there rebuilds them).
 */
export const OFFICIAL_MARK = { badge, alt: "Bookeyond" } as const;
