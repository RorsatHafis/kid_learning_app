import { cn } from "@/lib/utils/cn";

/**
 * Real product screens on the landing page, in quiet device frames so they read as the product, not
 * as decoration. The images are captures of the running app with sample data (the page says so).
 */
export function PhoneFrame({
  src,
  alt,
  className,
  eager = false,
}: {
  src: string;
  alt: string;
  className?: string;
  eager?: boolean;
}) {
  return (
    <div className={cn("rounded-[2.6rem] bg-tonle-950 p-2.5 shadow-[0_30px_60px_-24px_rgba(10,33,39,0.55)] ring-1 ring-white/10", className)}>
      <div className="relative overflow-hidden rounded-[2.1rem] bg-[#E7F1EC]">
        <img src={src} alt={alt} width={390} height={844} loading={eager ? "eager" : "lazy"} decoding="async" className="block h-auto w-full" />
        <span aria-hidden className="absolute left-1/2 top-1.5 h-3.5 w-16 -translate-x-1/2 rounded-full bg-tonle-950" />
      </div>
    </div>
  );
}

export function BrowserFrame({
  src,
  alt,
  width,
  height,
  className,
  eager = false,
}: {
  src: string;
  alt: string;
  width: number;
  height: number;
  className?: string;
  eager?: boolean;
}) {
  return (
    <div className={cn("overflow-hidden rounded-[18px] bg-white shadow-[0_30px_60px_-28px_rgba(10,33,39,0.45)] ring-1 ring-tonle-950/10", className)}>
      <div aria-hidden className="flex h-8 items-center gap-1.5 border-b border-line bg-paper px-3.5">
        <span className="h-2.5 w-2.5 rounded-full bg-coral/70" />
        <span className="h-2.5 w-2.5 rounded-full bg-light/80" />
        <span className="h-2.5 w-2.5 rounded-full bg-palm/60" />
        <span className="ml-3 h-4 flex-1 rounded-full bg-white ring-1 ring-line" />
      </div>
      <img src={src} alt={alt} width={width} height={height} loading={eager ? "eager" : "lazy"} decoding="async" className="block h-auto w-full" />
    </div>
  );
}
