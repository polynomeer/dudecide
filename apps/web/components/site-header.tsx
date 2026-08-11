import Link from "next/link";
import type { Route } from "next";
import { SessionBadge } from "@/components/session-badge";

const navItems = [
  { href: "/start", label: "Start Session" },
  { href: "/meetings/new", label: "Create Meeting" },
] satisfies Array<{ href: Route; label: string }>;

export function SiteHeader() {
  return (
    <header className="panel mb-5 flex flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-7">
      <div className="flex items-center gap-4">
        <Link href="/" className="inline-flex items-center gap-3">
          <span className="flex h-12 w-12 items-center justify-center rounded-3xl bg-ink text-lg font-semibold text-mist">
            DD
          </span>
          <div>
            <div className="eyebrow">Dudecide MVP</div>
            <div className="font-[var(--font-display)] text-xl font-semibold tracking-[-0.04em]">
              Plan with evidence, not scroll fatigue.
            </div>
          </div>
        </Link>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <nav className="flex flex-wrap gap-2">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-full border border-[color:var(--color-line)] px-4 py-2 text-sm font-medium transition hover:border-ink hover:bg-white/80"
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <SessionBadge />
      </div>
    </header>
  );
}
