/**
 * PD-34: the official Bookeyond mark.
 *
 * The genuine Bookeyond logo files were not available to the project (Y38: not in the repository,
 * the knowledge package or the brand PDFs). Until they are supplied, the product mark drawn in
 * `BrandMark` stands in - it is the product's own mark, not presented as Bookeyond's official logo.
 *
 * To use the official mark: put its vector files in `src/assets/brand/` (one for light grounds, one
 * for dark), import them here and set `OFFICIAL_MARK`. Every brand touchpoint (landing, sign-in,
 * family and staff headers, the child's space, loading) then shows it, with no other change.
 *
 *   import ink from "@/assets/brand/bookeyond-mark-ink.svg";
 *   import paper from "@/assets/brand/bookeyond-mark-paper.svg";
 *   export const OFFICIAL_MARK = { ink, paper };
 */
export const OFFICIAL_MARK: { ink: string; paper: string } | null = null;
