const BUBBLE_PATH =
  "M18 7h28a12 12 0 0 1 12 12v18a12 12 0 0 1-12 12H31l-10.5 8.6a1.5 1.5 0 0 1-2.5-1.2V49A12 12 0 0 1 6 37V19A12 12 0 0 1 18 7z";
const CHECK_PATH = "M21.5 28.5l7 7 14-14";

/** Check speech bubble symbol. Bubble uses the accent token; the check stays white. */
export function LogoMark({ size = 32, className = "" }: { size?: number; className?: string }) {
  return (
    <svg viewBox="0 0 64 64" width={size} height={size} className={className} aria-hidden="true">
      <path d={BUBBLE_PATH} fill="rgb(var(--accent))" />
      <path
        d={CHECK_PATH}
        fill="none"
        stroke="#fff"
        strokeWidth="6.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function Logo({ size = 28, className = "" }: { size?: number; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <LogoMark size={size} />
      <span className="font-display text-lg text-ink">dudecide</span>
    </span>
  );
}
